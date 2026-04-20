package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.service.SyncEngineService;
import com.mogu.data.integration.service.SyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 同步任务调度管理器
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskSchedulerManager {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SyncTaskService syncTaskService;
    private final SyncEngineService syncEngineService;

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
        String springCron = convertQuartzToSpringCron(cronExpression);
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

    /**
     * 将 Quartz Cron 表达式转换为 Spring Cron 表达式
     * Quartz: 秒 分 时 日 月 周 [年]  支持 ?
     * Spring: 秒 分 时 日 月 周      不支持 ?
     */
    public static String convertQuartzToSpringCron(String quartzCron) {
        if (quartzCron == null || quartzCron.isEmpty()) {
            return quartzCron;
        }
        String[] parts = quartzCron.trim().split("\\s+");
        // 去掉第7位（年字段）
        if (parts.length >= 7) {
            String[] newParts = new String[6];
            System.arraycopy(parts, 0, newParts, 0, 6);
            parts = newParts;
        }
        // 将 ? 替换为 *
        for (int i = 0; i < parts.length; i++) {
            if ("?".equals(parts[i])) {
                parts[i] = "*";
            }
        }
        return String.join(" ", parts);
    }

}
