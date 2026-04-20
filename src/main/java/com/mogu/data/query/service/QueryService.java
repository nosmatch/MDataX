package com.mogu.data.query.service;

import com.mogu.data.common.BusinessException;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL查询服务
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private final PermissionService permissionService;

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行SQL查询（仅SELECT，带权限校验、LIMIT限制、超时控制）
     */
    public QueryResultVO execute(String sql, Long userId) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new BusinessException("SQL不能为空");
        }

        String trimmed = sql.trim();

        // 1. 只允许SELECT
        if (!isSelectStatement(trimmed)) {
            throw new BusinessException("只允许执行 SELECT 查询语句");
        }

        // 2. 解析涉及的表名
        Set<String> tables = extractTableNames(trimmed);
        if (tables.isEmpty()) {
            throw new BusinessException("无法解析SQL中涉及的表名");
        }

        // 3. 权限校验：所有涉及的表都必须有读权限
        for (String table : tables) {
            if (!permissionService.hasReadPermission(userId, table)) {
                throw new BusinessException("无权限查询表: " + table);
            }
        }

        // 4. 默认限制100条，并附加ClickHouse执行超时设置（5秒）
        String executableSql = addLimitIfNeeded(trimmed);
        if (!executableSql.toUpperCase().contains("SETTINGS")) {
            executableSql += " SETTINGS max_execution_time = 5";
        }

        // 5. 执行查询（通过JdbcTemplate设置Statement超时）
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(executableSql);
        long executionTime = System.currentTimeMillis() - start;

        // 6. 封装结果
        QueryResultVO vo = new QueryResultVO();
        if (!rows.isEmpty()) {
            vo.setColumns(new ArrayList<>(rows.get(0).keySet()));
        } else {
            vo.setColumns(Collections.emptyList());
        }
        vo.setRows(rows);
        vo.setRowCount(rows.size());
        vo.setExecutionTime(executionTime);
        return vo;
    }

    /**
     * 校验是否为安全的SELECT语句
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
     * 移除SQL注释和字符串常量，防止关键字出现在字符串中导致误判
     */
    private String removeCommentsAndStrings(String sql) {
        String s = sql.replaceAll("--[^\\n]*", " ");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("'[^']*'", " ");
        return s;
    }

    /**
     * 从SQL中提取涉及的表名（database.table格式）
     */
    private Set<String> extractTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        String cleaned = removeCommentsAndStrings(sql);

        Pattern pattern = Pattern.compile(
                "\\b(FROM|JOIN)\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            String dbOrTable = matcher.group(2);
            String tableOnly = matcher.group(3);
            if (tableOnly != null) {
                tables.add(dbOrTable + "." + tableOnly);
            } else {
                // 无数据库前缀时，默认使用 default
                tables.add("default." + dbOrTable);
            }
        }
        return tables;
    }

    /**
     * 若SQL未包含LIMIT，则自动追加 LIMIT 100
     */
    private String addLimitIfNeeded(String sql) {
        String trimmed = sql.trim().replaceAll(";+$", "");
        String upper = trimmed.toUpperCase();
        int lastLimit = upper.lastIndexOf("LIMIT");
        if (lastLimit > 0) {
            String after = upper.substring(lastLimit + 5).trim();
            if (after.matches("^\\d+.*")) {
                return trimmed;
            }
        }
        return trimmed + " LIMIT 100";
    }

}
