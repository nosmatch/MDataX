package com.mogu.data.system.service;

import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.system.entity.Role;
import com.mogu.data.system.entity.RolePermission;
import com.mogu.data.system.entity.UserRole;
import com.mogu.data.system.mapper.RoleMapper;
import com.mogu.data.system.mapper.RolePermissionMapper;
import com.mogu.data.system.mapper.UserMapper;
import com.mogu.data.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;

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

    /**
     * 获取或创建用户的个人角色
     */
    @Transactional
    public Long getOrCreatePersonalRole(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
        );
        if (userRoles != null && !userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(java.util.stream.Collectors.toList());
            List<Role> roles = roleMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                            .in(Role::getId, roleIds)
                            .eq(Role::getRoleType, 2)
                            .eq(Role::getDeleted, 0)
            );
            if (roles != null && !roles.isEmpty()) {
                return roles.get(0).getId();
            }
        }
        // 创建个人角色
        com.mogu.data.system.entity.User user = userMapper.selectById(userId);
        Role personalRole = new Role();
        personalRole.setRoleName(user != null && user.getNickname() != null ? user.getNickname() : "用户" + userId);
        personalRole.setRoleCode("user_" + userId);
        personalRole.setDescription("用户个人角色");
        personalRole.setRoleType(2);
        personalRole.setStatus(1);
        roleMapper.insert(personalRole);

        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(personalRole.getId());
        userRoleMapper.insert(ur);
        return personalRole.getId();
    }

    /**
     * 给指定角色授予表权限
     */
    @Transactional
    public void grantPermission(Long roleId, String tableName, String permissionType) {
        RolePermission exist = rolePermissionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
                        .eq(RolePermission::getTableName, tableName)
                        .eq(RolePermission::getPermissionType, permissionType)
        );
        if (exist == null) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setTableName(tableName);
            rp.setPermissionType(permissionType);
            rolePermissionMapper.insert(rp);
        }
    }

}
