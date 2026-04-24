package com.mogu.data.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.common.BusinessException;
import com.mogu.data.query.service.ClickHouseQueryService;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.report.entity.ReportChart;
import com.mogu.data.report.mapper.ReportChartMapper;
import com.mogu.data.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 报表图表配置服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportChartService extends ServiceImpl<ReportChartMapper, ReportChart> {

    private final ReportChartMapper reportChartMapper;
    private final ClickHouseQueryService clickHouseQueryService;
    private final PermissionService permissionService;

    /**
     * 查询报表的所有图表配置（按排序号排序）
     */
    public List<ReportChart> getChartsByReportId(Long reportId) {
        LambdaQueryWrapper<ReportChart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportChart::getReportId, reportId);
        wrapper.eq(ReportChart::getDeleted, 0);
        wrapper.orderByAsc(ReportChart::getSortOrder);
        return list(wrapper);
    }

    /**
     * 创建图表配置
     */
    public void createChart(ReportChart chart) {
        if (chart.getReportId() == null) {
            throw new BusinessException("报表ID不能为空");
        }
        if (!StringUtils.hasText(chart.getChartType())) {
            throw new BusinessException("图表类型不能为空");
        }

        // 设置默认值
        if (chart.getSortOrder() == null) {
            // 获取当前报表的最大排序号
            LambdaQueryWrapper<ReportChart> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportChart::getReportId, chart.getReportId());
            wrapper.eq(ReportChart::getDeleted, 0);
            wrapper.orderByDesc(ReportChart::getSortOrder);
            wrapper.last("LIMIT 1");
            ReportChart lastChart = getOne(wrapper);
            chart.setSortOrder(lastChart != null ? lastChart.getSortOrder() + 1 : 1);
        }
        if (chart.getLayoutSpan() == null) {
            chart.setLayoutSpan(12); // 默认全宽
        }

        save(chart);
        log.info("创建图表配置 - ReportId: {}, ChartId: {}, Type: {}",
                chart.getReportId(), chart.getId(), chart.getChartType());
    }

    /**
     * 更新图表配置
     */
    public void updateChart(ReportChart chart) {
        if (chart.getId() == null) {
            throw new BusinessException("图表ID不能为空");
        }
        ReportChart existing = getById(chart.getId());
        if (existing == null || existing.getDeleted() != null && existing.getDeleted() == 1) {
            throw new BusinessException("图表配置不存在");
        }

        updateById(chart);
        log.info("更新图表配置 - ChartId: {}, Type: {}", chart.getId(), chart.getChartType());
    }

    /**
     * 删除图表配置
     */
    public void deleteChart(Long chartId) {
        ReportChart chart = getById(chartId);
        if (chart == null) {
            throw new BusinessException("图表配置不存在");
        }
        removeById(chartId);
        log.info("删除图表配置 - ChartId: {}", chartId);
    }

    /**
     * 批量保存图表配置
     */
    public void batchSaveCharts(Long reportId, List<ReportChart> charts) {
        if (reportId == null) {
            throw new BusinessException("报表ID不能为空");
        }
        if (charts == null || charts.isEmpty()) {
            throw new BusinessException("图表配置不能为空");
        }

        // 验证每个图表的必要字段
        for (int i = 0; i < charts.size(); i++) {
            ReportChart chart = charts.get(i);
            if (!StringUtils.hasText(chart.getTitle())) {
                throw new BusinessException("第" + (i + 1) + "个图表的标题不能为空");
            }
            if (!StringUtils.hasText(chart.getSqlContent())) {
                throw new BusinessException("第" + (i + 1) + "个图表的SQL内容不能为空");
            }
            if (!StringUtils.hasText(chart.getChartType())) {
                throw new BusinessException("第" + (i + 1) + "个图表的类型不能为空");
            }
        }

        // 删除原有的图表配置
        LambdaQueryWrapper<ReportChart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportChart::getReportId, reportId);
        remove(wrapper);

        // 批量插入新的图表配置
        for (int i = 0; i < charts.size(); i++) {
            ReportChart chart = charts.get(i);
            chart.setReportId(reportId);
            chart.setSortOrder(i + 1);
            if (chart.getLayoutSpan() == null) {
                chart.setLayoutSpan(12);
            }
            // 清除ID，让数据库自动生成
            chart.setId(null);
        }
        saveBatch(charts);
        log.info("批量保存图表配置 - ReportId: {}, Count: {}", reportId, charts.size());
    }

    /**
     * 从旧版单图表报表创建图表配置（用于兼容）
     */
    public ReportChart createFromLegacyReport(Long reportId, String chartType, String xAxisField, String yAxisField) {
        ReportChart chart = new ReportChart();
        chart.setReportId(reportId);
        chart.setChartType(chartType);
        chart.setXAxisField(xAxisField);
        chart.setYAxisField(yAxisField);
        chart.setTitle("默认图表");
        chart.setSortOrder(1);
        chart.setLayoutSpan(12);
        save(chart);
        return chart;
    }

    /**
     * 执行单个图表的SQL
     */
    public QueryResultVO executeChart(Long chartId, Long userId) {
        ReportChart chart = getById(chartId);
        if (chart == null || chart.getDeleted() != null && chart.getDeleted() == 1) {
            throw new BusinessException("图表不存在");
        }

        String sql = chart.getSqlContent();
        if (!StringUtils.hasText(sql)) {
            throw new BusinessException("图表SQL为空，请先配置SQL语句");
        }

        // 1. 校验SQL安全性（仅允许 SELECT）
        if (!isSelectStatement(sql)) {
            throw new BusinessException("图表SQL只允许 SELECT 查询语句");
        }

        // 2. 解析涉及的表名并校验读权限
        Set<String> tables = extractTableNames(sql);
        for (String table : tables) {
            if (!permissionService.hasReadPermission(userId, table)) {
                throw new BusinessException("无权限查询表: " + table);
            }
        }

        // 3. 执行SQL（限制5000条，超时10秒）
        ExecuteOptions options = ExecuteOptions.builder()
                .readonly(true)
                .maxRows(5000)
                .timeoutSeconds(10)
                .build();

        log.info("执行图表SQL - ChartId: {}, Title: {}", chartId, chart.getTitle());
        return clickHouseQueryService.execute(sql, options);
    }

    /**
     * 并行执行报表的所有图表SQL
     */
    public Map<Long, QueryResultVO> executeAllCharts(Long reportId, Long userId) {
        List<ReportChart> charts = getChartsByReportId(reportId);

        // 过滤掉没有SQL的图表
        List<ReportChart> validCharts = charts.stream()
                .filter(c -> StringUtils.hasText(c.getSqlContent()))
                .collect(Collectors.toList());

        if (validCharts.isEmpty()) {
            throw new BusinessException("报表没有可执行的图表（所有图表都缺少SQL配置）");
        }

        Map<Long, QueryResultVO> results = new ConcurrentHashMap<>();
        Map<Long, String> errors = new ConcurrentHashMap<>();

        // 并行执行所有图表SQL
        validCharts.parallelStream().forEach(chart -> {
            try {
                QueryResultVO result = executeChart(chart.getId(), userId);
                results.put(chart.getId(), result);
                log.info("图表执行成功 - ChartId: {}, Title: {}, Rows: {}",
                        chart.getId(), chart.getTitle(), result.getRowCount());
            } catch (Exception e) {
                log.error("图表执行失败 - ChartId: {}, Title: {}, Error: {}",
                        chart.getId(), chart.getTitle(), e.getMessage());
                errors.put(chart.getId(), e.getMessage());
            }
        });

        // 如果有失败的图表，记录日志但不抛异常（允许部分图表失败）
        if (!errors.isEmpty()) {
            log.warn("部分图表执行失败 - ReportId: {}, Failed: {}/{}",
                    reportId, errors.size(), validCharts.size());
        }

        return results;
    }

    /**
     * 校验是否为安全的 SELECT 语句
     */
    private boolean isSelectStatement(String sql) {
        String cleaned = removeCommentsAndStrings(sql).toUpperCase().trim();
        if (!cleaned.startsWith("SELECT")) {
            return false;
        }
        String[] forbidden = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT"};
        for (String keyword : forbidden) {
            if (cleaned.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 移除SQL中的注释和字符串
     */
    private String removeCommentsAndStrings(String sql) {
        String s = sql.replaceAll("--[^\\n]*", " ");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("'[^']*'", " ");
        return s;
    }

    /**
     * 从SQL中提取涉及的表名
     */
    private Set<String> extractTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        String cleaned = removeCommentsAndStrings(sql);
        Pattern pattern = Pattern.compile(
                "\\b(?:FROM|JOIN)\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            String dbOrTable = matcher.group(1);
            String tableOnly = matcher.group(2);
            if (tableOnly != null) {
                tables.add(dbOrTable + "." + tableOnly);
            } else {
                tables.add("default." + dbOrTable);
            }
        }
        return tables;
    }

}
