package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.util.CronUtils;
import com.mogu.data.integration.service.SyncEngineService;
import com.mogu.data.integration.service.SyncTaskService;
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
    private final SyncTaskService syncTaskService;
    private final SyncEngineService syncEngineService;

    public SyncTaskSchedulerManager(
            @Qualifier("syncTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            SyncTaskService syncTaskService,
            SyncEngineService syncEngineService) {
        this.taskScheduler = taskScheduler;
        this.syncTaskService = syncTaskService;
        this.syncEngineService = syncEngineService;
    }

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化所有启用状态的任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        log.info("初始化同步任务调度...");
        List<SyncTask> tasks = syncTaskService.lambdaQuery()
                .eq(SyncTask::getDeleted, 0)
                .eq(SyncTask::getStatus, 1)
                .list();
        for (SyncTask task : tasks) {
            if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
                schedule(task.getId(), task.getCronExpression());
            }
        }
        log.info("已加载 {} 个定时同步任务", tasks.size());
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
            syncEngineService.execute(taskId);
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

}
