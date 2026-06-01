package com.mogu.data.query.service;

import com.mogu.data.common.BusinessException;
import com.mogu.data.common.LoginUser;
import com.mogu.data.metadata.service.MetadataTableAutoSyncService;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.system.service.PermissionService;
import com.mogu.data.metadata.service.TableAccessHistoryService;
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
    private final TableAccessHistoryService tableAccessHistoryService;
    private final MetadataTableAutoSyncService autoSyncService;

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
        String username = LoginUser.currentUsername();
        for (String table : tables) {
            int dot = table.indexOf('.');
            if (dot > 0) {
                String dbName = table.substring(0, dot);
                String tblName = table.substring(dot + 1);
                userTableVisitService.recordVisit(userId, dbName, tblName);
                tableAccessHistoryService.recordRead(userId, username, dbName, tblName, null);
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
     * 执行 SQL 开发语句（支持 SELECT / DDL / DML，带权限校验）
     */
    public QueryResultVO executeDev(String sql, Long userId) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new BusinessException("SQL 不能为空");
        }

        String trimmed = sql.trim();
        boolean isSelect = isSelectStatement(trimmed);

        // 解析 SQL 中涉及的读表和写表
        Set<String> readTables = extractReadTableNames(trimmed);
        Set<String> writeTables = extractWriteTableNames(trimmed);

        if (isSelect) {
            // 纯 SELECT：读权限校验 + 访问记录
            if (readTables.isEmpty()) {
                throw new BusinessException("无法解析 SQL 中涉及的表名");
            }
            for (String table : readTables) {
                if (!permissionService.hasReadPermission(userId, table)) {
                    throw new BusinessException("无权限查询表: " + table);
                }
            }
            String username = LoginUser.currentUsername();
            for (String table : readTables) {
                int dot = table.indexOf('.');
                if (dot > 0) {
                    String dbName = table.substring(0, dot);
                    String tblName = table.substring(dot + 1);
                    userTableVisitService.recordVisit(userId, dbName, tblName);
                    tableAccessHistoryService.recordRead(userId, username, dbName, tblName, null);
                }
            }
        } else {
            // DDL / DML：读权限校验（来源表）+ 写权限校验（目标表）
            for (String table : readTables) {
                if (!permissionService.hasReadPermission(userId, table)) {
                    throw new BusinessException("无权限读取表: " + table);
                }
            }
            for (String table : writeTables) {
                if (!permissionService.hasWritePermission(userId, table)) {
                    throw new BusinessException("无权限操作表: " + table);
                }
            }
        }

        ExecuteOptions options = ExecuteOptions.builder()
                .readonly(false)
                .maxRows(100)
                .timeoutSeconds(5)
                .build();

        QueryResultVO result = clickHouseQueryService.execute(trimmed, options);

        // DDL / DML 执行成功后：
        // 1. 只同步写表的元数据并设置责任人（来源表不受影响）
        // 2. 只记录写表的访问历史
        if (!isSelect) {
            String username = LoginUser.currentUsername();
            for (String table : writeTables) {
                int dot = table.indexOf('.');
                if (dot > 0) {
                    String dbName = table.substring(0, dot);
                    String tblName = table.substring(dot + 1);
                    autoSyncService.syncTableOwner(dbName, tblName, userId);
                    tableAccessHistoryService.recordWrite(userId, username, dbName, tblName, null);
                }
            }
        }

        return result;
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
                // 无数据库前缀时，默认使用 default
                tables.add("default." + dbOrTable);
            }
        }
        return tables;
    }

    /**
     * 从 SQL 中提取读操作涉及的表名（FROM / JOIN）
     */
    private Set<String> extractReadTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        String cleaned = removeCommentsAndStrings(sql);
        collectTableNames(cleaned, "\\b(?:FROM|JOIN)\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        return tables;
    }

    /**
     * 从 SQL 中提取写操作涉及的表名（INSERT / UPDATE / DELETE / CREATE / DROP / ALTER / TRUNCATE）
     */
    private Set<String> extractWriteTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        String cleaned = removeCommentsAndStrings(sql);

        collectTableNames(cleaned, "\\bINSERT\\s+INTO\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bUPDATE\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bDELETE\\s+FROM\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bCREATE\\s+(?:TABLE|VIEW|DICTIONARY)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bDROP\\s+(?:TABLE|VIEW|DICTIONARY)\\s+(?:IF\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bALTER\\s+TABLE\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bTRUNCATE\\s+(?:TABLE\\s+)?`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);

        return tables;
    }

    private void collectTableNames(String cleaned, String regex, Set<String> tables) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            String part1 = matcher.group(1);
            String part2 = matcher.group(2);
            if (part2 != null) {
                tables.add(part1 + "." + part2);
            } else if (part1 != null) {
                tables.add("default." + part1);
            }
        }
    }

}
