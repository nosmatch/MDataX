package com.mogu.data.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.quality.entity.QualityCheckResult;
import com.mogu.data.quality.entity.QualityReport;
import com.mogu.data.quality.mapper.QualityCheckResultMapper;
import com.mogu.data.quality.mapper.QualityReportMapper;
import com.mogu.data.quality.vo.QualityReportVO;
import com.mogu.data.quality.vo.TrendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 质量报告服务
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Service
public class QualityReportService {

    @Autowired
    private QualityReportMapper qualityReportMapper;

    @Autowired
    private QualityCheckResultMapper qualityCheckResultMapper;

    @Autowired
    private MetadataTableMapper metadataTableMapper;

    @Autowired
    private com.mogu.data.quality.mapper.QualityRuleMapper qualityRuleMapper;

    /**
     * 获取表的最新质量报告
     *
     * @param tableId 表ID
     * @return 质量报告VO
     */
    public QualityReportVO getReport(Long tableId) {
        log.info("获取表质量报告[tableId={}]", tableId);

        // 查询最新的质量报告
        QualityReport report = qualityReportMapper.selectOne(
            new LambdaQueryWrapper<QualityReport>()
                .eq(QualityReport::getTableId, tableId)
                .orderByDesc(QualityReport::getCheckTime)
                .last("LIMIT 1")
        );

        if (report == null) {
            log.warn("表[{}]暂无质量报告", tableId);
            return buildEmptyReport(tableId);
        }

        // 转换为VO
        QualityReportVO vo = convertToVO(report);

        log.info("质量报告查询成功[tableId={}, score={}]", tableId, vo.getQualityScore());

        return vo;
    }

    /**
     * 获取表的质量趋势
     *
     * @param tableId 表ID
     * @param days 查询天数
     * @return 趋势数据列表
     */
    public List<TrendVO> getTrend(Long tableId, Integer days) {
        log.info("获取表质量趋势[tableId={}, days={}]", tableId, days);

        // 计算查询的起始日期
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        // 查询指定日期范围的质量报告
        List<QualityReport> reports = qualityReportMapper.selectList(
            new LambdaQueryWrapper<QualityReport>()
                .eq(QualityReport::getTableId, tableId)
                .ge(QualityReport::getReportDate, startDate)
                .le(QualityReport::getReportDate, endDate)
                .orderByAsc(QualityReport::getReportDate)
        );

        // 转换为趋势VO
        List<TrendVO> trends = new ArrayList<>();
        for (QualityReport report : reports) {
            TrendVO trend = new TrendVO();
            trend.setDate(report.getReportDate().toString());
            trend.setQualityScore(report.getQualityScore());
            trend.setQualityLevel(report.getQualityLevel());
            trend.setPassRate(report.getPassRate());
            trend.setTotalRules(report.getTotalRules());
            trend.setPassedRules(report.getPassedRules());
            trend.setFailedRules(report.getFailedRules());
            trends.add(trend);
        }

        log.info("质量趋势查询成功[tableId={}, dataCount={}]", tableId, trends.size());

        return trends;
    }

    /**
     * 获取表的检查历史
     *
     * @param tableId 表ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 分页的质量报告列表
     */
    public IPage<QualityReportVO> getCheckHistory(Long tableId, Integer pageNum, Integer pageSize) {
        log.info("获取表检查历史[tableId={}, page={}, size={}]", tableId, pageNum, pageSize);

        // 分页查询质量报告
        Page<QualityReport> page = new Page<>(pageNum, pageSize);
        IPage<QualityReport> reportPage = qualityReportMapper.selectPage(page,
            new LambdaQueryWrapper<QualityReport>()
                .eq(QualityReport::getTableId, tableId)
                .orderByDesc(QualityReport::getCheckTime)
        );

        // 转换为VO
        List<QualityReportVO> vos = new ArrayList<>();
        for (QualityReport report : reportPage.getRecords()) {
            vos.add(convertToVO(report));
        }

        // 构建返回结果
        IPage<QualityReportVO> voPage = new Page<>(pageNum, pageSize, reportPage.getTotal());
        voPage.setRecords(vos);

        log.info("检查历史查询成功[tableId={}, totalCount={}]", tableId, reportPage.getTotal());

        return voPage;
    }

    /**
     * 获取检查结果详情
     *
     * @param resultId 结果ID
     * @return 检查结果
     */
    public QualityCheckResult getCheckResult(Long resultId) {
        log.info("获取检查结果详情[resultId={}]", resultId);

        QualityCheckResult result = qualityCheckResultMapper.selectById(resultId);

        if (result == null) {
            throw new IllegalArgumentException("检查结果不存在: " + resultId);
        }

        log.info("检查结果查询成功[resultId={}, status={}]", resultId, result.getStatus());

        return result;
    }

    /**
     * 获取报告的所有检查结果
     *
     * @param reportId 报告ID
     * @return 检查结果列表
     */
    public List<QualityCheckResult> getReportCheckResults(Long reportId) {
        log.info("获取报告的检查结果[reportId={}]", reportId);

        // 1. 先查询报告信息，获取 table_id 和 report_date
        QualityReport report = qualityReportMapper.selectById(reportId);
        if (report == null) {
            log.warn("报告不存在[reportId={}]", reportId);
            return new java.util.ArrayList<>();
        }

        log.info("报告信息[reportId={}, tableId={}, reportDate={}]",
            reportId, report.getTableId(), report.getReportDate());

        // 2. 查询该表的所有质量规则
        List<com.mogu.data.quality.entity.QualityRule> rules = qualityRuleMapper.selectList(
            new LambdaQueryWrapper<com.mogu.data.quality.entity.QualityRule>()
                .eq(com.mogu.data.quality.entity.QualityRule::getTableId, report.getTableId())
        );

        if (rules.isEmpty()) {
            log.warn("该表没有配置质量规则[tableId={}]", report.getTableId());
            return new java.util.ArrayList<>();
        }

        // 构建规则ID到规则名称的映射
        java.util.Map<Long, String> ruleNameMap = rules.stream()
            .collect(java.util.stream.Collectors.toMap(
                com.mogu.data.quality.entity.QualityRule::getId,
                com.mogu.data.quality.entity.QualityRule::getRuleName
            ));

        List<Long> ruleIds = rules.stream()
            .map(com.mogu.data.quality.entity.QualityRule::getId)
            .collect(java.util.stream.Collectors.toList());

        log.info("找到规则数量[ruleIds={}]", ruleIds.size());

        // 3. 查询这些规则在报告日期当天的检查结果
        LocalDateTime dayStart = report.getReportDate().atStartOfDay();
        LocalDateTime dayEnd = report.getReportDate().plusDays(1).atStartOfDay();

        List<QualityCheckResult> results = qualityCheckResultMapper.selectList(
            new LambdaQueryWrapper<QualityCheckResult>()
                .in(QualityCheckResult::getRuleId, ruleIds)
                .ge(QualityCheckResult::getCheckTime, dayStart)
                .lt(QualityCheckResult::getCheckTime, dayEnd)
                .orderByDesc(QualityCheckResult::getCheckTime)
        );

        // 填充规则名称
        for (QualityCheckResult result : results) {
            String ruleName = ruleNameMap.get(result.getRuleId());
            if (ruleName != null) {
                result.setRuleName(ruleName);
            }
        }

        log.info("报告检查结果查询成功[reportId={}, resultCount={}]", reportId, results.size());

        return results;
    }

    /**
     * 获取全局报告列表（分页）
     *
     * @param pageNum 页码
     * @param pageSize 页大小
     * @param database 数据库名（可选）
     * @param tableName 表名（可选）
     * @param qualityLevel 质量等级（可选）
     * @return 分页的质量报告列表
     */
    public IPage<QualityReportVO> listReports(Integer pageNum, Integer pageSize,
                                               String database, String tableName, String qualityLevel) {
        log.info("获取全局报告列表[page={}, size={}, database={}, tableName={}, level={}]",
            pageNum, pageSize, database, tableName, qualityLevel);

        // 构建查询条件（只针对质量等级筛选）
        LambdaQueryWrapper<QualityReport> queryWrapper = new LambdaQueryWrapper<>();

        // 按质量等级筛选（如果qualityLevel不为空）
        if (qualityLevel != null && !qualityLevel.trim().isEmpty()) {
            queryWrapper.eq(QualityReport::getQualityLevel, qualityLevel.trim().toUpperCase());
        }

        // 按报告日期降序、质量分数降序排序
        queryWrapper.orderByDesc(QualityReport::getReportDate)
                   .orderByDesc(QualityReport::getQualityScore);

        // 查询所有符合条件的报告
        List<QualityReport> allReports = qualityReportMapper.selectList(queryWrapper);

        // 收集所有的table_id
        List<Long> tableIds = allReports.stream()
            .map(QualityReport::getTableId)
            .distinct()
            .collect(java.util.stream.Collectors.toList());

        // 批量查询表信息
        java.util.Map<Long, MetadataTable> tableMap = new java.util.HashMap<>();
        if (!tableIds.isEmpty()) {
            List<MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>()
                    .in(MetadataTable::getId, tableIds)
            );
            tableMap = tables.stream()
                .collect(java.util.stream.Collectors.toMap(
                    MetadataTable::getId,
                    t -> t
                ));
        }

        // 填充表信息并应用筛选
        List<QualityReport> filteredReports = new ArrayList<>();
        for (QualityReport report : allReports) {
            MetadataTable table = tableMap.get(report.getTableId());
            if (table != null) {
                // 设置表名和数据库名到report对象（用于convertToVO）
                report.setTableName(table.getTableName());
                report.setDatabase(table.getDatabaseName());

                // 应用数据库和表名筛选
                boolean matchDatabase = (database == null || database.trim().isEmpty())
                    || table.getDatabaseName() != null
                    && table.getDatabaseName().toLowerCase().contains(database.trim().toLowerCase());
                boolean matchTableName = (tableName == null || tableName.trim().isEmpty())
                    || table.getTableName() != null
                    && table.getTableName().toLowerCase().contains(tableName.trim().toLowerCase());

                if (matchDatabase && matchTableName) {
                    filteredReports.add(report);
                }
            }
        }

        // 手动分页
        int total = filteredReports.size();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<QualityReport> pageReports = new ArrayList<>();
        if (fromIndex < total) {
            pageReports = filteredReports.subList(fromIndex, toIndex);
        }

        // 转换为VO
        List<QualityReportVO> vos = new ArrayList<>();
        for (QualityReport report : pageReports) {
            vos.add(convertToVO(report));
        }

        // 构建返回结果
        IPage<QualityReportVO> voPage = new Page<>(pageNum, pageSize, total);
        voPage.setRecords(vos);

        log.info("全局报告列表查询成功[totalCount={}]", total);

        return voPage;
    }

    /**
     * 保存质量报告
     *
     * @param report 质量报告
     * @return 保存的报告ID
     */
    public Long saveReport(QualityReport report) {
        // 设置报告日期为当前日期
        if (report.getReportDate() == null) {
            report.setReportDate(LocalDate.now());
        }

        // 设置创建时间
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(LocalDateTime.now());
        }
        report.setUpdatedAt(LocalDateTime.now());

        qualityReportMapper.insert(report);

        log.info("质量报告保存成功[reportId={}, tableId={}, score={}]",
            report.getId(), report.getTableId(), report.getQualityScore());

        return report.getId();
    }

    /**
     * 转换为VO
     *
     * @param report 报告实体
     * @return 报告VO
     */
    private QualityReportVO convertToVO(QualityReport report) {
        QualityReportVO vo = new QualityReportVO();
        vo.setId(report.getId());
        vo.setTableId(report.getTableId());
        vo.setTableName(report.getTableName());
        vo.setDatabase(report.getDatabase());
        vo.setReportDate(report.getReportDate());
        vo.setQualityScore(report.getQualityScore());
        vo.setQualityLevel(report.getQualityLevel());
        // qualityStatus由qualityLevel决定，不需要单独设置
        vo.setTotalRules(report.getTotalRules());
        vo.setPassedRules(report.getPassedRules());
        vo.setFailedRules(report.getFailedRules());
        vo.setWarnedRules(report.getWarnedRules());
        vo.setPassRate(report.getPassRate());
        vo.setCheckTime(report.getCheckTime());
        vo.setCheckDuration(report.getCheckDuration());
        vo.setCheckType(report.getCheckType());
        vo.setTriggeredBy(report.getTriggeredBy());
        return vo;
    }

    /**
     * 构建空报告（表暂无检查记录时使用）
     *
     * @param tableId 表ID
     * @return 空报告VO
     */
    private QualityReportVO buildEmptyReport(Long tableId) {
        QualityReportVO vo = new QualityReportVO();
        vo.setTableId(tableId);
        vo.setQualityScore(100);
        vo.setQualityLevel("EXCELLENT");
        // qualityStatus由qualityLevel决定
        vo.setTotalRules(0);
        vo.setPassedRules(0);
        vo.setFailedRules(0);
        vo.setWarnedRules(0);
        vo.setPassRate(100);
        return vo;
    }
}
