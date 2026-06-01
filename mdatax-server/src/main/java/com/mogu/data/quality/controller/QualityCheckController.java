package com.mogu.data.quality.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.quality.dto.ManualCheckRequest;
import com.mogu.data.quality.engine.model.QualityCheckContext;
import com.mogu.data.quality.service.QualityReportService;
import com.mogu.data.quality.service.QualityRuleService;
import com.mogu.data.quality.engine.service.QualityCheckEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 质量检查执行控制器
 * 提供手动执行质量检查的API接口
 *
 * @author fengzhu
 * @since 2026-05-19
 */
@Slf4j
@RestController
@RequestMapping("/quality/check")
@Validated
public class QualityCheckController {

    @Autowired
    private QualityCheckEngineService qualityCheckEngineService;

    @Autowired
    private QualityRuleService qualityRuleService;

    @Autowired
    private QualityReportService qualityReportService;

    /**
     * 手动执行质量检查（批量）
     *
     * @param request 检查请求
     * @return 检查结果
     */
    @PostMapping
    public Result<Map<String, Object>> manualCheck(@Valid @RequestBody ManualCheckRequest request) {
        log.info("手动执行质量检查请求: {}", request);

        try {
            Long tableId = request.getTableId();
            Boolean async = request.getAsync() != null ? request.getAsync() : false;

            // 获取当前用户
            String triggeredBy = LoginUser.currentUsername();
            if (triggeredBy == null) {
                triggeredBy = "system";
            }

            log.info("开始执行质量检查: tableId={}, async={}, triggeredBy={}",
                    tableId, async, triggeredBy);

            Map<String, Object> result = new HashMap<>();

            if (async) {
                // 异步执行
                qualityCheckEngineService.checkAsync(tableId, "MANUAL", triggeredBy);

                result.put("status", "submitted");
                result.put("message", "检查任务已提交，正在后台执行");
                result.put("tableId", tableId);
                result.put("triggeredBy", triggeredBy);

                log.info("检查任务已提交到后台: tableId={}", tableId);

            } else {
                // 同步执行
                QualityCheckContext context = qualityCheckEngineService.check(tableId, "MANUAL", triggeredBy);

                result.put("status", "completed");
                result.put("message", "检查已完成");
                result.put("checkId", context.getCheckId());
                result.put("tableId", tableId);
                result.put("taskCount", context.getTasks() != null ? context.getTasks().size() : 0);
                result.put("totalDuration", context.getTotalDuration());
                result.put("startTime", context.getStartTime());
                result.put("endTime", context.getEndTime());

                log.info("检查已完成: checkId={}, tableId={}, duration={}ms",
                        context.getCheckId(), tableId, context.getTotalDuration());
            }

            return Result.ok(result);

        } catch (Exception e) {
            log.error("手动执行质量检查失败", e);
            return Result.error("执行质量检查失败: " + e.getMessage());
        }
    }

    /**
     * 执行单个规则检查
     *
     * @param ruleId 规则ID
     * @return 检查结果
     */
    @PostMapping("/rules/{ruleId}/execute")
    public Result<Map<String, Object>> executeRule(@PathVariable Long ruleId) {
        log.info("执行单个规则检查: ruleId={}", ruleId);

        try {
            // 获取当前用户
            String triggeredBy = LoginUser.currentUsername();
            if (triggeredBy == null) {
                triggeredBy = "system";
            }

            // 查询规则
            com.mogu.data.quality.entity.QualityRule rule = qualityRuleService.getRule(ruleId);
            if (rule == null) {
                return Result.error("规则不存在: ruleId=" + ruleId);
            }

            if (!rule.getEnabled()) {
                return Result.error("规则已禁用，无法执行: ruleId=" + ruleId);
            }

            log.info("开始执行规则检查: ruleId={}, ruleName={}, tableId={}",
                    ruleId, rule.getRuleName(), rule.getTableId());

            // 异步执行检查
            qualityCheckEngineService.checkAsync(rule.getTableId(), "MANUAL", triggeredBy);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "submitted");
            result.put("message", "规则检查任务已提交，正在后台执行");
            result.put("ruleId", ruleId);
            result.put("ruleName", rule.getRuleName());
            result.put("tableId", rule.getTableId());
            result.put("triggeredBy", triggeredBy);

            log.info("规则检查任务已提交: ruleId={}", ruleId);

            return Result.ok(result);

        } catch (Exception e) {
            log.error("执行规则检查失败: ruleId={}", ruleId, e);
            return Result.error("执行规则检查失败: " + e.getMessage());
        }
    }

    /**
     * 执行表的所有规则检查
     *
     * @param tableId 表ID
     * @param async 是否异步执行
     * @return 检查结果
     */
    @PostMapping("/tables/{tableId}/execute")
    public Result<Map<String, Object>> executeTableRules(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "true") Boolean async) {

        log.info("执行表的所有规则检查: tableId={}, async={}", tableId, async);

        try {
            // 获取当前用户
            String triggeredBy = LoginUser.currentUsername();
            if (triggeredBy == null) {
                triggeredBy = "system";
            }

            Map<String, Object> result = new HashMap<>();

            if (async) {
                // 异步执行
                qualityCheckEngineService.checkAsync(tableId, "MANUAL", triggeredBy);

                result.put("status", "submitted");
                result.put("message", "表规则检查任务已提交，正在后台执行");
                result.put("tableId", tableId);
                result.put("triggeredBy", triggeredBy);

                log.info("表规则检查任务已提交: tableId={}", tableId);

            } else {
                // 同步执行
                QualityCheckContext context = qualityCheckEngineService.check(tableId, "MANUAL", triggeredBy);

                result.put("status", "completed");
                result.put("message", "表规则检查已完成");
                result.put("checkId", context.getCheckId());
                result.put("tableId", tableId);
                result.put("taskCount", context.getTasks() != null ? context.getTasks().size() : 0);
                result.put("totalDuration", context.getTotalDuration());
                result.put("startTime", context.getStartTime());
                result.put("endTime", context.getEndTime());

                log.info("表规则检查已完成: checkId={}, tableId={}, duration={}ms",
                        context.getCheckId(), tableId, context.getTotalDuration());
            }

            return Result.ok(result);

        } catch (Exception e) {
            log.error("执行表规则检查失败: tableId={}", tableId, e);
            return Result.error("执行表规则检查失败: " + e.getMessage());
        }
    }

    /**
     * 查询表的最新检查报告
     *
     * @param tableId 表ID
     * @return 质量报告
     */
    @GetMapping("/tables/{tableId}/report")
    public Result<Object> getLatestReport(@PathVariable Long tableId) {
        log.info("查询表的最新质量报告: tableId={}", tableId);

        try {
            com.mogu.data.quality.vo.QualityReportVO report = qualityReportService.getReport(tableId);

            if (report == null) {
                return Result.error("未找到表的质量报告: tableId=" + tableId);
            }

            log.info("查询到质量报告: tableId={}, reportDate={}, qualityScore={}",
                    tableId, report.getReportDate(), report.getQualityScore());

            return Result.ok(report);

        } catch (Exception e) {
            log.error("查询质量报告失败: tableId={}", tableId, e);
            return Result.error("查询质量报告失败: " + e.getMessage());
        }
    }

    /**
     * 快速检查（只执行检查，不生成报告）
     *
     * @param tableId 表ID
     * @return 检查结果摘要
     */
    @PostMapping("/tables/{tableId}/quick-check")
    public Result<Map<String, Object>> quickCheck(@PathVariable Long tableId) {
        log.info("快速检查: tableId={}", tableId);

        try {
            // 获取当前用户
            String triggeredBy = LoginUser.currentUsername();
            if (triggeredBy == null) {
                triggeredBy = "system";
            }

            // 异步执行检查
            qualityCheckEngineService.checkAsync(tableId, "MANUAL", triggeredBy);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "submitted");
            result.put("message", "快速检查任务已提交");
            result.put("tableId", tableId);

            log.info("快速检查任务已提交: tableId={}", tableId);

            return Result.ok(result);

        } catch (Exception e) {
            log.error("快速检查失败: tableId={}", tableId, e);
            return Result.error("快速检查失败: " + e.getMessage());
        }
    }
}
