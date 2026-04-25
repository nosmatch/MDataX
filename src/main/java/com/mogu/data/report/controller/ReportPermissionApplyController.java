package com.mogu.data.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.report.entity.ReportPermissionApply;
import com.mogu.data.report.service.ReportPermissionApplyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 报表权限申请控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/report-apply")
@RequiredArgsConstructor
public class ReportPermissionApplyController {

    private final ReportPermissionApplyService applyService;

    /**
     * 提交报表权限申请
     *
     * @param id      报表ID
     * @param request 申请请求
     * @return 操作结果
     */
    @PostMapping("/report/{id}")
    public Result<Void> apply(@PathVariable Long id, @RequestBody ApplyRequest request) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        String username = LoginUser.currentUsername();

        // 校验角色有效性
        if (!"viewer".equals(request.getApplyRole()) && !"editor".equals(request.getApplyRole())) {
            return Result.error("无效的申请角色，必须是 viewer 或 editor");
        }

        applyService.submitApply(userId, username, id, request.getApplyRole(), request.getApplyReason());
        return Result.success();
    }

    /**
     * 撤回申请
     *
     * @param applyId 申请ID
     * @return 操作结果
     */
    @PutMapping("/{applyId}/cancel")
    public Result<Void> cancel(@PathVariable Long applyId) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        applyService.cancelApply(applyId, userId);
        return Result.success();
    }

    /**
     * 我的报表权限申请列表
     *
     * @return 申请列表
     */
    @GetMapping("/my-list")
    public Result<Page<ReportPermissionApply>> myList(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        return Result.success(applyService.getMyApplies(userId, page, size));
    }

    /**
     * 待我审批的报表权限申请列表
     *
     * @return 申请列表
     */
    @GetMapping("/pending")
    public Result<Page<ReportPermissionApply>> pendingApprovals(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        return Result.success(applyService.getPendingApprovals(userId, page, size));
    }

    /**
     * 我已审批的报表权限申请列表
     *
     * @return 申请列表
     */
    @GetMapping("/history")
    public Result<Page<ReportPermissionApply>> approvalHistory(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        return Result.success(applyService.getApprovalHistory(userId, page, size));
    }

    /**
     * 审批操作（通过/拒绝）
     *
     * @param applyId 申请ID
     * @param request 审批请求
     * @return 操作结果
     */
    @PutMapping("/{applyId}")
    public Result<Void> approve(@PathVariable Long applyId, @RequestBody ApprovalRequest request) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            applyService.approve(applyId, userId, request.getComment());
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            applyService.reject(applyId, userId, request.getComment());
        } else {
            return Result.error("非法操作类型");
        }

        return Result.success();
    }

    /**
     * 申请请求
     */
    @Data
    public static class ApplyRequest {
        /**
         * 申请角色：viewer/editor
         */
        private String applyRole;

        /**
         * 申请理由
         */
        private String applyReason;
    }

    /**
     * 审批请求
     */
    @Data
    public static class ApprovalRequest {
        /**
         * 操作类型：APPROVE-通过, REJECT-拒绝
         */
        private String action;

        /**
         * 审批意见
         */
        private String comment;
    }
}
