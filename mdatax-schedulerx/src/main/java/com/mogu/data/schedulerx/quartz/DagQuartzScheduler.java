package com.mogu.data.schedulerx.quartz;

import com.mogu.data.schedulerx.entity.DagDef;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

/**
 * DAG Quartz 调度管理器
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class DagQuartzScheduler {

    private static final String DAG_JOB_GROUP = "dag_jobs";
    private static final String DAG_TRIGGER_GROUP = "dag_triggers";

    private final Scheduler scheduler;

    public DagQuartzScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 为 DAG 创建或更新 Quartz Cron 调度
     */
    public void scheduleDag(DagDef dagDef) {
        String dagId = dagDef.getDagId();
        String cron = dagDef.getCronExpression();
        if (cron == null || cron.trim().isEmpty()) {
            log.info("[DagQuartzScheduler] DAG 无 Cron 表达式，跳过调度: {}", dagId);
            return;
        }

        String jobName = dagId;
        String triggerName = dagId + "_trigger";

        try {
            JobKey jobKey = new JobKey(jobName, DAG_JOB_GROUP);
            TriggerKey triggerKey = new TriggerKey(triggerName, DAG_TRIGGER_GROUP);

            // 构建 JobDetail
            JobDetail jobDetail = JobBuilder.newJob(DagQuartzJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .usingJobData(DagQuartzJob.DAG_ID_KEY, dagId)
                    .build();

            // 构建 CronTrigger（带时区）
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                    .cronSchedule(cron)
                    .withMisfireHandlingInstructionFireAndProceed();

            if (dagDef.getTimezone() != null && !dagDef.getTimezone().trim().isEmpty()) {
                try {
                    scheduleBuilder.inTimeZone(TimeZone.getTimeZone(dagDef.getTimezone()));
                } catch (Exception e) {
                    log.warn("[DagQuartzScheduler] 时区配置无效，使用系统默认时区: dagId={}, timezone={}", dagId, dagDef.getTimezone());
                }
            }

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(scheduleBuilder)
                    .build();

            // 如果 Job 已存在则更新，否则新增
            if (scheduler.checkExists(jobKey)) {
                scheduler.addJob(jobDetail, true);
                if (scheduler.checkExists(triggerKey)) {
                    scheduler.rescheduleJob(triggerKey, trigger);
                    log.info("[DagQuartzScheduler] 更新 DAG 定时调度: dagId={}, cron={}", dagId, cron);
                } else {
                    scheduler.scheduleJob(trigger);
                    log.info("[DagQuartzScheduler] 新增 DAG 定时 Trigger: dagId={}, cron={}", dagId, cron);
                }
            } else {
                scheduler.scheduleJob(jobDetail, trigger);
                log.info("[DagQuartzScheduler] 创建 DAG 定时调度: dagId={}, cron={}", dagId, cron);
            }
        } catch (SchedulerException e) {
            log.error("[DagQuartzScheduler] 调度 DAG 失败: dagId={}", dagId, e);
            throw new RuntimeException("调度 DAG 失败: " + dagId, e);
        }
    }

    /**
     * 删除 DAG 的 Quartz 调度
     */
    public void unscheduleDag(String dagId) {
        String jobName = dagId;
        String triggerName = dagId + "_trigger";

        try {
            TriggerKey triggerKey = new TriggerKey(triggerName, DAG_TRIGGER_GROUP);
            JobKey jobKey = new JobKey(jobName, DAG_JOB_GROUP);

            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            log.info("[DagQuartzScheduler] 删除 DAG 定时调度: {}", dagId);
        } catch (SchedulerException e) {
            log.error("[DagQuartzScheduler] 删除 DAG 调度失败: dagId={}", dagId, e);
            throw new RuntimeException("删除 DAG 调度失败: " + dagId, e);
        }
    }

    /**
     * 暂停 DAG 的 Quartz 调度
     */
    public void pauseDag(String dagId) {
        String triggerName = dagId + "_trigger";

        try {
            TriggerKey triggerKey = new TriggerKey(triggerName, DAG_TRIGGER_GROUP);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.pauseTrigger(triggerKey);
                log.info("[DagQuartzScheduler] 暂停 DAG 定时调度: {}", dagId);
            }
        } catch (SchedulerException e) {
            log.error("[DagQuartzScheduler] 暂停 DAG 调度失败: dagId={}", dagId, e);
            throw new RuntimeException("暂停 DAG 调度失败: " + dagId, e);
        }
    }

    /**
     * 恢复 DAG 的 Quartz 调度
     */
    public void resumeDag(String dagId) {
        String triggerName = dagId + "_trigger";

        try {
            TriggerKey triggerKey = new TriggerKey(triggerName, DAG_TRIGGER_GROUP);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.resumeTrigger(triggerKey);
                log.info("[DagQuartzScheduler] 恢复 DAG 定时调度: {}", dagId);
            }
        } catch (SchedulerException e) {
            log.error("[DagQuartzScheduler] 恢复 DAG 调度失败: dagId={}", dagId, e);
            throw new RuntimeException("恢复 DAG 调度失败: " + dagId, e);
        }
    }
}
