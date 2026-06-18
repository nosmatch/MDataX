package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskDependency;
import com.mogu.data.integration.entity.TaskExecution;
import com.mogu.data.integration.mapper.TaskDependencyMapper;
import com.mogu.data.integration.mapper.TaskExecutionMapper;
import com.mogu.data.integration.mapper.TaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 任务依赖关系服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
public class TaskDependencyService extends ServiceImpl<TaskDependencyMapper, TaskDependency> {

    private final TaskMapper taskMapper;
    private final TaskExecutionMapper taskExecutionMapper;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    public TaskDependencyService(TaskMapper taskMapper, TaskExecutionMapper taskExecutionMapper,
                                  ThreadPoolTaskScheduler taskScheduler) {
        this.taskMapper = taskMapper;
        this.taskExecutionMapper = taskExecutionMapper;
        this.taskScheduler = taskScheduler;
    }

    private TaskExecutionService taskExecutionService;

    @Autowired
    public void setTaskExecutionService(@Lazy TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTriggers = new ConcurrentHashMap<>();
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * 获取任务的所有依赖关系（包含任务基本信息）
     */
    public TaskDependencyVO getTaskDependencies(Long taskId) {
        // 上游依赖（前置任务）
        List<TaskDependency> upstreams = baseMapper.selectByDownstreamTaskId(taskId);

        // 下游任务（依赖此任务）
        List<TaskDependency> downstreams = baseMapper.selectByUpstreamTaskId(taskId);

        // 批量填充任务信息
        Set<Long> relatedTaskIds = new HashSet<>();
        upstreams.forEach(dep -> relatedTaskIds.add(dep.getUpstreamTaskId()));
        downstreams.forEach(dep -> relatedTaskIds.add(dep.getDownstreamTaskId()));
        relatedTaskIds.add(taskId);

        Map<Long, Task> taskMap = taskMapper.selectBatchIds(relatedTaskIds).stream()
                .collect(Collectors.toMap(Task::getId, t -> t));

        TaskDependencyVO vo = new TaskDependencyVO();
        vo.setTaskId(taskId);
        vo.setCurrentTask(taskMap.get(taskId));
        vo.setUpstreamDependencies(enrichDependencies(upstreams, taskMap, true));
        vo.setDownstreamDependencies(enrichDependencies(downstreams, taskMap, false));
        return vo;
    }

    private List<TaskDependencyExt> enrichDependencies(List<TaskDependency> deps,
                                                       Map<Long, Task> taskMap,
                                                       boolean isUpstream) {
        List<TaskDependencyExt> result = new ArrayList<>();
        for (TaskDependency dep : deps) {
            Long relatedTaskId = isUpstream ? dep.getUpstreamTaskId() : dep.getDownstreamTaskId();
            Task task = taskMap.get(relatedTaskId);
            TaskDependencyExt ext = new TaskDependencyExt();
            ext.setId(dep.getId());
            ext.setUpstreamTaskId(dep.getUpstreamTaskId());
            ext.setDownstreamTaskId(dep.getDownstreamTaskId());
            ext.setDependencyType(dep.getDependencyType());
            ext.setConditionExpression(dep.getConditionExpression());
            ext.setDelaySeconds(dep.getDelaySeconds());
            ext.setTaskCode(task != null ? task.getTaskCode() : null);
            ext.setTaskName(task != null ? task.getTaskName() : null);
            ext.setTaskType(task != null ? task.getTaskType() : null);
            result.add(ext);
        }
        return result;
    }

    /**
     * 添加上游依赖
     */
    @Transactional
    public void addDependency(Long downstreamTaskId, AddDependencyRequest request) {
        // 检查任务是否存在
        if (taskMapper.selectById(downstreamTaskId) == null) {
            throw new IllegalArgumentException("下游任务不存在");
        }
        if (taskMapper.selectById(request.getUpstreamTaskId()) == null) {
            throw new IllegalArgumentException("上游任务不存在");
        }

        // 检查是否会产生循环依赖
        if (hasCycle(request.getUpstreamTaskId(), downstreamTaskId)) {
            throw new IllegalArgumentException("添加此依赖会产生循环依赖");
        }

        // 检查是否已存在
        long count = lambdaQuery()
                .eq(TaskDependency::getUpstreamTaskId, request.getUpstreamTaskId())
                .eq(TaskDependency::getDownstreamTaskId, downstreamTaskId)
                .count();
        if (count > 0) {
            throw new IllegalArgumentException("依赖关系已存在");
        }

        // 创建依赖关系
        TaskDependency dependency = new TaskDependency();
        dependency.setUpstreamTaskId(request.getUpstreamTaskId());
        dependency.setDownstreamTaskId(downstreamTaskId);
        dependency.setDependencyType(request.getDependencyType());
        dependency.setConditionExpression(request.getConditionExpression());
        dependency.setDelaySeconds(request.getDelaySeconds());
        dependency.setCreateUserId(request.getCreateUserId());

        save(dependency);
        log.info("添加依赖关系成功: upstream={}, downstream={}",
                request.getUpstreamTaskId(), downstreamTaskId);
    }

    /**
     * 删除依赖关系
     */
    @Transactional
    public void removeDependency(Long dependencyId) {
        TaskDependency dependency = getById(dependencyId);
        if (dependency == null) {
            throw new IllegalArgumentException("依赖关系不存在");
        }

        removeById(dependencyId);
        log.info("删除依赖关系成功: dependencyId={}", dependencyId);
    }

    /**
     * 检查任务是否有依赖关系
     */
    public boolean hasDependency(Long taskId) {
        long upstreamCount = lambdaQuery()
                .eq(TaskDependency::getUpstreamTaskId, taskId)
                .count();
        long downstreamCount = lambdaQuery()
                .eq(TaskDependency::getDownstreamTaskId, taskId)
                .count();
        return upstreamCount > 0 || downstreamCount > 0;
    }

    /**
     * 搜索可添加依赖的任务
     */
    public List<com.mogu.data.integration.entity.Task> searchTasksForDependency(
            Long currentTaskId, String keyword, String taskType) {

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.mogu.data.integration.entity.Task> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        wrapper.eq(com.mogu.data.integration.entity.Task::getDeleted, 0);
        wrapper.ne(com.mogu.data.integration.entity.Task::getId, currentTaskId);  // 排除自己

        if (org.springframework.util.StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(com.mogu.data.integration.entity.Task::getTaskName, keyword)
                    .or().like(com.mogu.data.integration.entity.Task::getTaskCode, keyword));
        }
        if (org.springframework.util.StringUtils.hasText(taskType)) {
            wrapper.eq(com.mogu.data.integration.entity.Task::getTaskType, taskType);
        }

        wrapper.orderByDesc(com.mogu.data.integration.entity.Task::getCreateTime);
        wrapper.last("LIMIT 50");  // 限制返回数量

        return taskMapper.selectList(wrapper);
    }

    /**
     * 触发下游依赖任务
     */
    public void triggerDownstreamTasks(Long upstreamTaskId, String executionId, String upstreamStatus) {
        List<TaskDependency> downstreams = baseMapper.selectByUpstreamTaskId(upstreamTaskId);

        for (TaskDependency dep : downstreams) {
            // 判断是否满足触发条件
            if (!shouldTrigger(dep, upstreamStatus, executionId)) {
                log.debug("不满足触发条件，跳过: dependencyId={}", dep.getId());
                continue;
            }

            // 延迟执行
            if (dep.getDelaySeconds() != null && dep.getDelaySeconds() > 0) {
                scheduleDelayedTrigger(dep, executionId);
            } else {
                doTriggerDownstream(dep.getDownstreamTaskId(), executionId);
            }
        }
    }

    /**
     * 判断是否应该触发下游任务
     */
    private boolean shouldTrigger(TaskDependency dep, String upstreamStatus, String executionId) {
        // 判断依赖类型
        switch (dep.getDependencyType()) {
            case "SUCCESS":
                if (!"SUCCESS".equals(upstreamStatus)) {
                    return false;
                }
                break;
            case "FAILED":
                if (!"FAILED".equals(upstreamStatus)) {
                    return false;
                }
                break;
            case "ANY":
                if (!"SUCCESS".equals(upstreamStatus) && !"FAILED".equals(upstreamStatus)
                        && !"TIMEOUT".equals(upstreamStatus)) {
                    return false;
                }
                break;
            default:
                return false;
        }

        // 判断条件表达式（如果有）
        if (dep.getConditionExpression() != null && !dep.getConditionExpression().isEmpty()) {
            TaskExecution upstreamExec = taskExecutionMapper.selectByExecutionId(executionId);
            if (upstreamExec == null) {
                log.warn("上游执行记录不存在，跳过条件判断: executionId={}", executionId);
                return false;
            }

            if (!evaluateCondition(dep.getConditionExpression(), upstreamExec)) {
                log.debug("条件表达式不满足，跳过: dependencyId={}, expression={}",
                        dep.getId(), dep.getConditionExpression());
                return false;
            }
        }

        return true;
    }

    /**
     * 评估条件表达式（使用 SpEL）
     */
    private boolean evaluateCondition(String expression, TaskExecution upstreamExec) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("UPSTREAM_AFFECTED_ROWS", upstreamExec.getAffectedRows() != null ? upstreamExec.getAffectedRows() : 0L);
            context.setVariable("UPSTREAM_SYNC_COUNT", upstreamExec.getSyncCount() != null ? upstreamExec.getSyncCount() : 0L);
            context.setVariable("UPSTREAM_DURATION_MS", upstreamExec.getDurationMs() != null ? upstreamExec.getDurationMs() : 0L);

            return Boolean.TRUE.equals(spelParser.parseExpression(expression).getValue(context, Boolean.class));
        } catch (Exception e) {
            log.error("条件表达式求值失败: expression={}", expression, e);
            return false;
        }
    }

    /**
     * 延迟触发下游任务
     */
    private void scheduleDelayedTrigger(TaskDependency dep, String parentExecutionId) {
        String key = dep.getId() + "-" + parentExecutionId;

        long delaySeconds = dep.getDelaySeconds() != null ? dep.getDelaySeconds() : 0;
        java.util.Date triggerTime = new java.util.Date(System.currentTimeMillis() + delaySeconds * 1000L);

        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                doTriggerDownstream(dep.getDownstreamTaskId(), parentExecutionId);
            } finally {
                scheduledTriggers.remove(key);
            }
        }, triggerTime);

        scheduledTriggers.put(key, future);
        log.info("已安排延迟触发: dependencyId={}, delaySeconds={}",
                dep.getId(), dep.getDelaySeconds());
    }

    /**
     * 执行下游任务触发
     */
    private void doTriggerDownstream(Long downstreamTaskId, String parentExecutionId) {
        try {
            // 记录依赖触发执行
            taskExecutionService.recordDependencyExecution(downstreamTaskId, parentExecutionId);

            // TODO: 调用任务执行引擎执行下游任务
            log.info("触发下游任务: downstreamTaskId={}, parentExecutionId={}",
                    downstreamTaskId, parentExecutionId);
        } catch (Exception e) {
            log.error("触发下游任务失败: downstreamTaskId={}", downstreamTaskId, e);
        }
    }

    /**
     * 检测是否会产生循环依赖（迭代实现，避免深栈溢出）
     */
    private boolean hasCycle(Long upstreamTaskId, Long downstreamTaskId) {
        return pathExistsIter(downstreamTaskId, upstreamTaskId);
    }

    /**
     * 使用 BFS 迭代检测从 from 到 to 是否存在路径
     */
    private boolean pathExistsIter(Long from, Long to) {
        if (from.equals(to)) {
            return true;
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.offer(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<TaskDependency> downstreams = baseMapper.selectByUpstreamTaskId(current);
            for (TaskDependency dep : downstreams) {
                Long next = dep.getDownstreamTaskId();
                if (next.equals(to)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.offer(next);
                }
            }
        }
        return false;
    }

    /**
     * 获取任务DAG数据（仅当前任务上下游一层）
     */
    public TaskDagVO getTaskDag(Long taskId) {
        TaskDagVO vo = new TaskDagVO();
        vo.setTaskId(taskId);

        List<TaskDagNode> nodes = new ArrayList<>();
        List<TaskDagEdge> edges = new ArrayList<>();

        // 添加当前任务节点
        Task currentTask = taskMapper.selectById(taskId);
        if (currentTask != null) {
            nodes.add(buildNode(currentTask, true));
        }

        // 添加上游任务节点和边
        List<TaskDependency> upstreams = baseMapper.selectByDownstreamTaskId(taskId);
        for (TaskDependency dep : upstreams) {
            Task task = taskMapper.selectById(dep.getUpstreamTaskId());
            if (task != null) {
                nodes.add(buildNode(task, false));
                edges.add(new TaskDagEdge(task.getId(), taskId, dep.getDependencyType()));
            }
        }

        // 添加下游任务节点和边
        List<TaskDependency> downstreams = baseMapper.selectByUpstreamTaskId(taskId);
        for (TaskDependency dep : downstreams) {
            Task task = taskMapper.selectById(dep.getDownstreamTaskId());
            if (task != null) {
                nodes.add(buildNode(task, false));
                edges.add(new TaskDagEdge(taskId, task.getId(), dep.getDependencyType()));
            }
        }

        vo.setNodes(nodes);
        vo.setEdges(edges);
        return vo;
    }

    private TaskDagNode buildNode(Task task, boolean isCurrent) {
        TaskDagNode node = new TaskDagNode();
        node.setId(task.getId());
        node.setTaskCode(task.getTaskCode());
        node.setTaskName(task.getTaskName());
        node.setTaskType(task.getTaskType());
        node.setStatus(task.getStatus());
        node.setCurrent(isCurrent);
        // 填充最近执行状态
        if (taskExecutionService != null) {
            TaskExecution lastExecution = taskExecutionService.lambdaQuery()
                    .eq(TaskExecution::getTaskId, task.getId())
                    .orderByDesc(TaskExecution::getStartTime)
                    .last("LIMIT 1")
                    .one();
            if (lastExecution != null) {
                node.setLastExecutionStatus(lastExecution.getStatus());
            }
        }
        return node;
    }

    /**
     * DAG VO
     */
    @lombok.Data
    public static class TaskDagVO {
        private Long taskId;
        private List<TaskDagNode> nodes;
        private List<TaskDagEdge> edges;
    }

    /**
     * DAG节点
     */
    @lombok.Data
    public static class TaskDagNode {
        private Long id;
        private String taskCode;
        private String taskName;
        private String taskType;
        private Integer status;
        private boolean current;
        private String lastExecutionStatus;
    }

    /**
     * DAG边
     */
    @lombok.Data
    public static class TaskDagEdge {
        private Long source;
        private Long target;
        private String dependencyType;

        public TaskDagEdge(Long source, Long target, String dependencyType) {
            this.source = source;
            this.target = target;
            this.dependencyType = dependencyType;
        }
    }

    /**
     * 依赖关系VO
     */
    @lombok.Data
    public static class TaskDependencyVO {
        private Long taskId;
        private Task currentTask;
        private List<TaskDependencyExt> upstreamDependencies;
        private List<TaskDependencyExt> downstreamDependencies;
    }

    /**
     * 扩展依赖关系（包含关联任务信息）
     */
    @lombok.Data
    public static class TaskDependencyExt {
        private Long id;
        private Long upstreamTaskId;
        private Long downstreamTaskId;
        private String dependencyType;
        private String conditionExpression;
        private Integer delaySeconds;
        private String taskCode;
        private String taskName;
        private String taskType;
    }

    /**
     * 添加依赖请求
     */
    @lombok.Data
    public static class AddDependencyRequest {
        private Long upstreamTaskId;
        private String dependencyType = "SUCCESS";
        private String conditionExpression;
        private Integer delaySeconds = 0;
        private Long createUserId;
    }
}
