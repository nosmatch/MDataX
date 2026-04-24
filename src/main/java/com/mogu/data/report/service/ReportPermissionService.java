package com.mogu.data.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mogu.data.common.BusinessException;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.entity.ReportCollaborator;
import com.mogu.data.report.mapper.ReportCollaboratorMapper;
import com.mogu.data.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报表权限服务
 * 负责报表的访问权限控制和校验
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPermissionService {

    private final ReportService reportService;
    private final ReportCollaboratorService collaboratorService;
    private final ReportMapper reportMapper;
    private final ReportCollaboratorMapper collaboratorMapper;

    /**
     * 检查用户是否可以查看报表
     * 权限规则：
     * 1. 报表所有者可以查看
     * 2. 公开报表所有人可以查看
     * 3. 私有报表的协作者（viewer/editor）可以查看
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否有查看权限
     */
    public boolean canViewReport(Long reportId, Long userId) {
        if (userId == null) {
            return false;
        }

        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return false;
        }

        // 所有者可以查看
        if (userId.equals(report.getOwnerId())) {
            return true;
        }

        // 公开报表所有人可以查看
        if ("public".equals(report.getVisibility())) {
            return true;
        }

        // 私有报表检查协作者权限
        if ("private".equals(report.getVisibility())) {
            ReportCollaborator collaborator = collaboratorService.getByReportIdAndUserId(reportId, userId);
            return collaborator != null;
        }

        return false;
    }

    /**
     * 检查用户是否可以编辑报表
     * 权限规则：
     * 1. 报表所有者可以编辑
     * 2. 协作者角色为 editor 的可以编辑
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否有编辑权限
     */
    public boolean canEditReport(Long reportId, Long userId) {
        if (userId == null) {
            return false;
        }

        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return false;
        }

        // 所有者可以编辑
        if (userId.equals(report.getOwnerId())) {
            return true;
        }

        // 协作者检查是否有 editor 角色
        ReportCollaborator collaborator = collaboratorService.getByReportIdAndUserId(reportId, userId);
        return collaborator != null && "editor".equals(collaborator.getRole());
    }

    /**
     * 检查用户是否可以删除报表
     * 权限规则：
     * 只有报表所有者可以删除
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否有删除权限
     */
    public boolean canDeleteReport(Long reportId, Long userId) {
        if (userId == null) {
            return false;
        }

        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return false;
        }

        // 只有所有者可以删除
        return userId.equals(report.getOwnerId());
    }

    /**
     * 检查用户是否可以执行报表SQL
     * 权限规则：
     * 1. 报表所有者可以执行
     * 2. 公开报表所有人可以执行
     * 3. 私有报表的协作者（viewer/editor）可以执行
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否有执行权限
     */
    public boolean canExecuteReport(Long reportId, Long userId) {
        // 执行权限与查看权限相同
        return canViewReport(reportId, userId);
    }

    /**
     * 检查用户是否可以管理协作者
     * 权限规则：
     * 只有报表所有者可以管理协作者
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否有管理权限
     */
    public boolean canManageCollaborators(Long reportId, Long userId) {
        if (userId == null) {
            return false;
        }

        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return false;
        }

        // 只有所有者可以管理协作者
        return userId.equals(report.getOwnerId());
    }

    /**
     * 检查用户是否是报表所有者
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 是否是所有者
     */
    public boolean isOwner(Long reportId, Long userId) {
        if (userId == null) {
            return false;
        }

        Report report = reportService.getById(reportId);
        if (report == null) {
            return false;
        }

        return userId.equals(report.getOwnerId());
    }

    /**
     * 获取用户在报表中的角色
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 角色：owner-所有者, editor-编辑者, viewer-查看者, null-无权限
     */
    public String getReportRole(Long reportId, Long userId) {
        if (userId == null) {
            return null;
        }

        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            return null;
        }

        // 检查是否是所有者
        if (userId.equals(report.getOwnerId())) {
            return "owner";
        }

        // 检查是否是协作者
        ReportCollaborator collaborator = collaboratorService.getByReportIdAndUserId(reportId, userId);
        if (collaborator != null) {
            return collaborator.getRole();
        }

        // 公开报表，其他用户默认为 viewer
        if ("public".equals(report.getVisibility())) {
            return "viewer";
        }

        return null;
    }

    /**
     * 验证查看权限，无权限时抛出异常
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @throws BusinessException 无权限时抛出
     */
    public void requireViewPermission(Long reportId, Long userId) {
        if (!canViewReport(reportId, userId)) {
            throw new BusinessException("无权限访问该报表");
        }
    }

    /**
     * 验证编辑权限，无权限时抛出异常
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @throws BusinessException 无权限时抛出
     */
    public void requireEditPermission(Long reportId, Long userId) {
        if (!canEditReport(reportId, userId)) {
            throw new BusinessException("无权限编辑该报表");
        }
    }

    /**
     * 验证删除权限，无权限时抛出异常
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @throws BusinessException 无权限时抛出
     */
    public void requireDeletePermission(Long reportId, Long userId) {
        if (!canDeleteReport(reportId, userId)) {
            throw new BusinessException("无权限删除该报表");
        }
    }

    /**
     * 验证执行权限，无权限时抛出异常
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @throws BusinessException 无权限时抛出
     */
    public void requireExecutePermission(Long reportId, Long userId) {
        if (!canExecuteReport(reportId, userId)) {
            throw new BusinessException("无权限执行该报表");
        }
    }

    /**
     * 验证协作者管理权限，无权限时抛出异常
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @throws BusinessException 无权限时抛出
     */
    public void requireManageCollaboratorsPermission(Long reportId, Long userId) {
        if (!canManageCollaborators(reportId, userId)) {
            throw new BusinessException("无权限管理该报表的协作者");
        }
    }
}
