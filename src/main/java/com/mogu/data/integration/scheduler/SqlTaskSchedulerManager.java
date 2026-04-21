package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SqlTaskService;
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
 * SQL任务调度管理器（本地 Spring 调度实现）
 *
 * @author fengzhu
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.type", havingValue = "local")
public class SqlTaskSchedulerManager implements TaskSchedulerManager {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SqlTaskService sqlTaskService;
    private final SqlTaskEngineService sqlTaskEngineService;

    public SqlTaskSchedulerManager(
            @Qualifier("sqlTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            SqlTaskService sqlTaskService,
            SqlTaskEngineService sqlTaskEngineService) {
        this.taskScheduler = taskScheduler;
        this.sqlTaskService = sqlTaskService;
        this.sqlTaskEngineService = sqlTaskEngineService;
    }

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化所有启用状态的SQL任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        log.info("初始化SQL任务调度...");
        List<SqlTask> tasks = sqlTaskService.lambdaQuery()
                .eq(SqlTask::getDeleted, 0)
                .eq(SqlTask::getStatus, 1)
                .list();
        for (SqlTask task : tasks) {
            if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
                schedule(task.getId(), task.getCronExpression());
            }
        }
        log.info("已加载 {} 个定时SQL任务", tasks.size());
    }

    /**
     * 注册定时任务
     */
    public void schedule(Long taskId, String cronExpression) {
        cancel(taskId);
        String springCron = SyncTaskSchedulerManager.convertQuartzToSpringCron(cronExpression);
        try {
            CronTrigger trigger = new CronTrigger(springCron);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeTask(taskId), trigger);
            scheduledTasks.put(taskId, future);
            log.info("SQL任务调度已注册: taskId={}, cron={}", taskId, springCron);
        } catch (Exception e) {
            log.error("注册SQL任务调度失败: taskId={}, cron={}, error={}", taskId, springCron, e.getMessage());
        }
    }

    /**
     * 取消定时任务
     */
    public void cancel(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("SQL任务调度已取消: taskId={}", taskId);
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
     * 执行SQL任务（包装异常处理）
     */
    private void executeTask(Long taskId) {
        log.info("定时触发SQL任务: taskId={}", taskId);
        try {
            sqlTaskEngineService.execute(taskId);
        } catch (Exception e) {
            log.error("定时SQL任务执行失败: taskId={}", taskId, e);
        }
    }

    // ==================== TaskSchedulerManager 接口实现 ====================

    @Override
    public void scheduleSyncTask(SyncTask task) {
        throw new UnsupportedOperationException("SqlTaskSchedulerManager 不支持同步任务调度");
    }

    @Override
    public void scheduleSqlTask(SqlTask task) {
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedule(task.getId(), task.getCronExpression());
        }
    }

    @Override
    public void cancelSyncTask(Long taskId) {
        throw new UnsupportedOperationException("SqlTaskSchedulerManager 不支持同步任务调度");
    }

    @Override
    public void cancelSqlTask(Long taskId) {
        cancel(taskId);
    }

    @Override
    public void deleteSyncTask(Long taskId) {
        throw new UnsupportedOperationException("SqlTaskSchedulerManager 不支持同步任务调度");
    }

    @Override
    public void deleteSqlTask(Long taskId) {
        cancel(taskId);
    }

    @Override
    public void rescheduleSyncTask(SyncTask task) {
        throw new UnsupportedOperationException("SqlTaskSchedulerManager 不支持同步任务调度");
    }

    @Override
    public void rescheduleSqlTask(SqlTask task) {
        if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            reschedule(task.getId(), task.getCronExpression());
        } else {
            cancel(task.getId());
        }
    }

}
