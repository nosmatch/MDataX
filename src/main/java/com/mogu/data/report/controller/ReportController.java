package com.mogu.data.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.entity.ReportChart;
import com.mogu.data.report.service.ReportChartService;
import com.mogu.data.report.service.ReportPermissionService;
import com.mogu.data.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportChartService reportChartService;
    private final ReportPermissionService permissionService;

    /**
     * 分页查询报表列表
     */
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 先查询报表列表
        Page<Map<String, Object>> resultPage = reportService.pageReportsWithChartInfo(keyword, page, size, userId);

        // 为每个报表添加图表统计信息和权限信息
        for (Map<String, Object> report : resultPage.getRecords()) {
            Long reportId = (Long) report.get("id");
            List<ReportChart> charts = reportChartService.getChartsByReportId(reportId);
            report.put("chartCount", charts.size());

            // 收集图表类型
            Set<String> chartTypes = charts.stream()
                    .map(ReportChart::getChartType)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            report.put("chartTypes", new ArrayList<>(chartTypes));

            // 添加用户权限信息
            boolean hasViewPermission = permissionService.canViewReport(reportId, userId);
            boolean canEdit = permissionService.canEditReport(reportId, userId);
            boolean canDelete = permissionService.canDeleteReport(reportId, userId);
            report.put("hasViewPermission", hasViewPermission);
            report.put("canEdit", canEdit);
            report.put("canDelete", canDelete);
        }

        return Result.success(resultPage);
    }

    /**
     * 根据ID查询报表
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireViewPermission(id, userId);

        Report report = reportService.getById(id);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return Result.error("报表不存在");
        }

        // 获取用户角色信息
        String userRole = permissionService.getReportRole(id, userId);
        boolean canEdit = permissionService.canEditReport(id, userId);
        boolean canDelete = permissionService.canDeleteReport(id, userId);

        // 构建返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("id", report.getId());
        result.put("name", report.getName());
        result.put("description", report.getDescription());
        result.put("sqlContent", report.getSqlContent());
        result.put("visibility", report.getVisibility());
        result.put("ownerId", report.getOwnerId());
        result.put("status", report.getStatus());
        result.put("createTime", report.getCreateTime());
        result.put("updateTime", report.getUpdateTime());
        result.put("userRole", userRole);
        result.put("canEdit", canEdit);
        result.put("canDelete", canDelete);

        // 调试日志：打印字段值
        System.out.println("=== Report Debug Info ===");
        System.out.println("ID: " + report.getId());
        System.out.println("Name: " + report.getName());
        System.out.println("OwnerID: " + report.getOwnerId());
        System.out.println("Visibility: " + report.getVisibility());
        System.out.println("CurrentUser: " + userId);
        System.out.println("UserRole: " + userRole);
        System.out.println("CanEdit: " + canEdit);
        System.out.println("CanDelete: " + canDelete);
        System.out.println("========================");
        return Result.success(result);
    }

    /**
     * 创建报表
     */
    @PostMapping
    public Result<Report> create(@RequestBody Report report) {
        System.out.println("=== Create Report Debug Info ===");
        System.out.println("Name: " + report.getName());
        System.out.println("ChartType: " + report.getChartType());
        System.out.println("XAxisField: " + report.getXAxisField());
        System.out.println("YAxisField: " + report.getYAxisField());
        System.out.println("SqlContent: " + report.getSqlContent());
        System.out.println("==================================");

        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        reportService.createReport(report, userId);
        return Result.success(report);
    }

    /**
     * 更新报表
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Report report) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireEditPermission(id, userId);

        System.out.println("=== Update Report Debug Info ===");
        System.out.println("ID: " + id);
        System.out.println("Name: " + report.getName());
        System.out.println("OwnerID: " + report.getOwnerId());
        System.out.println("Visibility: " + report.getVisibility());
        System.out.println("ChartType: " + report.getChartType());
        System.out.println("XAxisField: " + report.getXAxisField());
        System.out.println("YAxisField: " + report.getYAxisField());
        System.out.println("SqlContent: " + report.getSqlContent());
        System.out.println("==================================");

        report.setId(id);
        reportService.updateReport(report);
        return Result.success();
    }

    /**
     * 删除报表
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireDeletePermission(id, userId);

        reportService.removeById(id);
        return Result.success();
    }

    /**
     * 执行报表 SQL 获取数据
     */
    @GetMapping("/{id}/data")
    public Result<QueryResultVO> execute(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireExecutePermission(id, userId);

        QueryResultVO result = reportService.executeReport(id, userId);
        return Result.success(result);
    }

    // ==================== 多图表相关接口 ====================

    /**
     * 查询报表的所有图表配置
     */
    @GetMapping("/{id}/charts")
    public Result<List<ReportChart>> getCharts(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireViewPermission(id, userId);

        List<ReportChart> charts = reportChartService.getChartsByReportId(id);
        return Result.success(charts);
    }

    /**
     * 创建图表配置
     */
    @PostMapping("/{id}/charts")
    public Result<Void> createChart(@PathVariable Long id, @RequestBody ReportChart chart) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireEditPermission(id, userId);

        chart.setReportId(id);
        reportChartService.createChart(chart);
        return Result.success();
    }

    /**
     * 更新图表配置
     */
    @PutMapping("/charts/{chartId}")
    public Result<Void> updateChart(@PathVariable Long chartId, @RequestBody ReportChart chart) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：需要获取报表ID
        ReportChart existingChart = reportChartService.getById(chartId);
        if (existingChart != null) {
            permissionService.requireEditPermission(existingChart.getReportId(), userId);
        }

        chart.setId(chartId);
        reportChartService.updateChart(chart);
        return Result.success();
    }

    /**
     * 删除图表配置
     */
    @DeleteMapping("/charts/{chartId}")
    public Result<Void> deleteChart(@PathVariable Long chartId) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：需要获取报表ID
        ReportChart existingChart = reportChartService.getById(chartId);
        if (existingChart != null) {
            permissionService.requireEditPermission(existingChart.getReportId(), userId);
        }

        reportChartService.deleteChart(chartId);
        return Result.success();
    }

    /**
     * 批量保存图表配置（用于报表编辑页保存）
     */
    @PutMapping("/{id}/charts/batch")
    public Result<Void> batchSaveCharts(@PathVariable Long id, @RequestBody List<ReportChart> charts) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireEditPermission(id, userId);

        reportChartService.batchSaveCharts(id, charts);
        return Result.success();
    }

    /**
     * 执行单个图表的SQL
     */
    @GetMapping("/charts/{chartId}/data")
    public Result<QueryResultVO> executeChart(@PathVariable Long chartId) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：需要获取报表ID
        ReportChart chart = reportChartService.getById(chartId);
        if (chart == null) {
            return Result.error("图表不存在");
        }
        permissionService.requireExecutePermission(chart.getReportId(), userId);

        // 检查是否有报表执行权限（用于决定是否跳过表权限检查）
        boolean hasReportPermission = permissionService.canExecuteReport(chart.getReportId(), userId);

        QueryResultVO result = reportChartService.executeChart(chartId, userId, hasReportPermission);
        return Result.success(result);
    }

    /**
     * 批量执行报表的所有图表SQL
     */
    @GetMapping("/{id}/charts-data")
    public Result<Map<Long, QueryResultVO>> executeAllCharts(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验
        permissionService.requireExecutePermission(id, userId);

        // 检查是否有报表执行权限（用于决定是否跳过表权限检查）
        boolean hasReportPermission = permissionService.canExecuteReport(id, userId);

        Map<Long, QueryResultVO> results = reportChartService.executeAllCharts(id, userId, hasReportPermission);
        return Result.success(results);
    }

}
