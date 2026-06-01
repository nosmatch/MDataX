package com.mogu.data.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.system.entity.PermissionApply;
import com.mogu.data.system.service.PermissionApplyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 权限申请控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/permission/apply")
@RequiredArgsConstructor
public class PermissionApplyController {

    private final PermissionApplyService applyService;

    /**
     * 提交权限申请
     */
    @PostMapping
    public Result<Void> apply(@RequestBody ApplyRequest request) {
        Long userId = LoginUser.currentUserId();
        String username = LoginUser.currentUsername();
        applyService.submitApply(userId, username,
                request.getDatabaseName(), request.getTableName(),
                request.getApplyType(), request.getApplyReason());
        return Result.success();
    }

    /**
     * 撤回申请
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        Long userId = LoginUser.currentUserId();
        applyService.cancelApply(id, userId);
        return Result.success();
    }

    /**
     * 我的申请列表
     */
    @GetMapping("/my-list")
    public Result<Page<PermissionApply>> myList(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        return Result.success(applyService.getMyApplies(userId, page, size));
    }

    /**
     * 待我审批的列表
     */
    @GetMapping("/approval/pending")
    public Result<Page<PermissionApply>> pendingApprovals(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        return Result.success(applyService.getPendingApprovals(userId, page, size));
    }

    /**
     * 我已审批的列表
     */
    @GetMapping("/approval/history")
    public Result<Page<PermissionApply>> approvalHistory(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        return Result.success(applyService.getApprovalHistory(userId, page, size));
    }

    /**
     * 审批操作（通过/拒绝）
     */
    @PutMapping("/approval/{id}")
    public Result<Void> approve(@PathVariable Long id, @RequestBody ApprovalRequest request) {
        Long userId = LoginUser.currentUserId();
        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            applyService.approve(id, userId, request.getComment());
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            applyService.reject(id, userId, request.getComment());
        } else {
            return Result.error("非法操作类型");
        }
        return Result.success();
    }

    @Data
    public static class ApplyRequest {
        private String databaseName;
        private String tableName;
        private String applyType;
        private String applyReason;
    }

    @Data
    public static class ApprovalRequest {
        private String action;
        private String comment;
    }
}
