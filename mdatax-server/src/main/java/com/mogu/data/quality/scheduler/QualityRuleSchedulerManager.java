package com.mogu.data.quality.scheduler;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mogu.data.quality.entity.QualityRule;
import com.mogu.data.quality.engine.service.QualityCheckEngineService;
import com.mogu.data.quality.service.QualityRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 质量规则定时调度管理器
 * 负责管理质量规则的定时执行
 *
 * @author fengzhu
 * @since 2026-05-19
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.type", havingValue = "local")
public class QualityRuleSchedulerManager {

    @Autowired
    @Qualifier("taskScheduler")
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private QualityRuleService qualityRuleService;

    @Autowired
    private QualityCheckEngineService qualityCheckEngineService;

    /**
     * 存储已调度的任务
     * key: ruleId, value: ScheduledFuture
     */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化所有定时规则
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        log.info("========================================");
        log.info("初始化质量规则定时调度...");
        log.info("========================================");

        try {
            // 查询所有启用的定时规则
            IPage<QualityRule> page = qualityRuleService.listRules(
                    null,  // tableId
                    null,  // ruleName
                    null,  // ruleType
                    null,  // ruleTemplate
                    true,  // enabled
                    1,     // pageNum
                    10000  // pageSize - 获取所有规则
            );

            List<QualityRule> allRules = page.getRecords();
            List<QualityRule> rules = allRules.stream()
                    .filter(rule -> rule.getCheckMode() != null
                            && rule.getCheckMode().contains("SCHEDULED")
                            && rule.getScheduleCron() != null
                            && !rule.getScheduleCron().trim().isEmpty())
                    .collect(java.util.stream.Collectors.toList());

            log.info("查询到 {} 个启用的定时规则", rules.size());

            int successCount = 0;
            int failCount = 0;

            for (QualityRule rule : rules) {
                try {
                    if (rule.getScheduleCron() != null && !rule.getScheduleCron().trim().isEmpty()) {
                        schedule(rule);
                        successCount++;
                    } else {
                        log.warn("规则[{}]的Cron表达式为空，跳过调度", rule.getRuleName());
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("注册规则调度失败: ruleId={}, ruleName={}, error={}",
                            rule.getId(), rule.getRuleName(), e.getMessage());
                    failCount++;
                }
            }

            log.info("质量规则定时调度初始化完成:");
            log.info("  - 成功注册: {} 个", successCount);
            log.info("  - 注册失败: {} 个", failCount);
            log.info("  - 总计: {} 个", rules.size());
            log.info("========================================");

        } catch (Exception e) {
            log.error("初始化质量规则定时调度失败", e);
        }
    }

    /**
     * 注册单个规则的定时任务
     *
     * @param rule 质量规则
     */
    public void schedule(QualityRule rule) {
        if (rule == null || rule.getId() == null) {
            log.warn("规则或规则ID为空，无法调度");
            return;
        }

        // 先取消已存在的任务
        cancel(rule.getId());

        try {
            String cronExpression = rule.getScheduleCron().trim();
            CronTrigger trigger = new CronTrigger(cronExpression);

            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeRule(rule),
                    trigger
            );

            scheduledTasks.put(rule.getId(), future);

            log.info("规则定时调度已注册: ruleId={}, ruleName={}, cron={}",
                    rule.getId(), rule.getRuleName(), cronExpression);

        } catch (Exception e) {
            log.error("注册规则定时调度失败: ruleId={}, ruleName={}, cron={}, error={}",
                    rule.getId(), rule.getRuleName(), rule.getScheduleCron(), e.getMessage());
            throw new RuntimeException("注册规则定时调度失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取消规则的定时任务
     *
     * @param ruleId 规则ID
     */
    public void cancel(Long ruleId) {
        if (ruleId == null) {
            return;
        }

        ScheduledFuture<?> future = scheduledTasks.remove(ruleId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("规则定时调度已取消: ruleId={}", ruleId);
        }
    }

    /**
     * 重新调度规则
     *
     * @param rule 质量规则
     */
    public void reschedule(QualityRule rule) {
        if (rule == null || rule.getId() == null) {
            log.warn("规则或规则ID为空，无法重新调度");
            return;
        }

        log.info("重新调度规则: ruleId={}, ruleName={}", rule.getId(), rule.getRuleName());

        // 取消旧任务
        cancel(rule.getId());

        // 如果规则启用且支持SCHEDULED模式且有Cron表达式，则重新调度
        if (rule.getEnabled()
                && rule.getCheckMode() != null
                && rule.getCheckMode().contains("SCHEDULED")
                && rule.getScheduleCron() != null
                && !rule.getScheduleCron().trim().isEmpty()) {
            schedule(rule);
        } else {
            log.info("规则不满足调度条件，跳过重新调度: ruleId={}, enabled={}, checkMode={}, hasCron={}",
                    rule.getId(),
                    rule.getEnabled(),
                    rule.getCheckMode(),
                    rule.getScheduleCron() != null && !rule.getScheduleCron().trim().isEmpty());
        }
    }

    /**
     * 执行规则检查
     *
     * @param rule 质量规则
     */
    private void executeRule(QualityRule rule) {
        log.info("========================================");
        log.info("开始执行定时规则检查");
        log.info("规则ID: {}", rule.getId());
        log.info("规则名称: {}", rule.getRuleName());
        log.info("表ID: {}", rule.getTableId());
        log.info("表名: {}", rule.getTableName());
        log.info("========================================");

        long startTime = System.currentTimeMillis();

        try {
            // 异步执行检查，避免阻塞调度线程
            qualityCheckEngineService.checkAsync(
                    rule.getTableId(),
                    "SCHEDULED",
                    "system"
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("定时规则检查任务已提交: ruleId={}, duration={}ms", rule.getId(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("执行定时规则检查失败: ruleId={}, ruleName={}, duration={}ms, error={}",
                    rule.getId(), rule.getRuleName(), duration, e.getMessage(), e);

            // TODO: 可以在这里添加告警逻辑
        }
    }

    /**
     * 获取已调度的任务数量
     *
     * @return 任务数量
     */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
    }

    /**
     * 检查规则是否已调度
     *
     * @param ruleId 规则ID
     * @return 是否已调度
     */
    public boolean isScheduled(Long ruleId) {
        if (ruleId == null) {
            return false;
        }
        ScheduledFuture<?> future = scheduledTasks.get(ruleId);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * 获取所有已调度的规则ID
     *
     * @return 规则ID列表
     */
    public List<Long> getScheduledRuleIds() {
        return scheduledTasks.keySet().stream()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 取消所有调度任务
     */
    public void cancelAll() {
        log.info("取消所有质量规则定时调度...");

        int count = 0;
        for (Map.Entry<Long, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
                count++;
            }
        }

        scheduledTasks.clear();
        log.info("已取消 {} 个定时调度任务", count);
    }
}
