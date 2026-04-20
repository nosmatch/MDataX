package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.mapper.DatasourceMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 异构数据同步引擎（MySQL → ClickHouse）
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncEngineService {

    private final JdbcTemplate clickHouseJdbcTemplate;
    private final DatasourceMapper datasourceMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncTaskLogService syncTaskLogService;

    private static final int BATCH_SIZE = 5000;

    /**
     * 执行同步任务
     */
    public void execute(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        Datasource ds = datasourceMapper.selectById(task.getDatasourceId());
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            throw new IllegalArgumentException("数据源不存在");
        }

        SyncTaskLog taskLog = syncTaskLogService.startLog(taskId);
        long rowCount = 0;
        try {
            // 1. 获取MySQL表结构
            List<ColumnInfo> columns = fetchMySQLColumns(ds, task.getSourceTable());
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("来源表不存在或无字段: " + task.getSourceTable());
            }

            // 2. 自动创建/更新ClickHouse表
            createOrUpdateClickHouseTable(task.getTargetTable(), columns);

            // 3. 执行同步
            if ("FULL".equalsIgnoreCase(task.getSyncType())) {
                rowCount = doFullSync(ds, task, columns);
            } else if ("INCREMENTAL".equalsIgnoreCase(task.getSyncType())) {
                rowCount = doIncrementalSync(ds, task, columns);
            } else {
                throw new IllegalArgumentException("不支持的同步类型: " + task.getSyncType());
            }

            // 4. 更新任务最后同步时间
            task.setLastSyncTime(LocalDateTime.now());
            syncTaskMapper.updateById(task);

            syncTaskLogService.finishLog(taskLog.getId(), "SUCCESS", "同步成功，共 " + rowCount + " 条", rowCount);
            log.info("同步任务完成: taskId={}, 行数={}", taskId, rowCount);
        } catch (Exception e) {
            log.error("同步任务失败: taskId={}", taskId, e);
            syncTaskLogService.finishLog(taskLog.getId(), "FAILED", e.getMessage(), rowCount);
            throw new RuntimeException("同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 全量同步
     */
    private long doFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws SQLException {
        // TRUNCATE目标表
        clickHouseJdbcTemplate.execute("TRUNCATE TABLE IF EXISTS " + task.getTargetTable());
        log.info("全量同步 - 已清空目标表: {}", task.getTargetTable());

        String mysqlUrl = buildMySQLUrl(ds);
        String selectSql = "SELECT * FROM `" + task.getSourceTable() + "`";

        long totalRows = 0;
        try (Connection conn = DriverManager.getConnection(mysqlUrl, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(BATCH_SIZE);
                try (ResultSet rs = stmt.executeQuery(selectSql)) {
                    totalRows = syncResultSet(rs, columns, task.getTargetTable());
                }
            }
        }
        return totalRows;
    }

    /**
     * 增量同步
     */
    private long doIncrementalSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws SQLException {
        LocalDateTime lastSyncTime = syncTaskLogService.getLastSuccessTime(task.getId());
        if (lastSyncTime == null) {
            log.info("增量同步 - 无历史成功记录，转为全量同步");
            return doFullSync(ds, task, columns);
        }

        String mysqlUrl = buildMySQLUrl(ds);
        String timeStr = lastSyncTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String selectSql = "SELECT * FROM `" + task.getSourceTable() + "` WHERE `" +
                task.getTimeField() + "` > '" + timeStr + "'";

        log.info("增量同步 - SQL: {}", selectSql);

        long totalRows = 0;
        try (Connection conn = DriverManager.getConnection(mysqlUrl, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(BATCH_SIZE);
                try (ResultSet rs = stmt.executeQuery(selectSql)) {
                    totalRows = syncResultSet(rs, columns, task.getTargetTable());
                }
            }
        }
        return totalRows;
    }

    /**
     * 将ResultSet数据批量同步到ClickHouse
     */
    private long syncResultSet(ResultSet rs, List<ColumnInfo> columns, String targetTable) throws SQLException {
        String insertSql = buildInsertSql(targetTable, columns);
        long totalRows = 0;
        List<Object[]> batch = new ArrayList<>();

        int colCount = columns.size();
        while (rs.next()) {
            Object[] row = new Object[colCount];
            for (int i = 0; i < colCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            batch.add(row);

            if (batch.size() >= BATCH_SIZE) {
                clickHouseJdbcTemplate.batchUpdate(insertSql, batch);
                totalRows += batch.size();
                log.debug("已同步 {} 条", totalRows);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            clickHouseJdbcTemplate.batchUpdate(insertSql, batch);
            totalRows += batch.size();
        }

        log.info("同步完成，目标表: {}, 总行数: {}", targetTable, totalRows);
        return totalRows;
    }

    /**
     * 获取MySQL表结构
     */
    private List<ColumnInfo> fetchMySQLColumns(Datasource ds, String tableName) throws SQLException {
        String mysqlUrl = buildMySQLUrl(ds);
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(mysqlUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, ds.getDatabaseName(), tableName, null)) {
                while (rs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = rs.getString("COLUMN_NAME");
                    col.mysqlType = rs.getString("TYPE_NAME");
                    col.size = rs.getInt("COLUMN_SIZE");
                    col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    /**
     * 创建或更新ClickHouse表
     */
    private void createOrUpdateClickHouseTable(String tableName, List<ColumnInfo> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            if (i > 0) sql.append(", ");
            sql.append("`").append(col.name).append("` ").append(mapToClickHouseType(col));
        }
        sql.append(") ENGINE = MergeTree() ORDER BY tuple()");

        log.info("创建ClickHouse表: {}", sql);
        clickHouseJdbcTemplate.execute(sql.toString());
    }

    /**
     * MySQL类型 → ClickHouse类型映射
     */
    private String mapToClickHouseType(ColumnInfo col) {
        String type = col.mysqlType.toUpperCase();
        if (type.contains("TINYINT")) {
            return "Int8";
        }
        if (type.contains("SMALLINT")) {
            return "Int16";
        }
        if (type.contains("MEDIUMINT") || type.contains("INT") || type.contains("INTEGER")) {
            return "Int32";
        }
        if (type.contains("BIGINT")) {
            return "Int64";
        }
        if (type.contains("FLOAT")) {
            return "Float32";
        }
        if (type.contains("DOUBLE") || type.contains("REAL")) {
            return "Float64";
        }
        if (type.contains("DECIMAL") || type.contains("NUMERIC")) {
            int p = col.size > 0 ? col.size : 18;
            int s = col.decimalDigits >= 0 ? col.decimalDigits : 2;
            return "Decimal(" + p + ", " + s + ")";
        }
        if (type.contains("DATE") && !type.contains("DATETIME") && !type.contains("TIMESTAMP")) {
            return "Date";
        }
        if (type.contains("DATETIME") || type.contains("TIMESTAMP")) {
            return "DateTime";
        }
        if (type.contains("TIME") || type.contains("YEAR")) {
            return "String";
        }
        if (type.contains("BOOL") || type.contains("BIT") && col.size == 1) {
            return "UInt8";
        }
        if (type.contains("BLOB") || type.contains("BINARY")) {
            return "String";
        }
        if (type.contains("JSON")) {
            return "String";
        }
        // VARCHAR, CHAR, TEXT, ENUM, SET 等默认映射为 String
        return "String";
    }

    /**
     * 构建INSERT SQL
     */
    private String buildInsertSql(String tableName, List<ColumnInfo> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("`").append(columns.get(i).name).append("`");
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");
        return sql.toString();
    }

    private String buildMySQLUrl(Datasource ds) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=30000",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
    }

    /**
     * 列信息内部类
     */
    private static class ColumnInfo {
        String name;
        String mysqlType;
        int size;
        int decimalDigits;
        boolean nullable;
    }
}
