package com.mogu.data.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.system.entity.Role;
import com.mogu.data.system.entity.RolePermission;
import com.mogu.data.system.service.RoleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 角色管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
@Validated
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/page")
    public Result<Page<Role>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(roleService.pageRoles(keyword, page, size));
    }

    @GetMapping("/list")
    public Result<List<Role>> list() {
        return Result.success(roleService.lambdaQuery().eq(Role::getStatus, 1).eq(Role::getDeleted, 0).list());
    }

    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody RoleCreateRequest request) {
        requireAdmin();
        Role role = new Role();
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDescription(request.getDescription());
        roleService.createRole(role);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        requireAdmin();
        Role role = new Role();
        role.setId(id);
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        roleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        roleService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    public Result<List<RolePermission>> getRolePermissions(@PathVariable Long id) {
        return Result.success(roleService.getRolePermissions(id));
    }

    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<RolePermission> permissions) {
        requireAdmin();
        roleService.assignPermissions(id, permissions);
        return Result.success();
    }

    private void requireAdmin() {
        if (!LoginUser.isCurrentAdmin()) {
            throw new com.mogu.data.common.BusinessException("无权限，仅管理员可操作");
        }
    }

    @Data
    public static class RoleCreateRequest {
        @NotBlank(message = "角色名称不能为空")
        private String roleName;
        @NotBlank(message = "角色编码不能为空")
        private String roleCode;
        private String description;
    }

    @Data
    public static class RoleUpdateRequest {
        private String roleName;
        private String roleCode;
        private String description;
        private Integer status;
    }

}
