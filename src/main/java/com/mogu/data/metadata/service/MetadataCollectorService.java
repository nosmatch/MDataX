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

import java.util.*;
import java.util.stream.Collectors;

/**
 * ClickHouse 元数据采集服务
 * 定时扫描 ClickHouse 系统表，同步表和字段元数据到 MySQL
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCollectorService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;

    /**
     * 执行全量元数据采集与增量同步
     */
    @Transactional(rollbackFor = Exception.class)
    public void collect() {
        log.info("开始 ClickHouse 元数据采集...");

        // 1. 从 ClickHouse 读取所有表（排除系统库）
        List<Map<String, Object>> chTables = clickHouseJdbcTemplate.queryForList(
                "SELECT database, name, engine, total_rows, total_bytes, comment " +
                        "FROM system.tables " +
                        "WHERE database NOT IN ('system', 'information_schema', 'INFORMATION_SCHEMA')"
        );

        // 1.5 从 system.parts 获取每张表的数据最近更新时间
        List<Map<String, Object>> chParts = clickHouseJdbcTemplate.queryForList(
                "SELECT database, table, max(modification_time) as last_update " +
                        "FROM system.parts " +
                        "WHERE database NOT IN ('system', 'information_schema', 'INFORMATION_SCHEMA') AND active = 1 " +
                        "GROUP BY database, table"
        );
        Map<String, java.time.LocalDateTime> lastUpdateMap = chParts.stream()
                .collect(Collectors.toMap(
                        r -> r.get("database") + "." + r.get("table"),
                        r -> {
                            Object val = r.get("last_update");
                            if (val instanceof java.sql.Timestamp) {
                                return ((java.sql.Timestamp) val).toLocalDateTime();
                            }
                            if (val instanceof java.time.LocalDateTime) {
                                return (java.time.LocalDateTime) val;
                            }
                            return null;
                        },
                        (existing, replacement) -> existing
                ));

        // 2. 读取本地所有未删除的表
        List<MetadataTable> localTables = tableMapper.selectList(
                new QueryWrapper<MetadataTable>().eq("deleted", 0)
        );
        Map<String, MetadataTable> localTableMap = localTables.stream()
                .collect(Collectors.toMap(
                        t -> t.getDatabaseName() + "." + t.getTableName(),
                        t -> t,
                        (existing, replacement) -> existing
                ));

        Set<String> chTableKeys = new HashSet<>();

        // 3. 处理 ClickHouse 中的表：新增或更新
        for (Map<String, Object> row : chTables) {
            String db = (String) row.get("database");
            String name = (String) row.get("name");
            String key = db + "." + name;
            chTableKeys.add(key);

            String engine = (String) row.get("engine");
            Long totalRows = extractLong(row.get("total_rows"));
            Long totalBytes = extractLong(row.get("total_bytes"));
            String comment = (String) row.get("comment");

            MetadataTable localTable = localTableMap.get(key);
            if (localTable == null) {
                // 新增表
                MetadataTable newTable = new MetadataTable();
                newTable.setDatabaseName(db);
                newTable.setTableName(name);
                newTable.setEngine(engine);
                newTable.setTotalRows(totalRows);
                newTable.setTotalBytes(totalBytes);
                newTable.setTableComment(comment);
                newTable.setOwnerId(1L); // 默认责任人：管理员
                newTable.setLastDataUpdateTime(lastUpdateMap.get(key));
                tableMapper.insert(newTable);

                syncColumns(newTable.getId(), db, name);
                log.info("新增表元数据: {}", key);
            } else {
                // 更新表（字段有变化时才更新）
                java.time.LocalDateTime lastDataUpdateTime = lastUpdateMap.get(key);
                boolean changed = !Objects.equals(localTable.getEngine(), engine)
                        || !Objects.equals(localTable.getTotalRows(), totalRows)
                        || !Objects.equals(localTable.getTotalBytes(), totalBytes)
                        || !Objects.equals(localTable.getTableComment(), comment)
                        || !Objects.equals(localTable.getLastDataUpdateTime(), lastDataUpdateTime);

                if (changed) {
                    localTable.setEngine(engine);
                    localTable.setTotalRows(totalRows);
                    localTable.setTotalBytes(totalBytes);
                    localTable.setTableComment(comment);
                    localTable.setLastDataUpdateTime(lastDataUpdateTime);
                    tableMapper.updateById(localTable);
                    log.info("更新表元数据: {}", key);
                }

                syncColumns(localTable.getId(), db, name);
            }
        }

        // 4. 处理 ClickHouse 中已不存在的表：逻辑删除
        for (MetadataTable localTable : localTables) {
            String key = localTable.getDatabaseName() + "." + localTable.getTableName();
            if (!chTableKeys.contains(key)) {
                tableMapper.deleteById(localTable.getId());
                // 物理删除该表下的所有字段（metadata_column 无逻辑删除字段）
                columnMapper.delete(new QueryWrapper<MetadataColumn>()
                        .eq("table_id", localTable.getId()));
                log.info("删除表元数据: {}", key);
            }
        }

        log.info("ClickHouse 元数据采集完成，共处理 {} 张表", chTableKeys.size());
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

        Set<String> chColumnNames = new HashSet<>();

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

        // 删除本地多余的字段（物理删除）
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
