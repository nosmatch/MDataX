package com.mogu.data.quality.engine.service;

import com.mogu.data.quality.alert.WebhookAlertService;
import com.mogu.data.quality.engine.analyzer.ResultAnalyzer;
import com.mogu.data.quality.engine.executor.SqlExecutor;
import com.mogu.data.quality.engine.model.CheckExecutionResult;
import com.mogu.data.quality.engine.model.CheckTask;
import com.mogu.data.quality.engine.model.QualityCheckContext;
import com.mogu.data.quality.engine.parser.RuleParser;
import com.mogu.data.quality.entity.QualityCheckResult;
import com.mogu.data.quality.entity.QualityReport;
import com.mogu.data.quality.service.QualityCheckResultService;
import com.mogu.data.quality.service.QualityReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 质量检查引擎服务
 * 质量检查的统一入口，整合规则解析、SQL执行、结果分析
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Service
public class QualityCheckEngineService {

    @Autowired
    private RuleParser ruleParser;

    @Autowired
    private SqlExecutor sqlExecutor;

    @Autowired
    private ResultAnalyzer resultAnalyzer;

    @Autowired
    private QualityReportService qualityReportService;

    @Autowired
    private QualityCheckResultService qualityCheckResultService;

    @Autowired
    private Executor qualityCheckExecutor;

    @Autowired
    private WebhookAlertService webhookAlertService;

    /**
     * 执行质量检查（同步）
     *
     * @param tableId 表ID
     * @param checkType 检查类型（REALTIME/SCHEDULED/MANUAL）
     * @param triggeredBy 触发者
     * @return 质量检查上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityCheckContext check(Long tableId, String checkType, String triggeredBy) {
        String checkId = UUID.randomUUID().toString();
        Date startTime = new Date();

        log.info("开始执行质量检查[checkId={}, tableId={}, checkType={}, triggeredBy={}]",
            checkId, tableId, checkType, triggeredBy);

        // 1. 构建检查上下文
        QualityCheckContext context = QualityCheckContext.builder()
            .checkId(checkId)
            .tableId(tableId)
            .checkType(checkType)
            .triggeredBy(triggeredBy)
            .startTime(startTime)
            .build();

        try {
            // 2. 解析规则，生成检查任务
            List<CheckTask> tasks = ruleParser.parseRules(tableId, checkType, triggeredBy);
            context.setTasks(tasks);

            if (tasks.isEmpty()) {
                log.warn("表[{}]没有可执行的检查任务", tableId);
                context.setEndTime(new Date());
                context.setTotalDuration(System.currentTimeMillis() - startTime.getTime());
                return context;
            }

            // 3. 并行执行检查任务
            List<CheckExecutionResult> results = executeCheckTasks(tasks);
            context.setResults(results);

            // 4. 分析结果，生成质量报告
            QualityReport report = resultAnalyzer.analyze(context);
            context.setEndTime(new Date());
            context.setTotalDuration(System.currentTimeMillis() - startTime.getTime());

            // 5. 保存质量报告
            qualityReportService.saveReport(report);

            // 6. 保存检查结果
            saveCheckResults(context, report.getId());

            // 7. 触发告警通知
            webhookAlertService.sendAlertIfNeeded(context);

            log.info("质量检查完成[checkId={}, qualityScore={}, qualityLevel={}, duration={}ms]",
                checkId, context.getQualityScore(), context.getQualityLevel(), context.getTotalDuration());

        } catch (Exception e) {
            log.error("质量检查执行失败[checkId={}]", checkId, e);
            context.setEndTime(new Date());
            context.setTotalDuration(System.currentTimeMillis() - startTime.getTime());
            throw new RuntimeException("质量检查执行失败: " + e.getMessage(), e);
        }

        return context;
    }

    /**
     * 执行质量检查（异步）
     *
     * @param tableId 表ID
     * @param checkType 检查类型
     * @param triggeredBy 触发者
     */
    @Async("qualityCheckExecutor")
    public void checkAsync(Long tableId, String checkType, String triggeredBy) {
        try {
            check(tableId, checkType, triggeredBy);
        } catch (Exception e) {
            log.error("异步质量检查失败[tableId={}, checkType={}]", tableId, checkType, e);
        }
    }

    /**
     * 并行执行检查任务
     *
     * @param tasks 检查任务列表
     * @return 检查执行结果列表
     */
    private List<CheckExecutionResult> executeCheckTasks(List<CheckTask> tasks) {
        log.info("开始并行执行{}个检查任务", tasks.size());

        // 使用CompletableFuture并行执行所有任务
        List<CompletableFuture<CheckExecutionResult>> futures = tasks.stream()
            .map(task -> CompletableFuture.supplyAsync(() -> sqlExecutor.execute(task), qualityCheckExecutor))
            .collect(Collectors.toList());

        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        // 收集结果
        try {
            allFutures.join();
            List<CheckExecutionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            log.info("检查任务执行完成，成功{}个，失败{}个",
                results.stream().filter(CheckExecutionResult::getSuccess).count(),
                results.stream().filter(r -> !r.getSuccess()).count());

            return results;

        } catch (Exception e) {
            log.error("并行执行检查任务失败", e);
            throw new RuntimeException("并行执行检查任务失败", e);
        }
    }

    /**
     * 保存检查结果
     *
     * @param context 检查上下文
     * @param reportId 质量报告ID
     */
    private void saveCheckResults(QualityCheckContext context, Long reportId) {
        List<QualityCheckResult> checkResults = new ArrayList<>();

        for (CheckExecutionResult result : context.getResults()) {
            QualityCheckResult checkResult = new QualityCheckResult();

            // 基本信息
            checkResult.setRuleId(result.getRuleId());
            checkResult.setRuleName(result.getRuleName());
            checkResult.setCheckType(context.getCheckType());
            checkResult.setTriggeredBy(context.getTriggeredBy());

            // 执行信息 - 使用检查时间
            if (result.getStartTime() != null) {
                checkResult.setCheckTime(result.getStartTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
            }
            checkResult.setCheckDuration(result.getDuration().intValue());

            // 结果信息
            if (result.getSuccess()) {
                checkResult.setStatus(result.getCheckResult().getStatus());
                checkResult.setActualValue(result.getCheckResult().getActualValue());
                checkResult.setExpectedValue(result.getCheckResult().getExpectedValue());
                checkResult.setErrorMessage(result.getCheckResult().getErrorMessage());
            } else {
                checkResult.setStatus("ERROR");
                checkResult.setErrorMessage(result.getErrorMessage());
            }

            checkResults.add(checkResult);
        }

        // 批量保存
        qualityCheckResultService.batchSaveResults(checkResults);

        log.info("保存了{}条检查结果", checkResults.size());
    }
}
