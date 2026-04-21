package com.mogu.data.query.service;

import com.mogu.data.common.BusinessException;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.system.service.PermissionService;
import com.mogu.data.metadata.service.UserTableVisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 查询服务（即席查询）
 *
 * <p>
 * 负责 SQL 查询的业务逻辑：SELECT 校验、表名解析、读权限校验、访问记录。
 * 实际执行委托给 {@link ClickHouseQueryService}。
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private final ClickHouseQueryService clickHouseQueryService;
    private final PermissionService permissionService;
    private final UserTableVisitService userTableVisitService;

    /**
     * 执行 SQL 查询（仅 SELECT，带权限校验、LIMIT 限制、超时控制）
     */
    public QueryResultVO execute(String sql, Long userId) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new BusinessException("SQL 不能为空");
        }

        String trimmed = sql.trim();

        // 1. 只允许 SELECT
        if (!isSelectStatement(trimmed)) {
            throw new BusinessException("只允许执行 SELECT 查询语句");
        }

        // 2. 解析涉及的表名
        Set<String> tables = extractTableNames(trimmed);
        if (tables.isEmpty()) {
            throw new BusinessException("无法解析 SQL 中涉及的表名");
        }

        // 3. 权限校验：所有涉及的表都必须有读权限
        for (String table : tables) {
            if (!permissionService.hasReadPermission(userId, table)) {
                throw new BusinessException("无权限查询表: " + table);
            }
        }

        // 4. 记录表访问
        for (String table : tables) {
            int dot = table.indexOf('.');
            if (dot > 0) {
                userTableVisitService.recordVisit(userId, table.substring(0, dot), table.substring(dot + 1));
            }
        }

        // 5. 通过统一引擎执行（只读、限制 100 条、超时 5 秒）
        ExecuteOptions options = ExecuteOptions.builder()
                .readonly(true)
                .maxRows(100)
                .timeoutSeconds(5)
                .build();

        return clickHouseQueryService.execute(trimmed, options);
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
     * 移除 SQL 注释和字符串常量，防止关键字出现在字符串中导致误判
     */
    private String removeCommentsAndStrings(String sql) {
        String s = sql.replaceAll("--[^\\n]*", " ");
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("'[^']*'", " ");
        return s;
    }

    /**
     * 从 SQL 中提取涉及的表名（database.table 格式）
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

}
