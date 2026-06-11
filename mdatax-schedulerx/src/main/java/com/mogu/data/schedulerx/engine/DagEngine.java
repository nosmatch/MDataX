package com.mogu.data.schedulerx.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import com.mogu.data.schedulerx.engine.model.TaskConfig;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.entity.DagTaskEntity;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.DagInstanceStatus;
import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.FailureStrategy;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import com.mogu.data.schedulerx.event.TaskEvent;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.service.DagTaskService;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import com.mogu.data.schedulerx.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DAG 编排引擎核心
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class DagEngine {

    @Autowired
    private DagDefService dagDefService;
    @Autowired
    private DagTaskService dagTaskService;
    @Autowired
    private DagInstanceService dagInstanceService;
    @Autowired
    private TaskInstanceService taskInstanceService;
    @Autowired
    private TopologyResolver topologyResolver;
    @Autowired
    private StateMachine stateMachine;
    @Autowired
    private TaskEventPublisher eventPublisher;
    @Autowired
    private TaskExecutor taskExecutor;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ScheduledExecutorService retryExecutor;

    /**
     * 启动 DAG 实例
     *
     * @param instanceId 实例 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void start(String instanceId) {
        log.info("[DagEngine] 启动 DAG 实例: {}", instanceId);

        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            log.error("[DagEngine] DAG 实例不存在: {}", instanceId);
            return;
        }

        // 加载 DAG 定义
        String dagId = dagInstance.getDagId();
        Dag dag = loadDag(dagId);
        if (dag == null) {
            log.error("[DagEngine] DAG 定义不存在: {}", dagId);
            dagInstance.setStatus(DagInstanceStatus.FAILURE.name());
            dagInstance.setUpdateTime(LocalDateTime.now());
            dagInstanceService.updateById(dagInstance);
            return;
        }

        // 状态机: PENDING -> RUNNING
        dagInstance.setStatus(DagInstanceStatus.RUNNING.name());
        dagInstance.setStartTime(LocalDateTime.now());
        dagInstanceService.updateById(dagInstance);

        // 拓扑解析，获取根任务
        Map<String, String> taskStatusMap = new HashMap<>();
        List<DagTask> readyTasks = topologyResolver.resolveReadyTasks(dag, taskStatusMap);

        if (readyTasks.isEmpty()) {
            log.warn("[DagEngine] DAG 实例 {} 没有就绪任务", instanceId);
            dagInstance.setStatus(DagInstanceStatus.SUCCESS.name());
            dagInstance.setEndTime(LocalDateTime.now());
            dagInstanceService.updateById(dagInstance);
            return;
        }

        // 为就绪任务创建 task_instance 并提交执行
        for (DagTask dagTask : readyTasks) {
            submitTask(dag, dagInstance, dagTask, 1);
        }
    }

    /**
     * 任务完成回调处理
     *
     * @param event 任务事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void onTaskComplete(TaskEvent event) {
        log.info("[DagEngine] 任务完成事件: taskInstanceId={}, type={}",
                event.getTaskInstanceId(), event.getType());

        TaskInstance taskInstance = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getTaskInstanceId, event.getTaskInstanceId())
                .one();
        if (taskInstance == null) {
            log.error("[DagEngine] 任务实例不存在: {}", event.getTaskInstanceId());
            return;
        }

        String dagInstanceId = taskInstance.getDagInstanceId();
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, dagInstanceId)
                .one();
        if (dagInstance == null) {
            log.error("[DagEngine] DAG 实例不存在: {}", dagInstanceId);
            return;
        }

        // 更新任务实例状态
        TaskInstanceStatus currentStatus = TaskInstanceStatus.valueOf(taskInstance.getStatus());
        TaskInstanceStatus newStatus = stateMachine.transition(currentStatus, event.getType());
        taskInstance.setStatus(newStatus.name());
        taskInstance.setEndTime(LocalDateTime.now());
        if (event.getDurationMs() != null) {
            taskInstance.setDurationMs(event.getDurationMs());
        }
        if (event.getOutput() != null) {
            taskInstance.setOutput(event.getOutput());
        }
        if (event.getErrorMsg() != null) {
            taskInstance.setErrorMsg(event.getErrorMsg());
        }
        taskInstanceService.updateById(taskInstance);

        // 加载 DAG
        Dag dag = loadDag(dagInstance.getDagId());
        if (dag == null) {
            return;
        }

        // 根据事件类型处理
        switch (event.getType()) {
            case SUCCESS:
                handleTaskSuccess(dag, dagInstance, taskInstance);
                break;
            case FAILURE:
            case TIMEOUT:
                handleTaskFailure(dag, dagInstance, taskInstance);
                break;
            default:
                break;
        }
    }

    /**
     * 处理任务成功
     */
    private void handleTaskSuccess(Dag dag, DagInstance dagInstance, TaskInstance completedTask) {
        String dagInstanceId = dagInstance.getInstanceId();

        // 查询当前所有任务的状态
        Map<String, String> taskStatusMap = getTaskStatusMap(dagInstanceId);

        // 拓扑解析下游就绪任务
        List<DagTask> readyTasks = topologyResolver.resolveReadyTasks(dag, taskStatusMap);
        for (DagTask dagTask : readyTasks) {
            submitTask(dag, dagInstance, dagTask, 1);
        }

        // 检查 DAG 是否完成
        checkDagComplete(dag, dagInstance);
    }

    /**
     * 处理任务失败/超时
     */
    private void handleTaskFailure(Dag dag, DagInstance dagInstance, TaskInstance failedTask) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dag.getDagId())
                .one();
        int maxRetry = dagDef != null ? dagDef.getMaxRetryTimes() : 0;
        int taskMaxRetry = failedTask.getAttemptNumber() != null ? failedTask.getAttemptNumber() : 0;

        // 检查任务是否还有重试次数
        DagTask dagTask = dag.getTask(failedTask.getTaskId());
        int nodeRetryTimes = dagTask != null && dagTask.getRetryTimes() != null ? dagTask.getRetryTimes() : 0;
        int totalMaxRetry = Math.max(maxRetry, nodeRetryTimes);

        if (taskMaxRetry < totalMaxRetry) {
            int retryInterval = dagDef.getRetryIntervalSeconds() != null ? dagDef.getRetryIntervalSeconds() : 0;
            final int nextAttempt = taskMaxRetry + 1;
            if (retryInterval > 0) {
                log.info("[DagEngine] 任务 {} 将在 {} 秒后重试，当前尝试次数: {}/{}",
                        failedTask.getTaskInstanceId(), retryInterval, taskMaxRetry, totalMaxRetry);
                retryExecutor.schedule(() -> {
                    DagInstance currentInstance = dagInstanceService.lambdaQuery()
                            .eq(DagInstance::getInstanceId, dagInstance.getInstanceId())
                            .one();
                    if (currentInstance == null) {
                        log.warn("[DagEngine] DAG 实例已不存在，取消重试: instanceId={}", dagInstance.getInstanceId());
                        return;
                    }
                    if (DagInstanceStatus.STOPPED.name().equals(currentInstance.getStatus())
                            || DagInstanceStatus.SUCCESS.name().equals(currentInstance.getStatus())) {
                        log.warn("[DagEngine] DAG 实例已终止或完成，取消重试: instanceId={}, status={}",
                                dagInstance.getInstanceId(), currentInstance.getStatus());
                        return;
                    }
                    submitTask(dag, currentInstance, dagTask, nextAttempt);
                }, retryInterval, TimeUnit.SECONDS);
            } else {
                log.info("[DagEngine] 任务 {} 立即重试，当前尝试次数: {}/{}",
                        failedTask.getTaskInstanceId(), taskMaxRetry, totalMaxRetry);
                submitTask(dag, dagInstance, dagTask, nextAttempt);
            }
            return;
        }

        // 无重试次数，按失败策略处理
        FailureStrategy strategy = dag.getFailureStrategy() != null ? dag.getFailureStrategy() : FailureStrategy.STOP_ALL;

        if (strategy == FailureStrategy.STOP_ALL) {
            // 停止所有 RUNNING 任务
            List<TaskInstance> runningTasks = taskInstanceService.lambdaQuery()
                    .eq(TaskInstance::getDagInstanceId, dagInstance.getInstanceId())
                    .eq(TaskInstance::getStatus, TaskInstanceStatus.RUNNING.name())
                    .list();
            for (TaskInstance task : runningTasks) {
                task.setStatus(TaskInstanceStatus.FAILURE.name());
                task.setEndTime(LocalDateTime.now());
                taskInstanceService.updateById(task);
            }

            // 标记 DAG 失败
            dagInstance.setStatus(DagInstanceStatus.FAILURE.name());
            dagInstance.setFailureTaskId(failedTask.getTaskId());
            dagInstance.setEndTime(LocalDateTime.now());
            dagInstance.setDurationMs((int) java.time.Duration.between(dagInstance.getStartTime(), LocalDateTime.now()).toMillis());
            dagInstanceService.updateById(dagInstance);
        } else {
            // CONTINUE 策略：标记失败，继续下游
            failedTask.setStatus(TaskInstanceStatus.FAILURE.name());
            taskInstanceService.updateById(failedTask);

            // 将下游任务标记为 SKIPPED
            skipDownstreamTasks(dag, dagInstance, failedTask.getTaskId());

            // 检查 DAG 是否完成
            checkDagComplete(dag, dagInstance);
        }
    }

    /**
     * 跳过下游任务
     */
    private void skipDownstreamTasks(Dag dag, DagInstance dagInstance, String failedTaskId) {
        List<String> downstream = dag.getDownstream(failedTaskId);
        if (downstream == null || downstream.isEmpty()) {
            return;
        }
        for (String downId : downstream) {
            // 检查是否已经存在 task_instance
            TaskInstance existing = taskInstanceService.lambdaQuery()
                    .eq(TaskInstance::getDagInstanceId, dagInstance.getInstanceId())
                    .eq(TaskInstance::getTaskId, downId)
                    .one();
            if (existing == null) {
                TaskInstance skippedTask = new TaskInstance();
                skippedTask.setTaskInstanceId(IdGenerator.generateTaskInstanceId());
                skippedTask.setDagInstanceId(dagInstance.getInstanceId());
                skippedTask.setDagId(dag.getDagId());
                skippedTask.setTaskId(downId);
                skippedTask.setTaskName(dag.getTask(downId) != null ? dag.getTask(downId).getTaskName() : downId);
                skippedTask.setStatus(TaskInstanceStatus.SKIPPED.name());
                skippedTask.setAttemptNumber(1);
                skippedTask.setCreateTime(LocalDateTime.now());
                skippedTask.setUpdateTime(LocalDateTime.now());
                taskInstanceService.save(skippedTask);
            }
            // 递归跳过更下游
            skipDownstreamTasks(dag, dagInstance, downId);
        }
    }

    /**
     * 提交任务执行
     */
    private void submitTask(Dag dag, DagInstance dagInstance, DagTask dagTask, int attemptNumber) {
        String taskInstanceId = IdGenerator.generateTaskInstanceId();

        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId(taskInstanceId);
        taskInstance.setDagInstanceId(dagInstance.getInstanceId());
        taskInstance.setDagId(dag.getDagId());
        taskInstance.setTaskId(dagTask.getTaskId());
        taskInstance.setTaskName(dagTask.getTaskName());
        taskInstance.setStatus(TaskInstanceStatus.RUNNING.name());
        taskInstance.setAttemptNumber(attemptNumber);
        taskInstance.setStartTime(LocalDateTime.now());
        taskInstance.setCreateTime(LocalDateTime.now());
        taskInstance.setUpdateTime(LocalDateTime.now());

        // 构建请求体（注入 callback 信息）
        if (dagTask.getConfig() != null) {
            try {
                taskInstance.setRequestBody(objectMapper.writeValueAsString(dagTask.getConfig()));
            } catch (Exception e) {
                log.error("[DagEngine] 序列化任务配置失败: {}", dagTask.getTaskId(), e);
            }
        }

        taskInstanceService.save(taskInstance);

        log.info("[DagEngine] 提交任务执行: dagInstanceId={}, taskInstanceId={}, taskId={}",
                dagInstance.getInstanceId(), taskInstanceId, dagTask.getTaskId());

        // 提交到执行器
        taskExecutor.execute(taskInstance);
    }

    /**
     * 检查 DAG 是否完成
     */
    private void checkDagComplete(Dag dag, DagInstance dagInstance) {
        Map<String, String> taskStatusMap = getTaskStatusMap(dagInstance.getInstanceId());

        // 如果所有任务都有终态（非 RUNNING/PENDING/WAITING_UPSTREAM）
        boolean allTerminal = true;
        for (DagTask task : dag.getTasks()) {
            String status = taskStatusMap.get(task.getTaskId());
            if (status == null || "PENDING".equals(status) || "WAITING_UPSTREAM".equals(status) || "RUNNING".equals(status)) {
                allTerminal = false;
                break;
            }
        }

        if (allTerminal) {
            String finalStatus = stateMachine.aggregateDagStatus(taskStatusMap, dag.getFailureStrategy());
            dagInstance.setStatus(finalStatus);
            dagInstance.setEndTime(LocalDateTime.now());
            if (dagInstance.getStartTime() != null) {
                dagInstance.setDurationMs((int) java.time.Duration.between(dagInstance.getStartTime(), LocalDateTime.now()).toMillis());
            }
            dagInstanceService.updateById(dagInstance);
            log.info("[DagEngine] DAG 实例完成: instanceId={}, status={}", dagInstance.getInstanceId(), finalStatus);
        }
    }

    /**
     * 加载 DAG 内存模型
     */
    private Dag loadDag(String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            return null;
        }

        List<DagTaskEntity> taskEntities = dagTaskService.lambdaQuery()
                .eq(DagTaskEntity::getDagId, dagId)
                .list();

        Dag dag = new Dag();
        dag.setDagId(dagDef.getDagId());
        dag.setDagName(dagDef.getDagName());
        dag.setCronExpression(dagDef.getCronExpression());
        dag.setTimezone(dagDef.getTimezone());
        dag.setTimeoutSeconds(dagDef.getTimeoutSeconds());
        dag.setRetryIntervalSeconds(dagDef.getRetryIntervalSeconds());
        if (dagDef.getFailureStrategy() != null) {
            dag.setFailureStrategy(FailureStrategy.valueOf(dagDef.getFailureStrategy()));
        }

        for (DagTaskEntity entity : taskEntities) {
            DagTask task = new DagTask();
            task.setTaskId(entity.getTaskId());
            task.setTaskName(entity.getTaskName());
            task.setTaskType(entity.getTaskType());
            task.setRetryTimes(entity.getRetryTimes());
            task.setTimeoutSeconds(entity.getTimeoutSeconds());
            if (entity.getTaskConfig() != null) {
                try {
                    task.setConfig(objectMapper.readValue(entity.getTaskConfig(), TaskConfig.class));
                } catch (Exception e) {
                    log.error("[DagEngine] 解析任务配置失败: {}", entity.getTaskId(), e);
                }
            }
            if (entity.getUpstreamTasks() != null) {
                try {
                    task.setUpstream(objectMapper.readValue(entity.getUpstreamTasks(), new TypeReference<List<String>>() {}));
                } catch (Exception e) {
                    log.error("[DagEngine] 解析上游任务失败: {}", entity.getTaskId(), e);
                }
            }
            dag.getTasks().add(task);
            dag.getTaskMap().put(task.getTaskId(), task);
        }

        dag.buildDownstream();
        return dag;
    }

    /**
     * 获取 DAG 实例下所有任务的状态
     */
    private Map<String, String> getTaskStatusMap(String dagInstanceId) {
        List<TaskInstance> tasks = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getDagInstanceId, dagInstanceId)
                .list();
        Map<String, String> map = new HashMap<>();
        for (TaskInstance task : tasks) {
            map.put(task.getTaskId(), task.getStatus());
        }
        return map;
    }

    /**
     * 手动杀除 DAG 实例
     *
     * @param instanceId 实例 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void kill(String instanceId) {
        log.info("[DagEngine] 杀除 DAG 实例: {}", instanceId);

        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            return;
        }

        // 停止所有 RUNNING 任务
        List<TaskInstance> runningTasks = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getDagInstanceId, instanceId)
                .eq(TaskInstance::getStatus, TaskInstanceStatus.RUNNING.name())
                .list();
        for (TaskInstance task : runningTasks) {
            task.setStatus(TaskInstanceStatus.FAILURE.name());
            task.setEndTime(LocalDateTime.now());
            taskInstanceService.updateById(task);
        }

        dagInstance.setStatus(DagInstanceStatus.STOPPED.name());
        dagInstance.setEndTime(LocalDateTime.now());
        dagInstanceService.updateById(dagInstance);
    }

    /**
     * 重试失败任务
     *
     * @param instanceId 实例 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryFailedTasks(String instanceId) {
        log.info("[DagEngine] 重试 DAG 实例失败任务: {}", instanceId);

        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            return;
        }

        Dag dag = loadDag(dagInstance.getDagId());
        if (dag == null) {
            return;
        }

        // 找到所有失败或超时的任务
        List<TaskInstance> failedTasks = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getDagInstanceId, instanceId)
                .in(TaskInstance::getStatus, Arrays.asList("FAILURE", "TIMEOUT", "STOPPED"))
                .list();

        if (failedTasks.isEmpty()) {
            log.info("[DagEngine] 没有失败任务需要重试: {}", instanceId);
            return;
        }

        // 重置 DAG 状态为 RUNNING
        dagInstance.setStatus(DagInstanceStatus.RUNNING.name());
        dagInstance.setEndTime(null);
        dagInstanceService.updateById(dagInstance);

        // 重新提交失败任务
        for (TaskInstance failedTask : failedTasks) {
            DagTask dagTask = dag.getTask(failedTask.getTaskId());
            if (dagTask != null) {
                submitTask(dag, dagInstance, dagTask, failedTask.getAttemptNumber() + 1);
            }
        }
    }

}
