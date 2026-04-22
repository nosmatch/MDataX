package com.mogu.data.system.service;

import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.system.entity.RolePermission;
import com.mogu.data.system.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 权限服务
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final RolePermissionMapper rolePermissionMapper;
    private final MetadataTableMapper metadataTableMapper;

    /**
     * 查询用户可读的表名列表
     */
    public Set<String> getReadableTables(Long userId) {
        return rolePermissionMapper.selectReadableTablesByUserId(userId);
    }

    /**
     * 查询用户可写的表名列表
     */
    public Set<String> getWritableTables(Long userId) {
        return rolePermissionMapper.selectWritableTablesByUserId(userId);
    }

    /**
     * 校验用户是否对指定表有读权限
     * 责任人自动拥有读写权限
     */
    public boolean hasReadPermission(Long userId, String tableName) {
        if (isOwner(userId, tableName)) {
            return true;
        }
        Set<String> tables = getReadableTables(userId);
        return tables.contains(tableName) || tables.contains("*");
    }

    /**
     * 校验用户是否对指定表有写权限
     * 责任人自动拥有读写权限；新表（元数据中不存在）默认允许写操作
     */
    public boolean hasWritePermission(Long userId, String tableName) {
        if (isOwner(userId, tableName)) {
            return true;
        }
        // 表在元数据中不存在（可能是新表，如 CREATE TABLE），允许写操作
        // 由 ClickHouse 自行校验 SQL 有效性，执行后会自动同步元数据
        if (!tableExists(tableName)) {
            return true;
        }
        Set<String> tables = getWritableTables(userId);
        return tables.contains(tableName) || tables.contains("*");
    }

    /**
     * 判断用户是否为指定表的责任人
     */
    private boolean isOwner(Long userId, String tableName) {
        if (userId == null || tableName == null) {
            return false;
        }
        int dot = tableName.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String dbName = tableName.substring(0, dot);
        String tblName = tableName.substring(dot + 1);
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetadataTable>()
                        .eq("database_name", dbName)
                        .eq("table_name", tblName)
                        .eq("deleted", 0)
                        .eq("owner_id", userId)
                        .last("LIMIT 1")
        );
        return tables != null && !tables.isEmpty();
    }

    /**
     * 判断表是否已在元数据中登记
     */
    private boolean tableExists(String tableName) {
        if (tableName == null) {
            return false;
        }
        int dot = tableName.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String dbName = tableName.substring(0, dot);
        String tblName = tableName.substring(dot + 1);
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetadataTable>()
                        .eq("database_name", dbName)
                        .eq("table_name", tblName)
                        .eq("deleted", 0)
                        .last("LIMIT 1")
        );
        return tables != null && !tables.isEmpty();
    }

}
