package com.mogu.data.quality.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mogu.data.common.Result;
import com.mogu.data.quality.entity.QualityCheckResult;
import com.mogu.data.quality.service.QualityReportService;
import com.mogu.data.quality.vo.QualityReportVO;
import com.mogu.data.quality.vo.TrendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 质量报告控制器
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@RestController
@RequestMapping("/quality/reports")
public class QualityReportController {

    @Autowired
    private QualityReportService qualityReportService;

    /**
     * 获取所有质量报告列表（分页）
     *
     * @param pageNum 页码
     * @param pageSize 页大小
     * @param database 数据库名（可选）
     * @param tableName 表名（可选）
     * @param qualityLevel 质量等级（可选）
     * @return 分页的质量报告列表
     */
    @GetMapping
    public Result<IPage<QualityReportVO>> listReports(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "tableName", required = false) String tableName,
            @RequestParam(value = "qualityLevel", required = false) String qualityLevel) {

        log.info("获取质量报告列表请求[page={}, size={}, database={}, tableName={}, level={}]",
            pageNum, pageSize, database, tableName, qualityLevel);

        try {
            IPage<QualityReportVO> page = qualityReportService.listReports(
                pageNum, pageSize, database, tableName, qualityLevel);

            return Result.ok(page);

        } catch (Exception e) {
            log.error("获取质量报告列表异常", e);
            return Result.error("获取质量报告列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有质量报告列表（分页）- 别名路径
     *
     * @param pageNum 页码
     * @param pageSize 页大小
     * @param database 数据库名（可选）
     * @param tableName 表名（可选）
     * @param qualityLevel 质量等级（可选）
     * @return 分页的质量报告列表
     */
    @GetMapping("/history")
    public Result<IPage<QualityReportVO>> listReportsHistory(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "tableName", required = false) String tableName,
            @RequestParam(value = "qualityLevel", required = false) String qualityLevel) {

        return listReports(pageNum, pageSize, database, tableName, qualityLevel);
    }

    /**
     * 获取表的质量报告
     *
     * @param tableId 表ID
     * @return 质量报告VO
     */
    @GetMapping("/table/{tableId}")
    public Result<QualityReportVO> getReport(@PathVariable("tableId") Long tableId) {
        log.info("获取表质量报告请求[tableId={}]", tableId);

        try {
            QualityReportVO report = qualityReportService.getReport(tableId);

            return Result.ok(report);

        } catch (Exception e) {
            log.error("获取表质量报告异常", e);
            return Result.error("获取表质量报告失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的质量趋势
     *
     * @param tableId 表ID
     * @param days 查询天数（默认30天）
     * @return 趋势数据列表
     */
    @GetMapping("/table/{tableId}/trend")
    public Result<List<TrendVO>> getTrend(
            @PathVariable("tableId") Long tableId,
            @RequestParam(value = "days", defaultValue = "30") Integer days) {

        log.info("获取表质量趋势请求[tableId={}, days={}]", tableId, days);

        try {
            List<TrendVO> trends = qualityReportService.getTrend(tableId, days);

            return Result.ok(trends);

        } catch (Exception e) {
            log.error("获取表质量趋势异常", e);
            return Result.error("获取表质量趋势失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的检查历史
     *
     * @param tableId 表ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 分页的质量报告列表
     */
    @GetMapping("/table/{tableId}/history")
    public Result<IPage<QualityReportVO>> getCheckHistory(
            @PathVariable("tableId") Long tableId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        log.info("获取表检查历史请求[tableId={}, page={}, size={}]", tableId, pageNum, pageSize);

        try {
            IPage<QualityReportVO> history = qualityReportService.getCheckHistory(
                tableId, pageNum, pageSize);

            return Result.ok(history);

        } catch (Exception e) {
            log.error("获取表检查历史异常", e);
            return Result.error("获取表检查历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取检查结果详情
     *
     * @param resultId 结果ID
     * @return 检查结果详情
     */
    @GetMapping("/results/{resultId}")
    public Result<QualityCheckResult> getCheckResult(@PathVariable("resultId") Long resultId) {
        log.info("获取检查结果详情请求[resultId={}]", resultId);

        try {
            QualityCheckResult result = qualityReportService.getCheckResult(resultId);

            return Result.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("获取检查结果失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取检查结果异常", e);
            return Result.error("获取检查结果失败: " + e.getMessage());
        }
    }

    /**
     * 获取报告的所有检查结果
     *
     * @param reportId 报告ID
     * @return 检查结果列表
     */
    @GetMapping("/report/{reportId}/results")
    public Result<List<QualityCheckResult>> getReportResults(@PathVariable("reportId") Long reportId) {
        log.info("获取报告检查结果请求[reportId={}]", reportId);

        try {
            List<QualityCheckResult> results = qualityReportService.getReportCheckResults(reportId);

            return Result.ok(results);

        } catch (Exception e) {
            log.error("获取报告检查结果异常", e);
            return Result.error("获取报告检查结果失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的综合质量概览
     *
     * @param tableId 表ID
     * @return 综合概览数据
     */
    @GetMapping("/table/{tableId}/overview")
    public Result<Object> getOverview(@PathVariable("tableId") Long tableId) {
        log.info("获取表综合质量概览请求[tableId={}]", tableId);

        try {
            // 获取最新报告
            QualityReportVO report = qualityReportService.getReport(tableId);

            // 获取趋势数据（最近7天）
            List<TrendVO> trends = qualityReportService.getTrend(tableId, 7);

            // 构建概览数据
            java.util.Map<String, Object> overview = new java.util.HashMap<>();
            overview.put("currentReport", report);
            overview.put("trend", trends);

            // 计算趋势统计
            if (trends != null && !trends.isEmpty()) {
                int avgScore = (int) trends.stream()
                    .mapToInt(TrendVO::getQualityScore)
                    .average()
                    .orElse(100);
                overview.put("avgScore", avgScore);

                int maxScore = trends.stream()
                    .mapToInt(TrendVO::getQualityScore)
                    .max()
                    .orElse(100);
                overview.put("maxScore", maxScore);

                int minScore = trends.stream()
                    .mapToInt(TrendVO::getQualityScore)
                    .min()
                    .orElse(100);
                overview.put("minScore", minScore);
            }

            return Result.ok(overview);

        } catch (Exception e) {
            log.error("获取表综合质量概览异常", e);
            return Result.error("获取表综合质量概览失败: " + e.getMessage());
        }
    }
}
