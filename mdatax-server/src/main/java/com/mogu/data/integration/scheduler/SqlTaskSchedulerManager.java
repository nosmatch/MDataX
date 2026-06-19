package com.mogu.data.integration.scheduler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskQualityDetail;
import com.mogu.data.integration.entity.TaskSyncDetail;
import com.mogu.data.integration.mapper.TaskQualityDetailMapper;
import com.mogu.data.integration.mapper.TaskSyncDetailMapper;
import com.mogu.data.integration.service.QualityTaskEngineService;
import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SqlTaskService;
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
    private final SyncEngineService syncEngineService;
    private final QualityTaskEngineService qualityTaskEngineService;
    private final TaskExecutionService taskExecutionService;
    private final TaskSyncDetailMapper taskSyncDetailMapper;
    private final TaskQualityDetailMapper taskQualityDetailMapper;
    private final com.mogu.data.integration.mapper.SqlTaskMapper sqlTaskMapper;
    private final com.mogu.data.integration.service.IdGeneratorService idGeneratorService;

    public SqlTaskSchedulerManager(
            @Qualifier("sqlTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            SqlTaskService sqlTaskService,
            SqlTaskEngineService sqlTaskEngineService,
            SyncEngineService syncEngineService,
            QualityTaskEngineService qualityTaskEngineService,
            TaskExecutionService taskExecutionService,
            TaskSyncDetailMapper taskSyncDetailMapper,
            TaskQualityDetailMapper taskQualityDetailMapper,
            com.mogu.data.integration.mapper.SqlTaskMapper sqlTaskMapper,
            com.mogu.data.integration.service.IdGeneratorService idGeneratorService) {
        this.taskScheduler = taskScheduler;
        this.sqlTaskService = sqlTaskService;
        this.sqlTaskEngineService = sqlTaskEngineService;
        this.syncEngineService = syncEngineService;
        this.qualityTaskEngineService = qualityTaskEngineService;
        this.taskExecutionService = taskExecutionService;
        this.taskSyncDetailMapper = taskSyncDetailMapper;
        this.taskQualityDetailMapper = taskQualityDetailMapper;
        this.sqlTaskMapper = sqlTaskMapper;
        this.idGeneratorService = idGeneratorService;
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

        String executionId = generateExecutionId();
        taskExecutionService.recordScheduleExecution(taskId, executionId, null);

        try {
            sqlTaskEngineService.execute(taskId);
            taskExecutionService.finishExecution(executionId, null, null);
        } catch (Exception e) {
            log.error("定时SQL任务执行失败: taskId={}", taskId, e);
            taskExecutionService.failExecution(executionId, e.getMessage());
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
    public Long triggerSqlTask(SqlTask task) {
        log.info("手动触发SQL任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId.toString(), task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                sqlTaskEngineService.execute(task.getId());
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发SQL任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return Long.parseLong(executionId);
    }

    @Override
    public Long triggerSyncTask(SyncTask task) {
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
        if (task == null) {
            return;
        }
        if ("SQL".equals(task.getTaskType()) && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedule(task.getId(), task.getCronExpression());
        } else if ("SYNC".equals(task.getTaskType()) && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedule(task.getId(), task.getCronExpression());
        } else if ("QUALITY".equals(task.getTaskType()) && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
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
        if ("SQL".equals(task.getTaskType())) {
            Long result = triggerUnifiedSqlTask(task);
            return result != null ? result.toString() : null;
        } else if ("SYNC".equals(task.getTaskType())) {
            Long result = triggerUnifiedSyncTask(task);
            return result != null ? result.toString() : null;
        } else if ("QUALITY".equals(task.getTaskType())) {
            Long result = triggerUnifiedQualityTask(task);
            return result != null ? result.toString() : null;
        }
        throw new IllegalArgumentException("不支持的任务类型: " + task.getTaskType());
    }

    private Long triggerUnifiedQualityTask(Task task) {
        TaskQualityDetail detail = taskQualityDetailMapper.selectByTaskId(task.getId());
        if (detail == null) {
            throw new IllegalArgumentException("质量任务详情不存在: " + task.getId());
        }
        log.info("手动触发质量监控任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId.toString(), task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                qualityTaskEngineService.execute(task.getId());
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发质量监控任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return Long.parseLong(executionId);
    }

    private Long triggerUnifiedSqlTask(Task task) {
        SqlTask sqlTask = sqlTaskMapper.selectById(task.getId());
        if (sqlTask == null) {
            throw new IllegalArgumentException("SQL任务不存在: " + task.getId());
        }
        return triggerSqlTask(sqlTask);
    }

    private Long triggerUnifiedSyncTask(Task task) {
        TaskSyncDetail detail = taskSyncDetailMapper.selectByTaskId(task.getId());
        if (detail == null) {
            throw new IllegalArgumentException("同步任务详情不存在: " + task.getId());
        }
        log.info("手动触发同步任务: taskId={}", task.getId());
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(
                task.getId(), executionId, executionId.toString(), task.getCreateUserId());
        taskScheduler.execute(() -> {
            try {
                syncEngineService.execute(task.getId(), null);
                taskExecutionService.finishExecution(executionId, null, null);
            } catch (Exception e) {
                log.error("手动触发同步任务失败: taskId={}, executionId={}", task.getId(), executionId, e);
                taskExecutionService.failExecution(executionId, e.getMessage());
            }
        });
        return Long.parseLong(executionId);
    }

    private String generateExecutionId() {
        return idGeneratorService.generateExecutionId();
    }

}
