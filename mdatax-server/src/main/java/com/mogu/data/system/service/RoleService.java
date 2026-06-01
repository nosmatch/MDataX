package com.mogu.data.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.system.entity.Role;
import com.mogu.data.system.entity.RolePermission;
import com.mogu.data.system.entity.UserRole;
import com.mogu.data.system.mapper.RoleMapper;
import com.mogu.data.system.mapper.RolePermissionMapper;
import com.mogu.data.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class RoleService extends ServiceImpl<RoleMapper, Role> {

    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public Page<Role> pageRoles(String keyword, long page, long size) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Role::getRoleName, keyword)
                    .or()
                    .like(Role::getRoleCode, keyword));
        }
        wrapper.orderByDesc(Role::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Transactional
    public void createRole(Role role) {
        if (lambdaQuery().eq(Role::getRoleCode, role.getRoleCode()).count() > 0) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        role.setStatus(1);
        save(role);
    }

    @Transactional
    public void updateRole(Role role) {
        Role exist = getById(role.getId());
        if (exist == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (StringUtils.hasText(role.getRoleCode()) && !exist.getRoleCode().equals(role.getRoleCode())) {
            if (lambdaQuery().eq(Role::getRoleCode, role.getRoleCode()).count() > 0) {
                throw new IllegalArgumentException("角色编码已存在");
            }
        }
        updateById(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        removeById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
    }

    @Transactional
    public void assignPermissions(Long roleId, List<RolePermission> permissions) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        if (permissions != null && !permissions.isEmpty()) {
            for (RolePermission rp : permissions) {
                rp.setRoleId(roleId);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    public List<RolePermission> getRolePermissions(Long roleId) {
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );
    }

}
