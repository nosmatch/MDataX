package com.mogu.data.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mogu.data.quality.entity.QualityReport;
import com.mogu.data.quality.mapper.QualityReportMapper;
import com.mogu.data.quality.vo.QualityDashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 质量大盘服务
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Service
public class QualityDashboardService {

    @Autowired
    private QualityReportMapper qualityReportMapper;

    @Autowired
    private com.mogu.data.metadata.mapper.MetadataTableMapper metadataTableMapper;

    /**
     * 获取整体质量概况
     *
     * @return 整体概况VO
     */
    public QualityDashboardVO.OverviewVO getOverview() {
        log.info("获取整体质量概况");

        // 查询所有表的最新报告
        List<QualityReport> latestReports = getAllLatestReports();

        // 统计数据
        QualityDashboardVO.OverviewVO overview = new QualityDashboardVO.OverviewVO();

        if (latestReports.isEmpty()) {
            // 暂无任何检查记录，返回默认值
            overview.setTotalTables(0L);
            overview.setExcellentTables(0L);
            overview.setGoodTables(0L);
            overview.setPassTables(0L);
            overview.setFailTables(0L);
            overview.setAvgQualityScore(100);
            overview.setTotalRules(0);
            overview.setPassedRules(0);
            overview.setFailedRules(0);
            overview.setWarnedRules(0);
            overview.setCheckToday(0);
            return overview;
        }

        // 统计表总数
        int totalTables = latestReports.size();

        // 统计各质量等级的表数量
        long excellentTables = latestReports.stream()
            .filter(r -> "EXCELLENT".equals(r.getQualityLevel()))
            .count();

        long goodTables = latestReports.stream()
            .filter(r -> "GOOD".equals(r.getQualityLevel()))
            .count();

        long passTables = latestReports.stream()
            .filter(r -> "PASS".equals(r.getQualityLevel()))
            .count();

        long failTables = latestReports.stream()
            .filter(r -> "FAIL".equals(r.getQualityLevel()))
            .count();

        // 计算平均质量分数
        double avgScore = latestReports.stream()
            .mapToInt(QualityReport::getQualityScore)
            .average()
            .orElse(100.0);

        // 统计规则执行情况
        int totalRules = latestReports.stream()
            .mapToInt(QualityReport::getTotalRules)
            .sum();

        int passedRules = latestReports.stream()
            .mapToInt(QualityReport::getPassedRules)
            .sum();

        int failedRules = latestReports.stream()
            .mapToInt(QualityReport::getFailedRules)
            .sum();

        int warnedRules = latestReports.stream()
            .mapToInt(QualityReport::getWarnedRules)
            .sum();

        // 统计今日检查次数
        Long checkCount = qualityReportMapper.selectCount(
            new LambdaQueryWrapper<QualityReport>()
                .ge(QualityReport::getReportDate, LocalDate.now())
        );
        int checkToday = checkCount.intValue();

        // 设置返回数据
        overview.setTotalTables((long) totalTables);
        overview.setExcellentTables(excellentTables);
        overview.setGoodTables(goodTables);
        overview.setPassTables(passTables);
        overview.setFailTables(failTables);
        overview.setAvgQualityScore((int) Math.round(avgScore));
        overview.setTotalRules(totalRules);
        overview.setPassedRules(passedRules);
        overview.setFailedRules(failedRules);
        overview.setWarnedRules(warnedRules);
        overview.setCheckToday(checkToday);

        log.info("整体质量概况查询成功[totalTables={}, avgScore={}]", totalTables, (int) avgScore);

        return overview;
    }

    /**
     * 获取质量趋势
     *
     * @param days 查询天数
     * @return 趋势数据列表
     */
    public List<QualityDashboardVO.TrendVO> getTrend(Integer days) {
        log.info("获取质量趋势[days={}]", days);

        // 计算查询的起始日期
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        // 查询指定日期范围的所有报告
        List<QualityReport> reports = qualityReportMapper.selectList(
            new LambdaQueryWrapper<QualityReport>()
                .ge(QualityReport::getReportDate, startDate)
                .le(QualityReport::getReportDate, endDate)
                .orderByAsc(QualityReport::getReportDate)
        );

        // 按日期分组统计
        Map<LocalDate, List<QualityReport>> groupedByDate = reports.stream()
            .collect(Collectors.groupingBy(QualityReport::getReportDate));

        // 构建趋势数据
        List<QualityDashboardVO.TrendVO> trends = new ArrayList<>();

        // 填充缺失的日期
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<QualityReport> dayReports = groupedByDate.getOrDefault(date, new ArrayList<>());

            QualityDashboardVO.TrendVO trend = new QualityDashboardVO.TrendVO();
            trend.setDate(date.toString());

            if (!dayReports.isEmpty()) {
                // 计算当天的平均质量分数
                double avgScore = dayReports.stream()
                    .mapToInt(QualityReport::getQualityScore)
                    .average()
                    .orElse(100.0);

                trend.setQualityScore((int) Math.round(avgScore));
                trend.setCheckCount(dayReports.size());
            } else {
                trend.setQualityScore(100);
                trend.setCheckCount(0);
            }

            trends.add(trend);
        }

        log.info("质量趋势查询成功[dataCount={}]", trends.size());

        return trends;
    }

    /**
     * 获取质量分布
     *
     * @return 质量分布VO
     */
    public QualityDashboardVO.DistributionVO getDistribution() {
        log.info("获取质量分布");

        // 查询所有表的最新报告
        List<QualityReport> latestReports = getAllLatestReports();

        QualityDashboardVO.DistributionVO distribution = new QualityDashboardVO.DistributionVO();

        if (latestReports.isEmpty()) {
            // 暂无数据，返回默认分布
            distribution.setExcellent(0);
            distribution.setGood(0);
            distribution.setPass(0);
            distribution.setFail(0);
            return distribution;
        }

        // 统计各质量等级的表数量
        long excellent = latestReports.stream()
            .filter(r -> "EXCELLENT".equals(r.getQualityLevel()))
            .count();

        long good = latestReports.stream()
            .filter(r -> "GOOD".equals(r.getQualityLevel()))
            .count();

        long pass = latestReports.stream()
            .filter(r -> "PASS".equals(r.getQualityLevel()))
            .count();

        long fail = latestReports.stream()
            .filter(r -> "FAIL".equals(r.getQualityLevel()))
            .count();

        distribution.setExcellent((int) excellent);
        distribution.setGood((int) good);
        distribution.setPass((int) pass);
        distribution.setFail((int) fail);

        log.info("质量分布查询成功[excellent={}, good={}, pass={}, fail={}]",
            excellent, good, pass, fail);

        return distribution;
    }

    /**
     * 获取质量TOP榜
     *
     * @param limit 返回数量限制
     * @return TOP榜列表
     */
    public List<QualityDashboardVO.TopTableVO> getTopTables(Integer limit) {
        log.info("获取质量TOP榜[limit={}]", limit);

        // 查询所有表的最新报告
        List<QualityReport> latestReports = getAllLatestReports();

        // 收集所有的table_id
        List<Long> tableIds = latestReports.stream()
            .map(QualityReport::getTableId)
            .distinct()
            .collect(Collectors.toList());

        // 批量查询表信息
        final Map<Long, com.mogu.data.metadata.entity.MetadataTable> tableMap;
        if (!tableIds.isEmpty()) {
            List<com.mogu.data.metadata.entity.MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<com.mogu.data.metadata.entity.MetadataTable>()
                    .in(com.mogu.data.metadata.entity.MetadataTable::getId, tableIds)
            );
            tableMap = tables.stream()
                .collect(Collectors.toMap(
                    com.mogu.data.metadata.entity.MetadataTable::getId,
                    t -> t
                ));
        } else {
            tableMap = new HashMap<>();
        }

        // 按质量分数排序，取前N名
        List<QualityDashboardVO.TopTableVO> topTables = latestReports.stream()
            .sorted((r1, r2) -> Integer.compare(r2.getQualityScore(), r1.getQualityScore()))
            .limit(limit)
            .map(report -> {
                QualityDashboardVO.TopTableVO vo = new QualityDashboardVO.TopTableVO();
                vo.setTableId(report.getTableId());

                // 从metadata_table中获取表名和数据库名
                com.mogu.data.metadata.entity.MetadataTable table = tableMap.get(report.getTableId());
                if (table != null) {
                    vo.setTableName(table.getTableName());
                    vo.setDatabase(table.getDatabaseName());
                }

                vo.setQualityScore(report.getQualityScore());
                vo.setQualityLevel(report.getQualityLevel());
                vo.setPassRate(report.getPassRate());
                vo.setCheckTime(report.getCheckTime());
                return vo;
            })
            .collect(Collectors.toList());

        log.info("质量TOP榜查询成功[topCount={}]", topTables.size());

        return topTables;
    }

    /**
     * 获取最近异常
     *
     * @param limit 返回数量限制
     * @return 异常列表
     */
    public List<QualityDashboardVO.AnomalyVO> getRecentAnomalies(Integer limit) {
        log.info("获取最近异常[limit={}]", limit);

        // 查询最近的质量报告，筛选出质量等级为FAIL或WARN的
        List<QualityReport> reports = qualityReportMapper.selectList(
            new LambdaQueryWrapper<QualityReport>()
                .in(QualityReport::getQualityLevel, Arrays.asList("FAIL", "WARN"))
                .orderByDesc(QualityReport::getLastCheckTime)
                .last("LIMIT " + limit)
        );

        // 收集所有的table_id
        List<Long> tableIds = reports.stream()
            .map(QualityReport::getTableId)
            .distinct()
            .collect(Collectors.toList());

        // 批量查询表信息
        final Map<Long, com.mogu.data.metadata.entity.MetadataTable> tableMap;
        if (!tableIds.isEmpty()) {
            List<com.mogu.data.metadata.entity.MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<com.mogu.data.metadata.entity.MetadataTable>()
                    .in(com.mogu.data.metadata.entity.MetadataTable::getId, tableIds)
            );
            tableMap = tables.stream()
                .collect(Collectors.toMap(
                    com.mogu.data.metadata.entity.MetadataTable::getId,
                    t -> t
                ));
        } else {
            tableMap = new HashMap<>();
        }

        // 转换为异常VO
        List<QualityDashboardVO.AnomalyVO> anomalies = reports.stream()
            .map(report -> {
                QualityDashboardVO.AnomalyVO vo = new QualityDashboardVO.AnomalyVO();
                vo.setTableId(report.getTableId());

                // 从metadata_table中获取表名和数据库名
                com.mogu.data.metadata.entity.MetadataTable table = tableMap.get(report.getTableId());
                if (table != null) {
                    vo.setTableName(table.getTableName());
                    vo.setDatabase(table.getDatabaseName());
                }

                vo.setQualityScore(report.getQualityScore());
                vo.setQualityLevel(report.getQualityLevel());
                vo.setFailedRules(report.getFailedRules());
                vo.setCheckTime(report.getCheckTime());
                vo.setCheckType(report.getCheckType());
                return vo;
            })
            .collect(Collectors.toList());

        log.info("最近异常查询成功[anomalyCount={}]", anomalies.size());

        return anomalies;
    }

    /**
     * 获取所有表的最新报告
     *
     * @return 最新报告列表
     */
    private List<QualityReport> getAllLatestReports() {
        // 使用子查询：每个表取最新的一条报告
        // 这里简化实现，查询所有报告后在内存中去重
        List<QualityReport> allReports = qualityReportMapper.selectList(
            new LambdaQueryWrapper<QualityReport>()
                .orderByDesc(QualityReport::getUpdatedAt)
        );

        // 按tableId去重，保留每个表最新的报告
        Map<Long, QualityReport> latestMap = new LinkedHashMap<>();
        for (QualityReport report : allReports) {
            if (!latestMap.containsKey(report.getTableId())) {
                latestMap.put(report.getTableId(), report);
            }
        }

        return new ArrayList<>(latestMap.values());
    }
}
