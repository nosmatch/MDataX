package com.mogu.data.quality.controller;

import com.mogu.data.common.Result;
import com.mogu.data.quality.service.QualityDashboardService;
import com.mogu.data.quality.vo.QualityDashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 质量大盘控制器
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@RestController
@RequestMapping("/quality/dashboard")
public class QualityDashboardController {

    @Autowired
    private QualityDashboardService qualityDashboardService;

    /**
     * 获取整体质量概况
     *
     * @return 整体概况VO
     */
    @GetMapping("/overview")
    public Result<QualityDashboardVO.OverviewVO> getOverview() {
        log.info("获取整体质量概况请求");

        try {
            QualityDashboardVO.OverviewVO overview = qualityDashboardService.getOverview();

            return Result.ok(overview);

        } catch (Exception e) {
            log.error("获取整体质量概况异常", e);
            return Result.error("获取整体质量概况失败: " + e.getMessage());
        }
    }

    /**
     * 获取质量趋势
     *
     * @param days 查询天数（默认30天）
     * @return 趋势数据列表
     */
    @GetMapping("/trend")
    public Result<List<QualityDashboardVO.TrendVO>> getTrend(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {

        log.info("获取质量趋势请求[days={}]", days);

        try {
            List<QualityDashboardVO.TrendVO> trends = qualityDashboardService.getTrend(days);

            return Result.ok(trends);

        } catch (Exception e) {
            log.error("获取质量趋势异常", e);
            return Result.error("获取质量趋势失败: " + e.getMessage());
        }
    }

    /**
     * 获取质量分布
     *
     * @return 质量分布VO
     */
    @GetMapping("/distribution")
    public Result<QualityDashboardVO.DistributionVO> getDistribution() {
        log.info("获取质量分布请求");

        try {
            QualityDashboardVO.DistributionVO distribution = qualityDashboardService.getDistribution();

            return Result.ok(distribution);

        } catch (Exception e) {
            log.error("获取质量分布异常", e);
            return Result.error("获取质量分布失败: " + e.getMessage());
        }
    }

    /**
     * 获取质量TOP榜
     *
     * @param limit 返回数量限制（默认10）
     * @return TOP榜列表
     */
    @GetMapping("/top")
    public Result<List<QualityDashboardVO.TopTableVO>> getTopTables(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {

        log.info("获取质量TOP榜请求[limit={}]", limit);

        try {
            List<QualityDashboardVO.TopTableVO> topTables = qualityDashboardService.getTopTables(limit);

            return Result.ok(topTables);

        } catch (Exception e) {
            log.error("获取质量TOP榜异常", e);
            return Result.error("获取质量TOP榜失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近异常
     *
     * @param limit 返回数量限制（默认10）
     * @return 异常列表
     */
    @GetMapping("/anomalies")
    public Result<List<QualityDashboardVO.AnomalyVO>> getRecentAnomalies(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {

        log.info("获取最近异常请求[limit={}]", limit);

        try {
            List<QualityDashboardVO.AnomalyVO> anomalies = qualityDashboardService.getRecentAnomalies(limit);

            return Result.ok(anomalies);

        } catch (Exception e) {
            log.error("获取最近异常异常", e);
            return Result.error("获取最近异常失败: " + e.getMessage());
        }
    }

    /**
     * 获取质量大盘完整数据（所有指标）
     *
     * @param trendDays 趋势天数（默认7天）
     * @param topLimit TOP榜数量（默认10）
     * @param anomalyLimit 异常数量（默认10）
     * @return 完整的大盘数据
     */
    @GetMapping("/all")
    public Result<Object> getDashboardAll(
            @RequestParam(value = "trendDays", defaultValue = "7") Integer trendDays,
            @RequestParam(value = "topLimit", defaultValue = "10") Integer topLimit,
            @RequestParam(value = "anomalyLimit", defaultValue = "10") Integer anomalyLimit) {

        log.info("获取质量大盘完整数据[trendDays={}, topLimit={}, anomalyLimit={}]",
            trendDays, topLimit, anomalyLimit);

        try {
            // 并行获取所有数据（提升性能）
            QualityDashboardVO.OverviewVO overview = qualityDashboardService.getOverview();
            List<QualityDashboardVO.TrendVO> trends = qualityDashboardService.getTrend(trendDays);
            QualityDashboardVO.DistributionVO distribution = qualityDashboardService.getDistribution();
            List<QualityDashboardVO.TopTableVO> topTables = qualityDashboardService.getTopTables(topLimit);
            List<QualityDashboardVO.AnomalyVO> anomalies = qualityDashboardService.getRecentAnomalies(anomalyLimit);

            // 组装完整数据
            java.util.Map<String, Object> dashboardData = new java.util.HashMap<>();
            dashboardData.put("overview", overview);
            dashboardData.put("trend", trends);
            dashboardData.put("distribution", distribution);
            dashboardData.put("topTables", topTables);
            dashboardData.put("recentAnomalies", anomalies);

            return Result.ok(dashboardData);

        } catch (Exception e) {
            log.error("获取质量大盘完整数据异常", e);
            return Result.error("获取质量大盘完整数据失败: " + e.getMessage());
        }
    }
}
