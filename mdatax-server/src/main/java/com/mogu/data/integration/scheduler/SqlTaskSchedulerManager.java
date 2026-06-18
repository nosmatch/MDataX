package com.mogu.data.integration.scheduler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.util.CronUtils;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SqlTaskService;
import com.mogu.data.integration.service.TaskExecutionService;
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
    private final TaskExecutionService taskExecutionService;
    private final com.mogu.data.integration.mapper.TaskMapper taskMapper;
    private final com.mogu.data.integration.mapper.SqlTaskMapper sqlTaskMapper;

    public SqlTaskSchedulerManager(
            @Qualifier("sqlTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            SqlTaskService sqlTaskService,
            SqlTaskEngineService sqlTaskEngineService,
            TaskExecutionService taskExecutionService,
            com.mogu.data.integration.mapper.TaskMapper taskMapper,
            com.mogu.data.integration.mapper.SqlTaskMapper sqlTaskMapper) {
        this.taskScheduler = taskScheduler;
        this.sqlTaskService = sqlTaskService;
        this.sqlTaskEngineService = sqlTaskEngineService;
        this.taskExecutionService = taskExecutionService;
        this.taskMapper = taskMapper;
        this.sqlTaskMapper = sqlTaskMapper;
    }

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化所有启用状态的SQL任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        log.info("初始化SQL任务调度...");
        long page = 1;
        long size = 500;
        long totalLoaded = 0;
        while (true) {
            Page<SqlTask> pageResult = sqlTaskService.lambdaQuery()
                    .eq(SqlTask::getDeleted, 0)
                    .eq(SqlTask::getStatus, 1)
                    .page(new Page<>(page, size));
            List<SqlTask> tasks = pageResult.getRecords();
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (SqlTask task : tasks) {
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
        log.info("已加载 {} 个定时SQL任务", totalLoaded);
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
    public String triggerSqlTask(SqlTask task) {
        log.info("手动触发SQL任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId, task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                sqlTaskEngineService.execute(task.getId());
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发SQL任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return executionId;
    }

    @Override
    public String triggerSyncTask(SyncTask task) {
        throw new UnsupportedOperationException("SqlTaskSchedulerManager 不支持同步任务手动触发");
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
        if ("SQL".equals(task.getTaskType())) {
            SqlTask sqlTask = sqlTaskMapper.selectById(task.getId());
            if (sqlTask != null) {
                scheduleSqlTask(sqlTask);
            }
        }
    }

    @Override
    public void cancelTask(Long taskId) {
        SqlTask sqlTask = sqlTaskMapper.selectById(taskId);
        if (sqlTask != null) {
            cancelSqlTask(taskId);
        }
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
        SqlTask sqlTask = sqlTaskMapper.selectById(task.getId());
        if (sqlTask == null) {
            throw new IllegalArgumentException("SQL任务不存在: " + task.getId());
        }
        return triggerSqlTask(sqlTask);
    }

    private String generateExecutionId() {
        return "EXEC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

}
