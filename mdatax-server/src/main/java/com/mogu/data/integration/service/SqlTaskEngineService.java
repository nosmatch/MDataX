package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.metadata.service.MetadataTableAutoSyncService;
import com.mogu.data.query.service.ClickHouseQueryService;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 任务执行引擎
 *
 * <p>
 * 复用 {@link ClickHouseQueryService} 统一执行引擎，支持 SELECT 和 DML/DDL。
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskEngineService {

    private final ClickHouseQueryService clickHouseQueryService;
    private final SqlTaskMapper sqlTaskMapper;
    private final SqlTaskLogService sqlTaskLogService;
    private final MetadataTableAutoSyncService autoSyncService;

    /**
     * 执行 SQL 任务
     */
    public void execute(Long taskId) {
        execute(taskId, null);
    }

    public void execute(Long taskId, Long dsInstanceId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        SqlTaskLog taskLog = sqlTaskLogService.startLog(taskId, dsInstanceId);
        try {
            String sql = task.getSqlContent();
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL 内容为空");
            }

            // 任务模式：不限制返回条数，不限制超时（或放宽），允许写入
            ExecuteOptions options = ExecuteOptions.builder()
                    .readonly(false)
                    .maxRows(0)
                    .timeoutSeconds(30)
                    .build();

            QueryResultVO result = clickHouseQueryService.execute(sql.trim(), options);

            // 执行成功后，自动同步元数据并设置责任人（仅对非 SELECT 语句）
            if (!isSelectStatement(sql)) {
                Set<String> tables = extractTableNames(sql);
                Long ownerId = task.getCreateUserId();
                for (String table : tables) {
                    int dot = table.indexOf('.');
                    if (dot > 0 && ownerId != null) {
                        autoSyncService.syncTableOwner(table.substring(0, dot), table.substring(dot + 1), ownerId);
                    }
                }
            }

            String message = String.format("执行成功，影响/返回 %d 行，耗时 %d ms",
                    result.getRowCount(), result.getExecutionTime());
            sqlTaskLogService.finishLog(taskLog.getId(), "SUCCESS", message);
            log.info("SQL 任务执行成功: taskId={}, 行数={}, 耗时={}ms",
                    taskId, result.getRowCount(), result.getExecutionTime());
        } catch (Exception e) {
            log.error("SQL 任务执行失败: taskId={}", taskId, e);
            sqlTaskLogService.finishLog(taskLog.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("SQL 任务执行失败: " + e.getMessage(), e);
        }
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
     * 从 SQL 中提取写操作涉及的表名（DDL / DML）
     */
    private Set<String> extractTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        String cleaned = removeCommentsAndStrings(sql);

        collectTableNames(cleaned, "\\b(?:CREATE|DROP|ALTER|TRUNCATE)\\s+(?:TABLE|VIEW|DICTIONARY)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bINSERT\\s+INTO\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bUPDATE\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);
        collectTableNames(cleaned, "\\bDELETE\\s+FROM\\s+`?([a-zA-Z0-9_]+)`?(?:\\.`?([a-zA-Z0-9_]+)`?)?", tables);

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
