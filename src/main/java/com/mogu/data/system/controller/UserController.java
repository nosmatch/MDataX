package com.mogu.data.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    public Result<Page<User>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.pageUsers(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        return Result.success(toUserMap(user));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserCreateRequest request) {
        requireAdmin();
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        userService.createUser(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        Long currentUserId = LoginUser.currentUserId();
        if (!LoginUser.isCurrentAdmin() && !id.equals(currentUserId)) {
            throw new com.mogu.data.common.BusinessException("无权限，只能修改自己的信息");
        }
        User user = new User();
        user.setId(id);
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus());
        userService.updateUser(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        userService.deleteUser(id);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    public Result<List<Long>> getUserRoles(@PathVariable Long id) {
        return Result.success(userService.getUserRoleIds(id));
    }

    @PostMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        requireAdmin();
        userService.assignRoles(id, roleIds);
        return Result.success();
    }

    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentUser() {
        Long userId = LoginUser.currentUserId();
        User user = userService.getById(userId);
        Map<String, Object> map = toUserMap(user);
        map.put("isAdmin", LoginUser.isCurrentAdmin());
        return Result.success(map);
    }

    private void requireAdmin() {
        if (!LoginUser.isCurrentAdmin()) {
            throw new com.mogu.data.common.BusinessException("无权限，仅管理员可操作");
        }
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        if (user == null) return map;
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("status", user.getStatus());
        map.put("createTime", user.getCreateTime());
        map.put("updateTime", user.getUpdateTime());
        return map;
    }

    @Data
    public static class UserCreateRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String nickname;
        private String email;
    }

    @Data
    public static class UserUpdateRequest {
        private String username;
        private String password;
        private String nickname;
        private String email;
        private Integer status;
    }

}
