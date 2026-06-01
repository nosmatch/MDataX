package com.mogu.data.report.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.report.dto.ReportCollaboratorDTO;
import com.mogu.data.report.entity.ReportCollaborator;
import com.mogu.data.report.service.ReportCollaboratorService;
import com.mogu.data.report.service.ReportPermissionService;
import com.mogu.data.report.service.ReportService;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报表协作者管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/report/{id}/collaborators")
@RequiredArgsConstructor
public class ReportCollaboratorController {

    private final ReportCollaboratorService collaboratorService;
    private final ReportPermissionService permissionService;
    private final ReportService reportService;
    private final UserService userService;

    /**
     * 查询报表的协作者列表
     *
     * @param id 报表ID
     * @return 协作者列表（包含用户信息）
     */
    @GetMapping
    public Result<List<ReportCollaboratorDTO>> getCollaborators(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：需要查看权限
        permissionService.requireViewPermission(id, userId);

        List<ReportCollaboratorDTO> collaborators = collaboratorService.getCollaboratorsWithUserInfo(id);
        return Result.success(collaborators);
    }

    /**
     * 添加协作者
     *
     * @param id 报表ID
     * @param collaborator 协作者信息（需要包含 userId 和 role）
     * @return 操作结果
     */
    @PostMapping
    public Result<Void> addCollaborator(@PathVariable Long id, @RequestBody ReportCollaborator collaborator) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：只有所有者可以管理协作者
        permissionService.requireManageCollaboratorsPermission(id, userId);

        // 验证角色有效性
        if (!"viewer".equals(collaborator.getRole()) && !"editor".equals(collaborator.getRole())) {
            return Result.error("无效的协作者角色，必须是 viewer 或 editor");
        }

        collaboratorService.addCollaborator(id, collaborator.getUserId(), collaborator.getRole());
        return Result.success();
    }

    /**
     * 通过用户名添加协作者
     *
     * @param id 报表ID
     * @param request 添加请求（包含 username 和 role）
     * @return 操作结果
     */
    @PostMapping("/by-username")
    public Result<Void> addCollaboratorByUsername(@PathVariable Long id, @RequestBody AddCollaboratorRequest request) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：只有所有者可以管理协作者
        permissionService.requireManageCollaboratorsPermission(id, userId);

        // 验证角色有效性
        if (!"viewer".equals(request.getRole()) && !"editor".equals(request.getRole())) {
            return Result.error("无效的协作者角色，必须是 viewer 或 editor");
        }

        // 根据用户名查找用户
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证用户状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            return Result.error("用户已被禁用");
        }

        collaboratorService.addCollaborator(id, user.getId(), request.getRole());
        return Result.success();
    }

    /**
     * 添加协作者请求
     */
    public static class AddCollaboratorRequest {
        private String username;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    /**
     * 移除协作者
     *
     * @param id     报表ID
     * @param userId 要移除的协作者用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{userId}")
    public Result<Void> removeCollaborator(@PathVariable Long id, @PathVariable Long userId) {
        Long currentUserId = LoginUser.currentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：只有所有者可以管理协作者
        permissionService.requireManageCollaboratorsPermission(id, currentUserId);

        collaboratorService.removeCollaborator(id, userId);
        return Result.success();
    }

    /**
     * 更新协作者角色
     *
     * @param id      报表ID
     * @param userId  协作者用户ID
     * @param role    新角色
     * @return 操作结果
     */
    @PutMapping("/{userId}/role")
    public Result<Void> updateCollaboratorRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody String role) {
        Long currentUserId = LoginUser.currentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：只有所有者可以管理协作者
        permissionService.requireManageCollaboratorsPermission(id, currentUserId);

        // 验证角色有效性
        if (!"viewer".equals(role) && !"editor".equals(role)) {
            return Result.error("无效的协作者角色，必须是 viewer 或 editor");
        }

        collaboratorService.updateCollaboratorRole(id, userId, role);
        return Result.success();
    }

    /**
     * 转移报表所有权
     * 将报表所有权转移给另一个用户
     *
     * @param id         报表ID
     * @param newOwnerId 新所有者用户ID
     * @return 操作结果
     */
    @PutMapping("/owner")
    public Result<Void> transferOwnership(@PathVariable Long id, @RequestBody Long newOwnerId) {
        Long currentUserId = LoginUser.currentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        // 权限校验：只有当前所有者可以转移所有权
        permissionService.requireManageCollaboratorsPermission(id, currentUserId);

        // 验证新所有者不是当前所有者
        if (currentUserId.equals(newOwnerId)) {
            return Result.error("不能将所有权转移给自己");
        }

        // TODO: 实现所有权转移逻辑
        // 1. 更新 report.owner_id
        // 2. 将原所有者添加为 editor 协作者（可选）
        // 3. 记录操作日志

        return Result.error("功能开发中");
    }
}
