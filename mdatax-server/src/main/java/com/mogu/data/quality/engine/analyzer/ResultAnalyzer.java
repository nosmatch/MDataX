package com.mogu.data.quality.engine.analyzer;

import com.mogu.data.quality.engine.model.CheckExecutionResult;
import com.mogu.data.quality.engine.model.QualityCheckContext;
import com.mogu.data.quality.entity.QualityReport;
import com.mogu.data.quality.enums.QualityLevel;
import com.mogu.data.quality.enums.CheckStatus;
import com.mogu.data.quality.constants.QualityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结果分析器
 * 负责分析所有检查结果，计算质量分数，生成质量报告
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Component
public class ResultAnalyzer {

    /**
     * 分析检查结果，生成质量报告
     *
     * @param context 质量检查上下文
     * @return 质量报告实体
     */
    public QualityReport analyze(QualityCheckContext context) {
        log.info("开始分析质量检查结果[checkId={}]", context.getCheckId());

        List<CheckExecutionResult> results = context.getResults();

        // 1. 统计检查结果
        int totalRules = results.size();
        int passedRules = (int) results.stream()
            .filter(r -> r.getSuccess() && CheckStatus.PASS.getCode().equals(r.getCheckResult().getStatus()))
            .count();
        int failedRules = (int) results.stream()
            .filter(r -> r.getSuccess() && CheckStatus.FAIL.getCode().equals(r.getCheckResult().getStatus()))
            .count();
        int warnedRules = (int) results.stream()
            .filter(r -> r.getSuccess() && CheckStatus.WARN.getCode().equals(r.getCheckResult().getStatus()))
            .count();
        int errorRules = (int) results.stream()
            .filter(r -> !r.getSuccess())
            .count();

        // 2. 计算质量分数
        int qualityScore = calculateQualityScore(totalRules, passedRules, failedRules, warnedRules, errorRules);

        // 3. 确定质量等级
        String qualityLevel = determineQualityLevel(qualityScore);

        // 4. 更新上下文
        context.setTotalRules(totalRules);
        context.setPassedRules(passedRules);
        context.setFailedRules(failedRules);
        context.setWarnedRules(warnedRules);
        context.setQualityScore(qualityScore);
        context.setQualityLevel(qualityLevel);

        log.info("质量分析完成: 总规则数={}, 通过={}, 失败={}, 警告={}, 错误={}, 质量分数={}, 质量等级={}",
            totalRules, passedRules, failedRules, warnedRules, errorRules, qualityScore, qualityLevel);

        // 5. 生成质量报告实体
        return buildQualityReport(context);
    }

    /**
     * 计算质量分数（0-100）
     *
     * 计算规则：
     * - 基础分 = (通过规则数 / 总规则数) * 100
     * - 失败规则扣分：每个失败规则扣10分
     * - 警告规则扣分：每个警告规则扣5分
     * - 错误规则扣分：每个错误规则扣20分
     * - 最低分数为0分
     *
     * @param totalRules 总规则数
     * @param passedRules 通过规则数
     * @param failedRules 失败规则数
     * @param warnedRules 警告规则数
     * @param errorRules 错误规则数
     * @return 质量分数
     */
    private int calculateQualityScore(int totalRules, int passedRules, int failedRules,
                                     int warnedRules, int errorRules) {
        if (totalRules == 0) {
            return QualityConstants.DEFAULT_QUALITY_SCORE;
        }

        // 基础分
        double baseScore = ((double) passedRules / totalRules) * 100;

        // 扣分
        double deduction = failedRules * QualityConstants.FAIL_RULE_DEDUCTION +
                          warnedRules * QualityConstants.WARN_RULE_DEDUCTION +
                          errorRules * QualityConstants.ERROR_RULE_DEDUCTION;

        // 计算最终分数
        int finalScore = (int) Math.round(baseScore - deduction);

        // 限制在0-100之间
        return Math.max(0, Math.min(100, finalScore));
    }

    /**
     * 根据质量分数确定质量等级
     *
     * @param qualityScore 质量分数
     * @return 质量等级
     */
    private String determineQualityLevel(int qualityScore) {
        if (qualityScore >= QualityConstants.EXCELLENT_MIN_SCORE) {
            return QualityLevel.EXCELLENT.getCode();
        } else if (qualityScore >= QualityConstants.GOOD_MIN_SCORE) {
            return QualityLevel.GOOD.getCode();
        } else if (qualityScore >= QualityConstants.PASS_MIN_SCORE) {
            return QualityLevel.PASS.getCode();
        } else {
            return QualityLevel.FAIL.getCode();
        }
    }

    /**
     * 构建质量报告实体
     *
     * @param context 质量检查上下文
     * @return 质量报告实体
     */
    private QualityReport buildQualityReport(QualityCheckContext context) {
        QualityReport report = new QualityReport();

        // 基本信息
        report.setTableId(context.getTableId());
        report.setTableName(context.getTable().getTableName());
        report.setDatabase(context.getTable().getDatabase());
        report.setCheckType(context.getCheckType());
        report.setTriggeredBy(context.getTriggeredBy());

        // 检查结果统计
        report.setTotalRules(context.getTotalRules());
        report.setPassedRules(context.getPassedRules());
        report.setFailedRules(context.getFailedRules());
        report.setWarnedRules(context.getWarnedRules());

        // 质量分数和等级
        report.setQualityScore(context.getQualityScore());
        report.setQualityLevel(context.getQualityLevel());

        // 检查时间
        report.setCheckTime(java.time.LocalDateTime.ofInstant(context.getStartTime().toInstant(), java.time.ZoneId.systemDefault()));
        report.setCheckDuration(context.getTotalDuration().intValue());

        // 计算通过率
        if (context.getTotalRules() > 0) {
            double passRate = ((double) context.getPassedRules() / context.getTotalRules()) * 100;
            report.setPassRate((int) Math.round(passRate));
        } else {
            report.setPassRate(0);
        }

        // 质量状态由qualityLevel字段表示，不需要单独设置

        return report;
    }
}
