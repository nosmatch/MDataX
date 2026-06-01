package com.mogu.data.metadata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataColumnMapper;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 元数据表自动同步服务
 * <p>
 * 当用户通过 SQL 创建/修改表后，自动将 ClickHouse 中的表信息同步到 MySQL 元数据表，
 * 并将执行者设为表的责任人。
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataTableAutoSyncService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;

    /**
     * 同步指定表的元数据，并设置责任人
     *
     * @param databaseName 数据库名
     * @param tableName    表名
     * @param ownerId      责任人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncTableOwner(String databaseName, String tableName, Long ownerId) {
        if (databaseName == null || tableName == null || ownerId == null) {
            return;
        }

        try {
            // 1. 从 ClickHouse 读取表信息
            List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(
                    "SELECT database, name, engine, total_rows, total_bytes, comment " +
                            "FROM system.tables " +
                            "WHERE database = ? AND name = ?",
                    databaseName, tableName
            );

            if (rows.isEmpty()) {
                log.warn("ClickHouse 中未找到表: {}.{}", databaseName, tableName);
                return;
            }

            Map<String, Object> row = rows.get(0);
            String engine = (String) row.get("engine");
            Long totalRows = extractLong(row.get("total_rows"));
            Long totalBytes = extractLong(row.get("total_bytes"));
            String comment = (String) row.get("comment");

            // 2. 查询本地是否已有记录
            List<MetadataTable> localTables = tableMapper.selectList(
                    new QueryWrapper<MetadataTable>()
                            .eq("database_name", databaseName)
                            .eq("table_name", tableName)
                            .eq("deleted", 0)
                            .last("LIMIT 1")
            );

            MetadataTable table;
            if (localTables == null || localTables.isEmpty()) {
                // 新增表
                table = new MetadataTable();
                table.setDatabaseName(databaseName);
                table.setTableName(tableName);
                table.setEngine(engine);
                table.setTotalRows(totalRows);
                table.setTotalBytes(totalBytes);
                table.setTableComment(comment);
                table.setOwnerId(ownerId);
                tableMapper.insert(table);
                log.info("自动同步新增表元数据: {}.{} owner={}", databaseName, tableName, ownerId);
            } else {
                // 更新表（ engine / rows / bytes / comment / owner_id 有变化才更新）
                table = localTables.get(0);
                boolean changed = !Objects.equals(table.getEngine(), engine)
                        || !Objects.equals(table.getTotalRows(), totalRows)
                        || !Objects.equals(table.getTotalBytes(), totalBytes)
                        || !Objects.equals(table.getTableComment(), comment)
                        || !Objects.equals(table.getOwnerId(), ownerId);

                if (changed) {
                    table.setEngine(engine);
                    table.setTotalRows(totalRows);
                    table.setTotalBytes(totalBytes);
                    table.setTableComment(comment);
                    table.setOwnerId(ownerId);
                    tableMapper.updateById(table);
                    log.info("自动同步更新表元数据: {}.{} owner={}", databaseName, tableName, ownerId);
                }
            }

            // 3. 同步字段信息
            syncColumns(table.getId(), databaseName, tableName);

        } catch (Exception e) {
            log.error("自动同步表元数据失败: {}.{}", databaseName, tableName, e);
        }
    }

    /**
     * 同步指定表的字段信息
     */
    private void syncColumns(Long tableId, String database, String tableName) {
        List<Map<String, Object>> chColumns = clickHouseJdbcTemplate.queryForList(
                "SELECT name, type, comment, default_expression, position " +
                        "FROM system.columns " +
                        "WHERE database = ? AND `table` = ? " +
                        "ORDER BY position",
                database, tableName
        );

        List<MetadataColumn> localColumns = columnMapper.selectList(
                new QueryWrapper<MetadataColumn>().eq("table_id", tableId)
        );
        Map<String, MetadataColumn> localColumnMap = localColumns.stream()
                .collect(Collectors.toMap(
                        MetadataColumn::getColumnName,
                        c -> c,
                        (existing, replacement) -> existing
                ));

        java.util.Set<String> chColumnNames = new java.util.HashSet<>();

        for (Map<String, Object> row : chColumns) {
            String colName = (String) row.get("name");
            chColumnNames.add(colName);

            String dataType = (String) row.get("type");
            String colComment = (String) row.get("comment");
            String defaultExpr = (String) row.get("default_expression");
            Integer position = extractInt(row.get("position"));

            MetadataColumn localCol = localColumnMap.get(colName);
            if (localCol == null) {
                MetadataColumn newCol = new MetadataColumn();
                newCol.setTableId(tableId);
                newCol.setColumnName(colName);
                newCol.setDataType(dataType);
                newCol.setColumnComment(colComment);
                newCol.setColumnDefault(defaultExpr);
                newCol.setOrdinalPosition(position);
                columnMapper.insert(newCol);
            } else {
                boolean changed = !Objects.equals(localCol.getDataType(), dataType)
                        || !Objects.equals(localCol.getColumnComment(), colComment)
                        || !Objects.equals(localCol.getColumnDefault(), defaultExpr)
                        || !Objects.equals(localCol.getOrdinalPosition(), position);

                if (changed) {
                    localCol.setDataType(dataType);
                    localCol.setColumnComment(colComment);
                    localCol.setColumnDefault(defaultExpr);
                    localCol.setOrdinalPosition(position);
                    columnMapper.updateById(localCol);
                }
            }
        }

        // 删除本地多余的字段
        for (MetadataColumn localCol : localColumns) {
            if (!chColumnNames.contains(localCol.getColumnName())) {
                columnMapper.deleteById(localCol.getId());
            }
        }
    }

    private Long extractLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
