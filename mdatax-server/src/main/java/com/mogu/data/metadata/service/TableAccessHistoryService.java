package com.mogu.data.metadata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.entity.TableAccessHistory;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.metadata.mapper.TableAccessHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表访问历史记录服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableAccessHistoryService extends ServiceImpl<TableAccessHistoryMapper, TableAccessHistory> {

    private final MetadataTableMapper metadataTableMapper;

    /**
     * 记录读访问（自动查找 tableId）
     */
    public void recordRead(Long userId, String username, String databaseName, String tableName, String ip) {
        Long tableId = resolveTableId(databaseName, tableName);
        record(userId, username, tableId, databaseName, tableName, "READ", ip);
    }

    /**
     * 记录写访问（自动查找 tableId）
     */
    public void recordWrite(Long userId, String username, String databaseName, String tableName, String ip) {
        Long tableId = resolveTableId(databaseName, tableName);
        record(userId, username, tableId, databaseName, tableName, "WRITE", ip);
    }

    private void record(Long userId, String username, Long tableId, String databaseName, String tableName, String accessType, String ip) {
        TableAccessHistory history = new TableAccessHistory();
        history.setUserId(userId);
        history.setUsername(username);
        history.setTableId(tableId);
        history.setDatabaseName(databaseName);
        history.setTableName(tableName);
        history.setAccessType(accessType);
        history.setIp(ip);
        save(history);
        log.debug("记录表访问历史: {}.{} type={} user={}", databaseName, tableName, accessType, username);
    }

    private Long resolveTableId(String databaseName, String tableName) {
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new QueryWrapper<MetadataTable>()
                        .eq("database_name", databaseName)
                        .eq("table_name", tableName)
                        .eq("deleted", 0)
                        .last("LIMIT 1")
        );
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        return tables.get(0).getId();
    }

    /**
     * 分页查询指定表的访问历史（按时间倒序）
     */
    public Page<TableAccessHistory> pageHistory(Long tableId, long page, long size) {
        LambdaQueryWrapper<TableAccessHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableAccessHistory::getTableId, tableId);
        wrapper.orderByDesc(TableAccessHistory::getAccessTime);
        return page(new Page<>(page, size), wrapper);
    }

}
