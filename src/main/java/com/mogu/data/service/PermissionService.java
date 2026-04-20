package com.mogu.data.service;

import com.mogu.data.entity.RolePermission;
import com.mogu.data.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
     */
    public boolean hasReadPermission(Long userId, String tableName) {
        Set<String> tables = getReadableTables(userId);
        return tables.contains(tableName) || tables.contains("*");
    }

    /**
     * 校验用户是否对指定表有写权限
     */
    public boolean hasWritePermission(Long userId, String tableName) {
        Set<String> tables = getWritableTables(userId);
        return tables.contains(tableName) || tables.contains("*");
    }

}
