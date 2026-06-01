package com.mogu.data.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.enums.DatasourceType;
import com.mogu.data.integration.mapper.DatasourceMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 异构数据同步引擎（多数据源 → ClickHouse）
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 执行同步任务
     *
     * @param taskId       同步任务ID
     * @param dsInstanceId DolphinScheduler 流程实例ID（DS 触发时传入，手动执行传 null）
     */
    public void execute(Long taskId, Long dsInstanceId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        Datasource ds = datasourceMapper.selectById(task.getDatasourceId());
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            throw new IllegalArgumentException("数据源不存在");
        }

        SyncTaskLog taskLog = syncTaskLogService.startLog(taskId, dsInstanceId);
        long rowCount = 0;
        try {
            DatasourceType type = DatasourceType.of(ds.getType());
            if (type == null) {
                throw new IllegalArgumentException("未知的数据源类型: " + ds.getType());
            }

            // 1. 获取源表结构
            List<ColumnInfo> columns = fetchColumns(ds, task.getSourceTable(), type);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("来源表不存在或无字段: " + task.getSourceTable());
            }

            // 2. 自动创建/更新ClickHouse目标表
            createOrUpdateClickHouseTable(task.getTargetTable(), columns);

            // 3. 按类型执行同步
            if ("FULL".equalsIgnoreCase(task.getSyncType())) {
                rowCount = doFullSync(ds, task, columns, type);
            } else if ("INCREMENTAL".equalsIgnoreCase(task.getSyncType())) {
                rowCount = doIncrementalSync(ds, task, columns, type);
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

    /* ========== 获取表结构 ========== */

    private List<ColumnInfo> fetchColumns(Datasource ds, String tableName, DatasourceType type) throws Exception {
        switch (type) {
            case MYSQL:
                return fetchMySQLColumns(ds, tableName);
            case CLICKHOUSE:
                return fetchClickHouseColumns(ds, tableName);
            case ELASTICSEARCH:
                return fetchEsColumns(ds, tableName);
            case KAFKA:
                return fetchKafkaColumns(ds, tableName);
            case LOCAL_EXCEL:
                return fetchExcelColumns(ds, tableName);
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
    }

    private List<ColumnInfo> fetchMySQLColumns(Datasource ds, String tableName) throws SQLException {
        String mysqlUrl = buildMySQLUrl(ds);
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(mysqlUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, ds.getDatabaseName(), tableName, null)) {
                while (rs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = rs.getString("COLUMN_NAME");
                    col.sourceType = rs.getString("TYPE_NAME");
                    col.size = rs.getInt("COLUMN_SIZE");
                    col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    private List<ColumnInfo> fetchClickHouseColumns(Datasource ds, String tableName) throws Exception {
        String url = String.format("jdbc:clickhouse://%s:%d/%s", ds.getHost(), ds.getPort(), ds.getDatabaseName());
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, ds.getDatabaseName(), tableName, null)) {
                while (rs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = rs.getString("COLUMN_NAME");
                    col.sourceType = rs.getString("TYPE_NAME");
                    col.size = rs.getInt("COLUMN_SIZE");
                    col.decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    col.nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    private List<ColumnInfo> fetchEsColumns(Datasource ds, String indexName) throws Exception {
        String body = doEsRequest(ds, "/" + indexName + "/_mapping");
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode mappings = root.path(indexName).path("mappings");
        JsonNode properties = mappings.path("properties");
        if (!properties.isObject()) {
            // 兼容旧版 ES 无 properties 或动态 mapping 的情况
            properties = mappings.path("dynamic").isMissingNode() ? null : mappings.path("properties");
        }
        if (properties == null || !properties.isObject()) {
            throw new IllegalArgumentException("无法获取ES索引字段映射: " + indexName);
        }
        List<ColumnInfo> columns = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            ColumnInfo col = new ColumnInfo();
            col.name = entry.getKey();
            col.sourceType = entry.getValue().path("type").asText("text");
            columns.add(col);
        }
        return columns;
    }

    private List<ColumnInfo> fetchKafkaColumns(Datasource ds, String topic) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", ds.getHost() + ":" + ds.getPort());
        props.put("group.id", "mdatax-schema-discovery-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("max.poll.records", 1);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            if (records.isEmpty()) {
                throw new IllegalArgumentException("Kafka Topic 暂无数据，无法推断字段结构");
            }
            ConsumerRecord<String, String> record = records.iterator().next();
            JsonNode json = OBJECT_MAPPER.readTree(record.value());
            List<ColumnInfo> columns = new ArrayList<>();
            Iterator<String> fieldNames = json.fieldNames();
            while (fieldNames.hasNext()) {
                ColumnInfo col = new ColumnInfo();
                col.name = fieldNames.next();
                col.sourceType = "text";
                columns.add(col);
            }
            return columns;
        }
    }

    private List<ColumnInfo> fetchExcelColumns(Datasource ds, String sheetName) {
        String filePath = ds.getExtraConfig();
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("Excel数据源未配置文件路径");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Excel文件不存在: " + filePath);
        }
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel工作表不存在: " + sheetName);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel工作表无表头行");
            }
            List<ColumnInfo> columns = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                ColumnInfo col = new ColumnInfo();
                col.name = headerRow.getCell(i) != null ? headerRow.getCell(i).getStringCellValue() : "col_" + i;
                col.sourceType = "String";
                columns.add(col);
            }
            return columns;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取Excel文件失败: " + e.getMessage(), e);
        }
    }

    /* ========== 全量同步 ========== */

    private long doFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns, DatasourceType type) throws Exception {
        clickHouseJdbcTemplate.execute("TRUNCATE TABLE IF EXISTS " + task.getTargetTable());
        log.info("全量同步 - 已清空目标表: {}", task.getTargetTable());

        switch (type) {
            case MYSQL:
                return doMySQLFullSync(ds, task, columns);
            case CLICKHOUSE:
                return doClickHouseFullSync(ds, task, columns);
            case ELASTICSEARCH:
                return doEsFullSync(ds, task, columns);
            case KAFKA:
                return doKafkaFullSync(ds, task, columns);
            case LOCAL_EXCEL:
                return doExcelFullSync(ds, task, columns);
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
    }

    private long doMySQLFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws SQLException {
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

    private long doClickHouseFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws Exception {
        String url = String.format("jdbc:clickhouse://%s:%d/%s", ds.getHost(), ds.getPort(), ds.getDatabaseName());
        String selectSql = "SELECT * FROM `" + task.getSourceTable() + "`";
        long totalRows = 0;
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(BATCH_SIZE);
                try (ResultSet rs = stmt.executeQuery(selectSql)) {
                    totalRows = syncResultSet(rs, columns, task.getTargetTable());
                }
            }
        }
        return totalRows;
    }

    private long doEsFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws Exception {
        String body = doEsRequest(ds, "/" + task.getSourceTable() + "/_search?size=10000");
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) {
            return 0;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            Map<String, Object> row = new LinkedHashMap<>();
            for (ColumnInfo col : columns) {
                row.put(col.name, convertEsValue(source.path(col.name)));
            }
            rows.add(row);
        }
        return syncMapRows(rows, columns, task.getTargetTable());
    }

    private long doKafkaFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", ds.getHost() + ":" + ds.getPort());
        props.put("group.id", "mdatax-sync-" + task.getId() + "-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("max.poll.records", 1000);

        long totalRows = 0;
        List<Map<String, Object>> batch = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(task.getSourceTable()));
            boolean running = true;
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) {
                    running = false;
                }
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode json = OBJECT_MAPPER.readTree(record.value());
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (ColumnInfo col : columns) {
                        row.put(col.name, convertJsonValue(json.path(col.name)));
                    }
                    batch.add(row);
                    if (batch.size() >= BATCH_SIZE) {
                        totalRows += syncMapRows(batch, columns, task.getTargetTable());
                        batch.clear();
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            totalRows += syncMapRows(batch, columns, task.getTargetTable());
        }
        return totalRows;
    }

    private long doExcelFullSync(Datasource ds, SyncTask task, List<ColumnInfo> columns) {
        String filePath = ds.getExtraConfig();
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("Excel数据源未配置文件路径");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Excel文件不存在: " + filePath);
        }
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheet(task.getSourceTable());
            if (sheet == null) {
                throw new IllegalArgumentException("Excel工作表不存在: " + task.getSourceTable());
            }
            List<Map<String, Object>> batch = new ArrayList<>();
            long totalRows = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, Object> data = new LinkedHashMap<>();
                for (int j = 0; j < columns.size(); j++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(j);
                    data.put(columns.get(j).name, getCellValue(cell));
                }
                batch.add(data);
                if (batch.size() >= BATCH_SIZE) {
                    totalRows += syncMapRows(batch, columns, task.getTargetTable());
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                totalRows += syncMapRows(batch, columns, task.getTargetTable());
            }
            return totalRows;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取Excel文件失败: " + e.getMessage(), e);
        }
    }

    /* ========== 增量同步 ========== */

    private long doIncrementalSync(Datasource ds, SyncTask task, List<ColumnInfo> columns, DatasourceType type) throws Exception {
        LocalDateTime lastSyncTime = syncTaskLogService.getLastSuccessTime(task.getId());
        if (lastSyncTime == null) {
            log.info("增量同步 - 无历史成功记录，转为全量同步");
            return doFullSync(ds, task, columns, type);
        }

        switch (type) {
            case MYSQL:
                return doMySQLIncrementalSync(ds, task, columns, lastSyncTime);
            case CLICKHOUSE:
                return doClickHouseIncrementalSync(ds, task, columns, lastSyncTime);
            case ELASTICSEARCH:
            case KAFKA:
            case LOCAL_EXCEL:
                // 非关系型数据源暂不支持增量，回退为全量
                log.info("{} 暂不支持增量同步，转为全量同步", type.getLabel());
                return doFullSync(ds, task, columns, type);
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
    }

    private long doMySQLIncrementalSync(Datasource ds, SyncTask task, List<ColumnInfo> columns, LocalDateTime lastSyncTime) throws SQLException {
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

    private long doClickHouseIncrementalSync(Datasource ds, SyncTask task, List<ColumnInfo> columns, LocalDateTime lastSyncTime) throws Exception {
        String url = String.format("jdbc:clickhouse://%s:%d/%s", ds.getHost(), ds.getPort(), ds.getDatabaseName());
        String timeStr = lastSyncTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String selectSql = "SELECT * FROM `" + task.getSourceTable() + "` WHERE `" +
                task.getTimeField() + "` > '" + timeStr + "'";
        log.info("增量同步 - SQL: {}", selectSql);
        long totalRows = 0;
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setFetchSize(BATCH_SIZE);
                try (ResultSet rs = stmt.executeQuery(selectSql)) {
                    totalRows = syncResultSet(rs, columns, task.getTargetTable());
                }
            }
        }
        return totalRows;
    }

    /* ========== 写入 ClickHouse ========== */

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

    private long syncMapRows(List<Map<String, Object>> rows, List<ColumnInfo> columns, String targetTable) {
        if (rows.isEmpty()) return 0;
        String insertSql = buildInsertSql(targetTable, columns);
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object[] arr = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                arr[i] = row.get(columns.get(i).name);
            }
            batch.add(arr);
        }
        clickHouseJdbcTemplate.batchUpdate(insertSql, batch);
        return rows.size();
    }

    /* ========== ClickHouse 建表 ========== */

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

    private String mapToClickHouseType(ColumnInfo col) {
        String type = (col.sourceType != null ? col.sourceType : "").toUpperCase();
        // 数字类型
        if (type.contains("TINYINT")) return "Int8";
        if (type.contains("SMALLINT")) return "Int16";
        if (type.contains("MEDIUMINT") || type.contains("INT") || type.contains("INTEGER")) return "Int32";
        if (type.contains("BIGINT")) return "Int64";
        if (type.contains("FLOAT")) return "Float32";
        if (type.contains("DOUBLE") || type.contains("REAL")) return "Float64";
        if (type.contains("DECIMAL") || type.contains("NUMERIC")) {
            int p = col.size > 0 ? col.size : 18;
            int s = col.decimalDigits >= 0 ? col.decimalDigits : 2;
            return "Decimal(" + p + ", " + s + ")";
        }
        // 日期类型
        if (type.contains("DATE") && !type.contains("DATETIME") && !type.contains("TIMESTAMP")) return "Date";
        if (type.contains("DATETIME") || type.contains("TIMESTAMP")) return "DateTime";
        if (type.contains("TIME") || type.contains("YEAR")) return "String";
        // 布尔
        if (type.contains("BOOL") || type.contains("BIT") && col.size == 1) return "UInt8";
        // 二进制
        if (type.contains("BLOB") || type.contains("BINARY")) return "String";
        if (type.contains("JSON")) return "String";
        // ES 类型映射
        if (type.contains("KEYWORD")) return "String";
        if (type.contains("TEXT")) return "String";
        if (type.contains("LONG")) return "Int64";
        if (type.contains("SHORT")) return "Int16";
        if (type.contains("BYTE")) return "Int8";
        if (type.contains("HALF_FLOAT")) return "Float32";
        if (type.contains("SCALED_FLOAT")) return "Float64";
        if (type.contains("FLOAT")) return "Float32";
        if (type.contains("DOUBLE")) return "Float64";
        if (type.contains("BOOLEAN")) return "UInt8";
        if (type.contains("DATE")) return "DateTime";
        if (type.contains("GEO_POINT") || type.contains("GEO_SHAPE") || type.contains("COMPLETION")) return "String";
        // 默认
        return "String";
    }

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

    /* ========== 工具方法 ========== */

    private String buildMySQLUrl(Datasource ds) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=10000&socketTimeout=30000",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
    }

    private String doEsRequest(Datasource ds, String path) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        Request.Builder builder = new Request.Builder()
                .url("http://" + ds.getHost() + ":" + ds.getPort() + path);
        if (StringUtils.hasText(ds.getUsername()) && StringUtils.hasText(ds.getPassword())) {
            builder.header("Authorization", Credentials.basic(ds.getUsername(), ds.getPassword()));
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("ES请求失败: HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private Object convertEsValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean() ? 1 : 0;
        if (node.isArray() || node.isObject()) return node.toString();
        return node.asText();
    }

    private Object convertJsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean() ? 1 : 0;
        if (node.isArray() || node.isObject()) return node.toString();
        return node.asText();
    }

    private Object getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1 : 0;
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    /**
     * 列信息内部类
     */
    private static class ColumnInfo {
        String name;
        String sourceType;
        int size;
        int decimalDigits;
        boolean nullable;
    }
}
