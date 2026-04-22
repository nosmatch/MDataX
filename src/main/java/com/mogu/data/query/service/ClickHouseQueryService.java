package com.mogu.data.query.service;

import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse 统一 SQL 执行引擎
 * <p>
 * 供 SQL 查询（即席）和 SQL 开发（任务）共用，通过 {@link ExecuteOptions} 区分执行策略。
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseQueryService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行 SQL 并返回结果
     *
     * @param sql     SQL 语句
     * @param options 执行选项
     * @return 查询结果
     */
    public QueryResultVO execute(String sql, ExecuteOptions options) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        String trimmed = sql.trim();
        boolean isSelect = isSelectStatement(trimmed);

        // 只读模式下禁止非 SELECT
        if (options.isReadonly() && !isSelect) {
            throw new IllegalArgumentException("当前模式仅允许执行 SELECT 查询语句");
        }

        // 构建可执行 SQL
        String executableSql = buildExecutableSql(trimmed, isSelect, options);

        long start = System.currentTimeMillis();
        QueryResultVO vo = new QueryResultVO();

        if (isSelect) {
            // SELECT：返回结果集
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(executableSql);
            long executionTime = System.currentTimeMillis() - start;

            if (!rows.isEmpty()) {
                vo.setColumns(new ArrayList<>(rows.get(0).keySet()));
            } else {
                vo.setColumns(Collections.emptyList());
            }
            vo.setRows(rows);
            vo.setRowCount(rows.size());
            vo.setExecutionTime(executionTime);
        } else {
            // DML/DDL：返回影响行数
            int affectedRows = jdbcTemplate.update(executableSql);
            long executionTime = System.currentTimeMillis() - start;

            vo.setColumns(Collections.singletonList("affected_rows"));
            vo.setRows(Collections.singletonList(
                    Collections.<String, Object>singletonMap("affected_rows", affectedRows)
            ));
            vo.setRowCount(affectedRows);
            vo.setExecutionTime(executionTime);
        }

        log.info("SQL 执行完成: 类型={}, 耗时={}ms, 行数={}",
                isSelect ? "SELECT" : "DML/DDL", vo.getExecutionTime(), vo.getRowCount());
        return vo;
    }

    /**
     * 构建可执行 SQL（追加 LIMIT 和超时设置）
     */
    private String buildExecutableSql(String sql, boolean isSelect, ExecuteOptions options) {
        String result = sql.replaceAll(";+$", "");

        // SELECT 追加 LIMIT
        if (isSelect && options.getMaxRows() > 0) {
            result = addLimitIfNeeded(result, options.getMaxRows());
        }

        // SELECT 追加超时设置（DDL/DML 追加 SETTINGS 可能导致语法错误）
        if (isSelect && options.getTimeoutSeconds() > 0
                && !result.toUpperCase().contains("SETTINGS")) {
            result += " SETTINGS max_execution_time = " + options.getTimeoutSeconds();
        }

        return result;
    }

    /**
     * 判断是否为 SELECT 语句
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
     * 移除 SQL 注释和字符串常量
     */
    private String removeCommentsAndStrings(String sql) {
        String s = sql.replaceAll("--[^\\n]*", " ");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("'[^']*'", " ");
        return s;
    }

    /**
     * 若 SQL 未包含 LIMIT，则自动追加 LIMIT N
     */
    private String addLimitIfNeeded(String sql, int maxRows) {
        String upper = sql.toUpperCase();
        int lastLimit = upper.lastIndexOf("LIMIT");
        if (lastLimit > 0) {
            String after = upper.substring(lastLimit + 5).trim();
            if (after.matches("^\\d+.*")) {
                return sql;
            }
        }
        return sql + " LIMIT " + maxRows;
    }

}
