package com.mogu.data.integration.dolphinscheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DolphinScheduler 调度管理器实现。
 *
 * <p>将 MDataX 的任务注册到 DS，由 DS 负责定时触发。
 * 每个任务对应 DS 中的一个单节点工作流 + 一个定时调度。
 *
 * <p>工作流命名规则: {TASK_TYPE}-{TASK_NAME}，如 {@code SQL-ceshi1}、{@code SYNC-订单同步}。
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.type", havingValue = "dolphinscheduler")
public class DolphinSchedulerManager implements TaskSchedulerManager {

    private final DolphinSchedulerClient dsClient;
    private final DolphinSchedulerProperties props;
    private final SqlTaskMapper sqlTaskMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SqlTaskDependencyMapper sqlTaskDependencyMapper;
    private final SqlTaskWorkflowMapper sqlTaskWorkflowMapper;

    @Override
    public void scheduleSyncTask(SyncTask task) {
        doSchedule(task.getId(), task.getTaskName(), "SYNC",
                task.getCronExpression(), task.getDsProcessCode(), task.getDsScheduleId(), task.getDsTaskCode(),
                (code) -> task.setDsProcessCode(code),
                (id) -> task.setDsScheduleId(id),
                (code) -> task.setDsTaskCode(code));
    }

    @Override
    public void scheduleSqlTask(SqlTask task) {
        doSchedule(task.getId(), task.getTaskName(), "SQL",
                task.getCronExpression(), task.getDsProcessCode(), task.getDsScheduleId(), task.getDsTaskCode(),
                (code) -> task.setDsProcessCode(code),
                (id) -> task.setDsScheduleId(id),
                (code) -> task.setDsTaskCode(code));
    }

    @Override
    public void cancelSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void cancelSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void deleteSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            deleteByTask(taskId, task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void deleteSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            deleteByTask(taskId, task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void rescheduleSyncTask(SyncTask task) {
        cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        scheduleSyncTask(task);
    }

    @Override
    public void rescheduleSqlTask(SqlTask task) {
        cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        scheduleSqlTask(task);
    }

    @Override
    public void scheduleWorkflow(SqlTaskWorkflow workflow) {
        if (!props.isEnabled()) {
            log.warn("DolphinScheduler 未启用，跳过 Workflow 调度注册: workflowId={}", workflow.getId());
            return;
        }
        DagData dagData = buildDagData(workflow.getId());
        String processName = "WORKFLOW-" + workflow.getWorkflowName();

        // 当 DS 重置后，旧 processCode 可能已不存在，需要检查
        Long processCode = workflow.getDsProcessCode();
        boolean needRecreate = false;
        if (processCode != null && !dsClient.processExists(processCode)) {
            log.warn("DS Workflow 已不存在，将重新创建: workflowId={}, oldProcessCode={}",
                    workflow.getId(), processCode);
            processCode = null;
            needRecreate = true;
        }

        processCode = dsClient.createOrUpdateDagProcess(
                processCode, processName,
                dagData.getNodes(), dagData.getRelations());
        workflow.setDsProcessCode(processCode);
        log.info("DS Workflow 工作流创建/更新成功: workflowId={}, processCode={}",
                workflow.getId(), processCode);

        // 上线工作流
        dsClient.releaseProcess(processCode, "ONLINE");

        // 处理定时调度
        String cron = workflow.getCronExpression();
        if (cron != null && !cron.isEmpty()) {
            Integer scheduleId;
            if (workflow.getDsScheduleId() == null || needRecreate) {
                scheduleId = dsClient.createSchedule(processCode, cron);
                workflow.setDsScheduleId(scheduleId);
                log.info("DS Workflow 定时调度创建成功: workflowId={}, scheduleId={}",
                        workflow.getId(), scheduleId);
            } else {
                dsClient.updateSchedule(processCode, workflow.getDsScheduleId(), cron);
                scheduleId = workflow.getDsScheduleId();
                log.info("DS Workflow 定时调度更新成功: workflowId={}, scheduleId={}",
                        workflow.getId(), scheduleId);
            }
            dsClient.onlineSchedule(processCode, scheduleId);
        }
    }

    @Override
    public void cancelWorkflow(Long workflowId) {
        SqlTaskWorkflow workflow = sqlTaskWorkflowMapper.selectById(workflowId);
        if (workflow != null && workflow.getDsProcessCode() != null) {
            cancelWorkflowInternal(workflow);
        }
    }

    @Override
    public void deleteWorkflow(Long workflowId) {
        SqlTaskWorkflow workflow = sqlTaskWorkflowMapper.selectById(workflowId);
        if (workflow != null && workflow.getDsProcessCode() != null) {
            deleteWorkflowInternal(workflow);
        }
    }

    @Override
    public void rescheduleWorkflow(SqlTaskWorkflow workflow) {
        cancelWorkflowInternal(workflow);
        scheduleWorkflow(workflow);
    }

    // ==================== Workflow 内部方法 ====================

    private void cancelWorkflowInternal(SqlTaskWorkflow workflow) {
        if (workflow.getDsProcessCode() == null) {
            return;
        }
        try {
            if (workflow.getDsScheduleId() != null) {
                dsClient.offlineSchedule(workflow.getDsProcessCode(), workflow.getDsScheduleId());
            }
            dsClient.releaseProcess(workflow.getDsProcessCode(), "OFFLINE");
            log.info("DS Workflow 调度已取消: workflowId={}", workflow.getId());
        } catch (Exception e) {
            log.error("DS Workflow 调度取消失败: workflowId={}", workflow.getId(), e);
        }
    }

    private void deleteWorkflowInternal(SqlTaskWorkflow workflow) {
        if (workflow.getDsProcessCode() == null) {
            return;
        }
        try {
            if (workflow.getDsScheduleId() != null) {
                dsClient.offlineSchedule(workflow.getDsProcessCode(), workflow.getDsScheduleId());
            }
            dsClient.releaseProcess(workflow.getDsProcessCode(), "OFFLINE");
            dsClient.deleteProcess(workflow.getDsProcessCode());
            log.info("DS Workflow 已删除: workflowId={}", workflow.getId());
        } catch (Exception e) {
            log.error("DS Workflow 删除失败: workflowId={}", workflow.getId(), e);
        }
    }

    private DagData buildDagData(Long workflowId) {
        // 1. 加载工作流内的 SQL 任务和同步任务
        List<SqlTask> sqlTasks = sqlTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SqlTask>()
                        .eq(SqlTask::getWorkflowId, workflowId)
                        .eq(SqlTask::getDeleted, 0));
        List<SyncTask> syncTasks = syncTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getWorkflowId, workflowId)
                        .eq(SyncTask::getDeleted, 0));

        List<SqlTaskDependency> deps = sqlTaskDependencyMapper.selectByWorkflowId(workflowId);

        // 使用复合键避免 SQL 和 SYNC 任务 id 冲突
        Map<String, Long> taskKeyToCode = new HashMap<>();
        List<DagNode> nodes = new ArrayList<>();

        // 2. 处理 SQL 任务
        for (SqlTask task : sqlTasks) {
            if (task.getDsTaskCode() == null) {
                task.setDsTaskCode(dsClient.generateTaskCode());
                sqlTaskMapper.updateById(task);
            }
            long taskCode = task.getDsTaskCode();
            taskKeyToCode.put(task.getId() + "#SQL", taskCode);
            nodes.add(new DagNode(taskCode, task.getTaskName(),
                    buildCallbackScript("SQL", task.getId())));
        }

        // 3. 处理同步任务
        for (SyncTask task : syncTasks) {
            if (task.getDsTaskCode() == null) {
                task.setDsTaskCode(dsClient.generateTaskCode());
                syncTaskMapper.updateById(task);
            }
            long taskCode = task.getDsTaskCode();
            taskKeyToCode.put(task.getId() + "#SYNC", taskCode);
            nodes.add(new DagNode(taskCode, task.getTaskName(),
                    buildCallbackScript("SYNC", task.getId())));
        }

        // 4. 构建依赖关系
        List<DagRelation> relations = new ArrayList<>();
        for (SqlTaskDependency dep : deps) {
            String preKey = dep.getDependTaskId() + "#" + dep.getDependTaskType();
            String postKey = dep.getTaskId() + "#" + dep.getTaskType();
            Long preCode = taskKeyToCode.get(preKey);
            Long postCode = taskKeyToCode.get(postKey);
            if (preCode != null && postCode != null) {
                relations.add(new DagRelation(preCode, postCode));
            }
        }

        return new DagData(nodes, relations);
    }

    private String buildCallbackScript(String taskType, Long taskId) {
        return String.format(
                "curl -s -X POST %s/api/internal/task/execute " +
                        "-H \"Content-Type: application/json\" " +
                        "-d \"{\\\"taskType\\\":\\\"%s\\\",\\\"taskId\\\":%d,\\\"secret\\\":\\\"%s\\\"}\" " +
                        "--max-time 7200",
                props.getCallbackUrl(), taskType, taskId, props.getCallbackSecret());
    }

    // ==================== 独立任务调度逻辑（原有） ====================

    // ==================== 通用调度逻辑 ====================

    private void doSchedule(Long taskId, String taskName, String taskType,
                            String cronExpression,
                            Long existProcessCode, Integer existScheduleId, Long existTaskCode,
                            java.util.function.Consumer<Long> processCodeSetter,
                            java.util.function.Consumer<Integer> scheduleIdSetter,
                            java.util.function.Consumer<Long> taskCodeSetter) {
        if (!props.isEnabled()) {
            log.warn("DolphinScheduler 未启用，跳过调度注册: taskId={}", taskId);
            return;
        }

        String processName = taskType + "-" + taskName;

        try {
            long taskCode = existTaskCode != null ? existTaskCode : dsClient.generateTaskCode();

            // 当 DS 重置后，旧 processCode 可能已不存在，需要检查
            Long processCode = existProcessCode;
            boolean needRecreate = false;
            if (processCode != null && !dsClient.processExists(processCode)) {
                log.warn("DS 工作流已不存在，将重新创建: taskId={}, oldProcessCode={}", taskId, processCode);
                processCode = null;
                needRecreate = true;
            }

            // 1. 创建或更新工作流
            if (processCode == null) {
                processCode = dsClient.createSingleNodeProcess(processName, taskType, taskId, taskCode);
                processCodeSetter.accept(processCode);
                log.info("DS 工作流创建成功: taskId={}, processCode={}", taskId, processCode);
            } else {
                dsClient.updateSingleNodeProcess(processCode, processName, taskType, taskId, taskCode);
                log.info("DS 工作流更新成功: taskId={}, processCode={}", taskId, processCode);
            }
            taskCodeSetter.accept(taskCode);

            // 2. 上线工作流（创建调度前必须先上线）
            dsClient.releaseProcess(processCode, "ONLINE");

            // 3. 处理定时调度
            if (cronExpression != null && !cronExpression.isEmpty()) {
                Integer scheduleId;
                if (existScheduleId == null || needRecreate) {
                    scheduleId = dsClient.createSchedule(processCode, cronExpression);
                    scheduleIdSetter.accept(scheduleId);
                    log.info("DS 定时调度创建成功: taskId={}, scheduleId={}", taskId, scheduleId);
                } else {
                    dsClient.updateSchedule(processCode, existScheduleId, cronExpression);
                    scheduleId = existScheduleId;
                    log.info("DS 定时调度更新成功: taskId={}, scheduleId={}", taskId, scheduleId);
                }

                // 4. 上线定时调度
                dsClient.onlineSchedule(processCode, scheduleId);
            }

        } catch (Exception e) {
            log.error("DS 调度注册失败: taskId={}, taskType={}", taskId, taskType, e);
            // 不抛异常，避免影响主流程；调用方应根据 ds_process_code 是否为 null 判断成功
        }
    }

    private void cancelByTask(Long processCode, Integer scheduleId) {
        if (processCode == null) {
            return;
        }
        try {
            if (scheduleId != null) {
                dsClient.offlineSchedule(processCode, scheduleId);
            }
            dsClient.releaseProcess(processCode, "OFFLINE");
            log.info("DS 调度已取消: processCode={}", processCode);
        } catch (Exception e) {
            log.error("DS 调度取消失败: processCode={}", processCode, e);
        }
    }

    private void deleteByTask(Long taskId, Long processCode, Integer scheduleId) {
        if (processCode == null) {
            return;
        }
        try {
            if (scheduleId != null) {
                dsClient.offlineSchedule(processCode, scheduleId);
            }
            dsClient.releaseProcess(processCode, "OFFLINE");
            dsClient.deleteProcess(processCode);
            log.info("DS 工作流已删除: taskId={}, processCode={}", taskId, processCode);
        } catch (Exception e) {
            log.error("DS 工作流删除失败: taskId={}, processCode={}", taskId, processCode, e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class DagData {
        private final List<DagNode> nodes;
        private final List<DagRelation> relations;
    }
}
