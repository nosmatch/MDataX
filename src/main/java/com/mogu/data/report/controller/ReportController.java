package com.mogu.data.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 分页查询报表列表
     */
    @GetMapping("/page")
    public Result<Page<Report>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        return Result.success(reportService.pageReports(keyword, page, size, userId));
    }

    /**
     * 根据ID查询报表
     */
    @GetMapping("/{id}")
    public Result<Report> getById(@PathVariable Long id) {
        Report report = reportService.getById(id);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return Result.error("报表不存在");
        }
        // 调试日志：打印字段值
        System.out.println("=== Report Debug Info ===");
        System.out.println("ID: " + report.getId());
        System.out.println("Name: " + report.getName());
        System.out.println("ChartType: " + report.getChartType());
        System.out.println("XAxisField: " + report.getXAxisField());
        System.out.println("YAxisField: " + report.getYAxisField());
        System.out.println("========================");
        return Result.success(report);
    }

    /**
     * 创建报表
     */
    @PostMapping
    public Result<Void> create(@RequestBody Report report) {
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
        return Result.success();
    }

    /**
     * 更新报表
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Report report) {
        System.out.println("=== Update Report Debug Info ===");
        System.out.println("ID: " + id);
        System.out.println("Name: " + report.getName());
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
        QueryResultVO result = reportService.executeReport(id, userId);
        return Result.success(result);
    }

}
