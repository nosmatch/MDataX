package com.mogu.data.integration.scheduler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskSyncDetail;
import com.mogu.data.integration.mapper.TaskMapper;
import com.mogu.data.integration.mapper.TaskSyncDetailMapper;
import com.mogu.data.integration.service.SyncEngineService;
import com.mogu.data.integration.service.TaskExecutionService;
import com.mogu.data.integration.util.CronUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 同步任务调度管理器（本地 Spring 调度实现）
 *
 * @author fengzhu
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.type", havingValue = "local")
public class SyncTaskSchedulerManager implements TaskSchedulerManager {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SyncEngineService syncEngineService;
    private final TaskExecutionService taskExecutionService;
    private final TaskMapper taskMapper;
    private final TaskSyncDetailMapper taskSyncDetailMapper;
    private final com.mogu.data.integration.mapper.SyncTaskMapper syncTaskMapper;
    private final com.mogu.data.integration.service.IdGeneratorService idGeneratorService;

    public SyncTaskSchedulerManager(
            @Qualifier("syncTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            SyncEngineService syncEngineService,
            TaskExecutionService taskExecutionService,
            TaskMapper taskMapper,
            TaskSyncDetailMapper taskSyncDetailMapper,
            com.mogu.data.integration.mapper.SyncTaskMapper syncTaskMapper,
            com.mogu.data.integration.service.IdGeneratorService idGeneratorService) {
        this.taskScheduler = taskScheduler;
        this.syncEngineService = syncEngineService;
        this.taskExecutionService = taskExecutionService;
        this.taskMapper = taskMapper;
        this.taskSyncDetailMapper = taskSyncDetailMapper;
        this.syncTaskMapper = syncTaskMapper;
        this.idGeneratorService = idGeneratorService;
    }

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化所有启用状态的统一同步任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        log.info("初始化同步任务调度...");
        long page = 1;
        long size = 500;
        long totalLoaded = 0;
        while (true) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(Task::getDeleted, 0)
                    .eq(Task::getStatus, 1)
                    .eq(Task::getTaskType, "SYNC");
            Page<Task> pageResult = taskMapper.selectPage(new Page<>(page, size), wrapper);
            List<Task> tasks = pageResult.getRecords();
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (Task task : tasks) {
                if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
                    schedule(task.getId(), task.getCronExpression());
                }
            }
            totalLoaded += tasks.size();
            if (!pageResult.hasNext()) {
                break;
            }
            page++;
        }
        log.info("已加载 {} 个定时同步任务", totalLoaded);
    }

    /**
     * 注册定时任务
     */
    public void schedule(Long taskId, String cronExpression) {
        cancel(taskId);
        String springCron = CronUtils.convertQuartzToSpringCron(cronExpression);
        try {
            CronTrigger trigger = new CronTrigger(springCron);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeTask(taskId), trigger);
            scheduledTasks.put(taskId, future);
            log.info("任务调度已注册: taskId={}, cron={}", taskId, springCron);
        } catch (Exception e) {
            log.error("注册任务调度失败: taskId={}, cron={}, error={}", taskId, springCron, e.getMessage());
        }
    }

    /**
     * 取消定时任务
     */
    public void cancel(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("任务调度已取消: taskId={}", taskId);
        }
    }

    /**
     * 重新调度
     */
    public void reschedule(Long taskId, String cronExpression) {
        cancel(taskId);
        if (cronExpression != null && !cronExpression.isEmpty()) {
            schedule(taskId, cronExpression);
        }
    }

    /**
     * 执行同步任务（包装异常处理）
     */
    private void executeTask(Long taskId) {
        log.info("定时触发同步任务: taskId={}", taskId);
        try {
            syncEngineService.execute(taskId, null);
        } catch (Exception e) {
            log.error("定时同步任务执行失败: taskId={}", taskId, e);
        }
    }

    // ==================== TaskSchedulerManager 接口实现 ====================

    @Override
    public void scheduleSyncTask(SyncTask task) {
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedule(task.getId(), task.getCronExpression());
        }
    }

    @Override
    public void scheduleSqlTask(SqlTask task) {
        throw new UnsupportedOperationException("SyncTaskSchedulerManager 不支持 SQL 任务调度");
    }

    @Override
    public void cancelSyncTask(Long taskId) {
        cancel(taskId);
    }

    @Override
    public void cancelSqlTask(Long taskId) {
        throw new UnsupportedOperationException("SyncTaskSchedulerManager 不支持 SQL 任务调度");
    }

    @Override
    public void deleteSyncTask(Long taskId) {
        cancel(taskId);
    }

    @Override
    public void deleteSqlTask(Long taskId) {
        throw new UnsupportedOperationException("SyncTaskSchedulerManager 不支持 SQL 任务调度");
    }

    @Override
    public void rescheduleSyncTask(SyncTask task) {
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            reschedule(task.getId(), task.getCronExpression());
        } else {
            cancel(task.getId());
        }
    }

    @Override
    public void rescheduleSqlTask(SqlTask task) {
        throw new UnsupportedOperationException("SyncTaskSchedulerManager 不支持 SQL 任务调度");
    }

    @Override
    public void scheduleWorkflow(com.mogu.data.integration.entity.SqlTaskWorkflow workflow) {
        throw new UnsupportedOperationException("本地调度器不支持 Workflow");
    }

    @Override
    public void cancelWorkflow(Long workflowId) {
        throw new UnsupportedOperationException("本地调度器不支持 Workflow");
    }

    @Override
    public void deleteWorkflow(Long workflowId) {
        throw new UnsupportedOperationException("本地调度器不支持 Workflow");
    }

    @Override
    public void rescheduleWorkflow(com.mogu.data.integration.entity.SqlTaskWorkflow workflow) {
        throw new UnsupportedOperationException("本地调度器不支持 Workflow");
    }

    @Override
    public Long triggerSqlTask(SqlTask task) {
        throw new UnsupportedOperationException("SyncTaskSchedulerManager 不支持 SQL 任务手动触发");
    }

    @Override
    public Long triggerSyncTask(SyncTask task) {
        log.info("手动触发同步任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId, task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                syncEngineService.execute(task.getId(), null);
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发同步任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return java.lang.Long.parseLong(executionId);
    }

    @Override
    public String triggerWorkflow(com.mogu.data.integration.entity.SqlTaskWorkflow workflow) {
        throw new UnsupportedOperationException("本地调度器不支持 Workflow 手动触发");
    }

    @Override
    public void stopWorkflowInstance(String instanceId) {
        throw new UnsupportedOperationException("本地调度器不支持实例操作");
    }

    @Override
    public void pauseWorkflowInstance(String instanceId) {
        throw new UnsupportedOperationException("本地调度器不支持实例操作");
    }

    @Override
    public void retryWorkflowInstance(String instanceId) {
        throw new UnsupportedOperationException("本地调度器不支持实例操作");
    }

    @Override
    public String listWorkflowInstances(com.mogu.data.integration.entity.SqlTaskWorkflow workflow, int pageNum, int pageSize) {
        throw new UnsupportedOperationException("本地调度器不支持实例查询");
    }

    @Override
    public String getInstanceDetail(String instanceId) {
        throw new UnsupportedOperationException("本地调度器不支持实例查询");
    }

    @Override
    public String getInstanceTasks(String instanceId) {
        throw new UnsupportedOperationException("本地调度器不支持实例查询");
    }

    @Override
    public String getSqlTaskInstances(Long taskId, int pageNum, int pageSize) {
        throw new UnsupportedOperationException("本地调度器不支持实例查询");
    }

    @Override
    public String getSyncTaskInstances(Long taskId, int pageNum, int pageSize) {
        throw new UnsupportedOperationException("本地调度器不支持实例查询");
    }

    // ==================== 统一任务调度实现 ====================

    @Override
    public void scheduleTask(com.mogu.data.integration.entity.Task task) {
        if (task != null && "SYNC".equals(task.getTaskType())
                && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedule(task.getId(), task.getCronExpression());
        }
    }

    @Override
    public void cancelTask(Long taskId) {
        cancel(taskId);
    }

    @Override
    public void rescheduleTask(com.mogu.data.integration.entity.Task task) {
        cancelTask(task.getId());
        scheduleTask(task);
    }

    @Override
    public String triggerTask(com.mogu.data.integration.entity.Task task) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        if (!"SYNC".equals(task.getTaskType())) {
            throw new IllegalArgumentException("任务类型不是同步任务: " + task.getTaskType());
        }
        TaskSyncDetail detail = taskSyncDetailMapper.selectByTaskId(task.getId());
        if (detail == null) {
            throw new IllegalArgumentException("同步任务详情不存在: " + task.getId());
        }
        return triggerUnifiedSyncTask(task, detail);
    }

    private String triggerUnifiedSyncTask(Task task, TaskSyncDetail detail) {
        log.info("手动触发同步任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId, task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                syncEngineService.execute(task.getId(), null);
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发同步任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return executionId;
    }

    private String generateExecutionId() {
        return idGeneratorService.generateExecutionId();
    }

}
