package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.TaskExecution;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.vo.TaskExecutionVO;
import com.mogu.data.integration.mapper.TaskExecutionMapper;
import com.mogu.data.integration.mapper.TaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.BeanUtils;

/**
 * 任务执行记录服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
public class TaskExecutionService extends ServiceImpl<TaskExecutionMapper, TaskExecution> {

    private final TaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IdGeneratorService idGeneratorService;

    @Autowired
    public TaskExecutionService(TaskMapper taskMapper, ApplicationEventPublisher eventPublisher, IdGeneratorService idGeneratorService) {
        this.taskMapper = taskMapper;
        this.eventPublisher = eventPublisher;
        this.idGeneratorService = idGeneratorService;
    }

    @Autowired
    @Lazy
    private TaskDependencyService taskDependencyService;

    /**
     * 分页查询执行记录
     */
    public Page<TaskExecutionVO> pageExecutions(Long taskId, String status, String triggerType,
                                               Long triggerUserId, LocalDateTime startTime,
                                               LocalDateTime endTime, long page, long size) {
        // 使用普通QueryWrapper并手动指定表别名
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TaskExecutionVO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();

        if (taskId != null) {
            wrapper.eq("te.task_id", taskId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("te.status", status);
        }
        if (StringUtils.hasText(triggerType)) {
            wrapper.eq("te.trigger_type", triggerType);
        }
        if (triggerUserId != null) {
            wrapper.eq("te.trigger_user_id", triggerUserId);
        }
        if (startTime != null) {
            wrapper.ge("te.start_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("te.start_time", endTime);
        }

        return baseMapper.pageWithTaskName(new Page<>(page, size), wrapper);
    }

    /**
     * 获取执行详情（含任务名称）
     */
    public TaskExecutionVO getExecutionDetailWithTask(String executionId) {
        TaskExecution execution = lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (execution == null) {
            return null;
        }

        TaskExecutionVO vo = new TaskExecutionVO();
        BeanUtils.copyProperties(execution, vo);

        // 查询任务名称和类型
        Task task = taskMapper.selectById(execution.getTaskId());
        if (task != null) {
            vo.setTaskName(task.getTaskName());
            vo.setTaskType(task.getTaskType());
        }

        return vo;
    }

    /**
     * 统计指定时间范围内的执行记录
     */
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> result = new HashMap<>();

        log.info("查询执行记录统计: startTime={}, endTime={}", startTime, endTime);

        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            wrapper.ge(TaskExecution::getStartTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(TaskExecution::getStartTime, endTime);
        }
        long total = count(wrapper);
        result.put("total", total);

        log.info("总执行记录数: total={}", total);

        for (String status : new String[]{"PENDING", "RUNNING", "SUCCESS", "FAILED", "TIMEOUT", "KILLED"}) {
            result.put(status.toLowerCase() + "Count", 0L);
        }
        for (TaskExecutionMapper.StatusCount sc : baseMapper.countByStatus(startTime, endTime)) {
            if (StringUtils.hasText(sc.getStatus())) {
                result.put(sc.getStatus().toLowerCase() + "Count", sc.getCount());
                log.info("状态统计: status={}, count={}", sc.getStatus(), sc.getCount());
            }
        }

        log.info("统计结果: {}", result);
        return result;
    }

    /**
     * 记录手动触发执行
     */
    @Transactional
    public void recordManualExecution(Long taskId, String executionId, String schedulerInstanceId,
                                       Long triggerUserId) {
        TaskExecution execution = new TaskExecution();
        execution.setExecutionId(executionId);
        execution.setTaskId(taskId);
        execution.setTriggerType("MANUAL");
        execution.setTriggerUserId(triggerUserId);
        execution.setSchedulerInstanceId(schedulerInstanceId);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus("RUNNING");
        execution.setAttemptNumber(1);
        execution.setMaxAttempts(1);

        save(execution);
        log.info("记录手动执行: taskId={}, executionId={}", taskId, executionId);
    }

    /**
     * 记录定时触发执行
     */
    @Transactional
    public void recordScheduleExecution(Long taskId, String executionId, String schedulerInstanceId) {
        TaskExecution execution = new TaskExecution();
        execution.setExecutionId(executionId);
        execution.setTaskId(taskId);
        execution.setTriggerType("SCHEDULE");
        execution.setSchedulerInstanceId(schedulerInstanceId);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus("RUNNING");
        execution.setAttemptNumber(1);
        execution.setMaxAttempts(1);

        save(execution);
        log.info("记录定时执行: taskId={}, executionId={}", taskId, executionId);
    }

    /**
     * 记录依赖触发执行
     */
    @Transactional
    public void recordDependencyExecution(Long taskId, String parentExecutionId) {
        String executionId = generateExecutionId();

        TaskExecution execution = new TaskExecution();
        execution.setExecutionId(executionId);
        execution.setTaskId(taskId);
        execution.setTriggerType("DEPENDENCY");
        execution.setParentExecutionId(parentExecutionId);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus("PENDING");
        execution.setAttemptNumber(1);
        execution.setMaxAttempts(1);

        save(execution);
        log.info("记录依赖执行: taskId={}, executionId={}, parentExecutionId={}",
                taskId, executionId, parentExecutionId);
    }

    /**
     * 更新执行状态（成功）
     */
    @Transactional
    public void finishExecution(String executionId, Integer affectedRows, Integer syncCount) {
        TaskExecution execution = lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (execution == null) {
            log.warn("执行记录不存在: executionId={}", executionId);
            return;
        }

        execution.setStatus("SUCCESS");
        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMs(java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());
        if (affectedRows != null) {
            execution.setAffectedRows(affectedRows);
        }
        if (syncCount != null) {
            execution.setSyncCount(syncCount);
        }

        updateById(execution);
        log.info("执行完成: executionId={}, durationMs={}", executionId, execution.getDurationMs());

        // 事务提交后触发下游依赖任务
        publishDownstreamTriggerEvent(execution.getTaskId(), executionId, "SUCCESS");
    }

    /**
     * 更新执行状态（失败）
     */
    @Transactional
    public void failExecution(String executionId, String errorMsg) {
        TaskExecution execution = lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (execution == null) {
            log.warn("执行记录不存在: executionId={}", executionId);
            return;
        }

        execution.setStatus("FAILED");
        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMs(java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());
        execution.setErrorMsg(errorMsg);

        updateById(execution);
        log.info("执行失败: executionId={}, error={}", executionId, errorMsg);

        // 事务提交后触发下游依赖任务（如果配置为失败也触发）
        publishDownstreamTriggerEvent(execution.getTaskId(), executionId, "FAILED");
    }

    /**
     * 终止执行
     */
    @Transactional
    public void killExecution(String executionId) {
        TaskExecution execution = lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }
        if (!"RUNNING".equals(execution.getStatus())) {
            throw new IllegalArgumentException("只能终止运行中的执行");
        }

        execution.setStatus("KILLED");
        execution.setEndTime(LocalDateTime.now());

        updateById(execution);
        log.info("执行已终止: executionId={}", executionId);
    }

    /**
     * 重试执行
     */
    @Transactional
    public String retryExecution(String executionId) {
        TaskExecution oldExecution = lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (oldExecution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }
        if (!"FAILED".equals(oldExecution.getStatus()) && !"TIMEOUT".equals(oldExecution.getStatus())) {
            throw new IllegalArgumentException("只能重试失败或超时的执行");
        }

        String newExecutionId = generateExecutionId();
        TaskExecution newExecution = new TaskExecution();
        newExecution.setExecutionId(newExecutionId);
        newExecution.setTaskId(oldExecution.getTaskId());
        newExecution.setTriggerType("MANUAL");
        newExecution.setStartTime(LocalDateTime.now());
        newExecution.setStatus("RUNNING");
        newExecution.setAttemptNumber(oldExecution.getAttemptNumber() + 1);
        newExecution.setMaxAttempts(oldExecution.getMaxAttempts());
        newExecution.setParentExecutionId(oldExecution.getParentExecutionId());

        save(newExecution);
        log.info("执行重试: oldExecutionId={}, newExecutionId={}", executionId, newExecutionId);
        return newExecutionId;
    }

    /**
     * 发布下游触发事件（事务提交后处理）
     */
    private void publishDownstreamTriggerEvent(Long taskId, String executionId, String upstreamStatus) {
        eventPublisher.publishEvent(new DownstreamTriggerEvent(this, taskId, executionId, upstreamStatus));
    }

    /**
     * 事务提交后触发下游任务
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDownstreamTriggerEvent(DownstreamTriggerEvent event) {
        try {
            taskDependencyService.triggerDownstreamTasks(event.getTaskId(), event.getExecutionId(), event.getUpstreamStatus());
        } catch (Exception e) {
            log.error("触发下游任务失败: taskId={}, executionId={}",
                    event.getTaskId(), event.getExecutionId(), e);
        }
    }

    private String generateExecutionId() {
        return idGeneratorService.generateExecutionId();
    }

    /**
     * 查询任务是否有运行中的执行记录
     */
    public long getRunningCountByTaskId(Long taskId) {
        return lambdaQuery()
                .eq(TaskExecution::getTaskId, taskId)
                .eq(TaskExecution::getStatus, "RUNNING")
                .count();
    }

    /**
     * 下游触发事件
     */
    public static class DownstreamTriggerEvent {
        private final Object source;
        private final Long taskId;
        private final String executionId;
        private final String upstreamStatus;

        public DownstreamTriggerEvent(Object source, Long taskId, String executionId, String upstreamStatus) {
            this.source = source;
            this.taskId = taskId;
            this.executionId = executionId;
            this.upstreamStatus = upstreamStatus;
        }

        public Object getSource() { return source; }
        public Long getTaskId() { return taskId; }
        public String getExecutionId() { return executionId; }
        public String getUpstreamStatus() { return upstreamStatus; }
    }

}
