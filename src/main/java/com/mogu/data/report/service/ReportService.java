package com.mogu.data.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.common.BusinessException;
import com.mogu.data.query.service.ClickHouseQueryService;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.entity.ReportChart;
import com.mogu.data.report.mapper.ReportMapper;
import com.mogu.data.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报表服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService extends ServiceImpl<ReportMapper, Report> {

    private final ClickHouseQueryService clickHouseQueryService;
    private final PermissionService permissionService;

    /**
     * 分页查询报表列表
     */
    public Page<Report> pageReports(String keyword, long page, long size, Long userId) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Report::getName, keyword);
        }
        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 分页查询报表列表（带图表统计信息）
     * 只返回用户有权限查看的报表：
     * 1. 用户拥有的报表
     * 2. 公开的报表
     * 3. 用户作为协作者的报表
     */
    public Page<Map<String, Object>> pageReportsWithChartInfo(String keyword, long page, long size, Long userId) {
        // 查询报表列表（带权限过滤）
        Page<Report> reportPage = pageReportsWithPermission(keyword, page, size, userId);

        // 查询每个报表的图表统计信息
        List<Map<String, Object>> records = reportPage.getRecords().stream()
                .map(report -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", report.getId());
                    map.put("name", report.getName());
                    map.put("description", report.getDescription());
                    map.put("status", report.getStatus());
                    map.put("visibility", report.getVisibility());
                    map.put("ownerId", report.getOwnerId());
                    map.put("createTime", report.getCreateTime());

                    // 注意：这里不能直接调用 reportChartService，会形成循环依赖
                    // 图表统计信息设置为默认值
                    map.put("chartCount", 0);
                    map.put("chartTypes", new ArrayList<>());

                    // 添加用户权限信息
                    map.put("canEdit", report.getOwnerId().equals(userId));
                    map.put("canDelete", report.getOwnerId().equals(userId));

                    return map;
                })
                .collect(Collectors.toList());

        // 转换为 Page<Map<String, Object>>
        Page<Map<String, Object>> resultPage = new Page<>(reportPage.getCurrent(), reportPage.getSize(), reportPage.getTotal());
        resultPage.setRecords(records);

        return resultPage;
    }

    /**
     * 分页查询报表列表（带权限过滤）
     */
    private Page<Report> pageReportsWithPermission(String keyword, long page, long size, Long userId) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Report::getName, keyword);
        }

        // 权限过滤：只返回用户有权限查看的报表
        // 1. 用户拥有的报表
        // 2. 公开的报表
        // 3. 用户作为协作者的报表
        wrapper.and(w -> w.eq(Report::getOwnerId, userId)
                .or()
                .eq(Report::getVisibility, "public")
                .or()
                .inSql(Report::getId,
                        "SELECT DISTINCT report_id FROM report_collaborator WHERE user_id = " + userId));

        wrapper.orderByDesc(Report::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 创建报表
     */
    public void createReport(Report report, Long userId) {
        if (!StringUtils.hasText(report.getName())) {
            throw new BusinessException("报表名称不能为空");
        }
        // 移除SQL内容验证，SQL现在在图表级别配置
        if (lambdaQuery().eq(Report::getName, report.getName()).eq(Report::getDeleted, 0).count() > 0) {
            throw new BusinessException("报表名称已存在");
        }
        report.setCreateUserId(userId);
        report.setOwnerId(userId); // 设置创建者为所有者
        report.setVisibility(report.getVisibility() != null ? report.getVisibility() : "private"); // 默认私有
        report.setStatus(report.getStatus() != null ? report.getStatus() : 1);

        log.info("保存报表 - Name: {}, Owner: {}, Visibility: {}",
                report.getName(), report.getOwnerId(), report.getVisibility());

        save(report);

        log.info("保存完成，生成的ID: {}", report.getId());
    }

    /**
     * 更新报表
     */
    public void updateReport(Report report) {
        if (report.getId() == null) {
            throw new BusinessException("报表ID不能为空");
        }
        Report existing = getById(report.getId());
        if (existing == null || existing.getDeleted() != null && existing.getDeleted() == 1) {
            throw new BusinessException("报表不存在");
        }
        if (StringUtils.hasText(report.getName()) && !report.getName().equals(existing.getName())) {
            if (lambdaQuery().eq(Report::getName, report.getName()).eq(Report::getDeleted, 0).count() > 0) {
                throw new BusinessException("报表名称已存在");
            }
        }

        log.info("更新报表 - ID: {}, Name: {}", report.getId(), report.getName());

        updateById(report);

        log.info("更新完成");
    }

    /**
     * 执行报表 SQL 并返回结果（带权限校验）
     */
    public QueryResultVO executeReport(Long reportId, Long userId) {
        Report report = getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            throw new BusinessException("报表不存在");
        }
        if (report.getStatus() != null && report.getStatus() == 0) {
            throw new BusinessException("报表已停用");
        }

        String sql = report.getSqlContent();
        if (!StringUtils.hasText(sql)) {
            throw new BusinessException("报表SQL为空");
        }

        // 1. 校验 SQL 安全性（仅允许 SELECT）
        if (!isSelectStatement(sql)) {
            throw new BusinessException("报表SQL只允许 SELECT 查询语句");
        }

        // 2. 解析涉及的表名并校验读权限
        Set<String> tables = extractTableNames(sql);
        for (String table : tables) {
            if (!permissionService.hasReadPermission(userId, table)) {
                throw new BusinessException("无权限查询表: " + table);
            }
        }

        // 3. 执行 SQL（限制 5000 条，超时 10 秒）
        ExecuteOptions options = ExecuteOptions.builder()
                .readonly(true)
                .maxRows(5000)
                .timeoutSeconds(10)
                .build();

        return clickHouseQueryService.execute(sql, options);
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

    private String removeCommentsAndStrings(String sql) {
        String s = sql.replaceAll("--[^\\n]*", " ");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("'[^']*'", " ");
        return s;
    }

    /**
     * 从 SQL 中提取涉及的表名
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
