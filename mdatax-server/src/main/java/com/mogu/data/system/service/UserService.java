package com.mogu.data.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.system.entity.Role;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.entity.UserRole;
import com.mogu.data.system.mapper.RoleMapper;
import com.mogu.data.system.mapper.UserMapper;
import com.mogu.data.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Page<User> pageUsers(String keyword, Integer status, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Transactional
    public void createUser(User user) {
        if (lambdaQuery().eq(User::getUsername, user.getUsername()).count() > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        save(user);

        // 自动生成个人角色并绑定
        Role personalRole = new Role();
        personalRole.setRoleName(user.getNickname() != null ? user.getNickname() : user.getUsername());
        personalRole.setRoleCode("user_" + user.getId());
        personalRole.setDescription("用户个人角色");
        personalRole.setRoleType(2);
        personalRole.setStatus(1);
        roleMapper.insert(personalRole);

        UserRole ur = new UserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(personalRole.getId());
        userRoleMapper.insert(ur);
    }

    @Transactional
    public void updateUser(User user) {
        User exist = getById(user.getId());
        if (exist == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (StringUtils.hasText(user.getUsername()) && !exist.getUsername().equals(user.getUsername())) {
            if (lambdaQuery().eq(User::getUsername, user.getUsername()).count() > 0) {
                throw new IllegalArgumentException("用户名已存在");
            }
        }
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        updateById(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        removeById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
    }

    public User findByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).eq(User::getDeleted, 0).one();
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    public List<Long> getUserRoleIds(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).collect(Collectors.toList());
    }

    /**
     * 判断用户是否为管理员（拥有 roleCode = admin 的角色）
     */
    public boolean isAdmin(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return false;
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().anyMatch(r -> "admin".equals(r.getRoleCode()));
    }

    /**
     * 搜索用户（按用户名或昵称模糊查询）
     * 用于协作者选择等功能
     *
     * @param keyword 搜索关键词
     * @return 用户列表
     */
    public List<User> searchUsers(String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDeleted, 0);
        wrapper.eq(User::getStatus, 1); // 只查询正常状态的用户
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword));
        }
        wrapper.last("LIMIT 20"); // 限制返回20条
        return list(wrapper);
    }

}
