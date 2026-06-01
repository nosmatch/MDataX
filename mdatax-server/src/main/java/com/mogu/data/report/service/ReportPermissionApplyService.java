package com.mogu.data.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.common.BusinessException;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.entity.ReportCollaborator;
import com.mogu.data.report.entity.ReportPermissionApply;
import com.mogu.data.report.mapper.ReportPermissionApplyMapper;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.enums.PermissionApplyStatus;
import com.mogu.data.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 报表权限申请服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPermissionApplyService extends ServiceImpl<ReportPermissionApplyMapper, ReportPermissionApply> {

    private final ReportPermissionApplyMapper applyMapper;
    private final ReportService reportService;
    private final ReportCollaboratorService collaboratorService;
    private final UserMapper userMapper;

    /**
     * 提交报表权限申请
     *
     * @param applicantId   申请人ID
     * @param applicantName 申请人姓名
     * @param reportId      报表ID
     * @param applyRole     申请角色（viewer/editor）
     * @param applyReason   申请理由
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitApply(Long applicantId, String applicantName, Long reportId, String applyRole, String applyReason) {
        // 1. 校验报表存在性
        Report report = reportService.getById(reportId);
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            throw new BusinessException("报表不存在");
        }

        // 2. 校验报表是否为私有（公开报表无需申请）
        if ("public".equals(report.getVisibility())) {
            throw new BusinessException("该报表为公开报表，无需申请权限即可查看");
        }

        // 3. 校验是否已有该报表权限
        if (applicantId.equals(report.getOwnerId())) {
            throw new BusinessException("您是该报表所有者，已拥有所有权限");
        }

        ReportCollaborator existingCollaborator = collaboratorService.getByReportIdAndUserId(reportId, applicantId);
        if (existingCollaborator != null) {
            String currentRole = existingCollaborator.getRole();
            if ("editor".equals(currentRole)) {
                throw new BusinessException("您已拥有该报表的编辑权限，无需申请");
            } else if ("viewer".equals(currentRole) && "viewer".equals(applyRole)) {
                throw new BusinessException("您已拥有该报表的查看权限，无需申请");
            }
        }

        // 4. 校验是否有待审批的重复申请
        ReportPermissionApply pending = applyMapper.selectPendingApply(applicantId, reportId, applyRole);
        if (pending != null) {
            throw new BusinessException("您已有一个待审批的相同申请，请勿重复提交");
        }

        // 5. 校验报表所有者
        if (report.getOwnerId() == null) {
            throw new BusinessException("该报表暂未设置所有者，无法提交申请，请联系管理员");
        }

        // 6. 获取所有者信息
        User owner = userMapper.selectById(report.getOwnerId());
        if (owner == null) {
            throw new BusinessException("报表所有者不存在");
        }

        // 7. 保存申请
        ReportPermissionApply apply = new ReportPermissionApply();
        apply.setReportId(reportId);
        apply.setReportName(report.getName());
        apply.setApplicantId(applicantId);
        apply.setApplicantName(applicantName);
        apply.setOwnerId(report.getOwnerId());
        apply.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
        apply.setApplyRole(applyRole);
        apply.setApplyReason(applyReason);
        apply.setStatus(PermissionApplyStatus.PENDING.getCode());
        applyMapper.insert(apply);

        log.info("用户 {} 提交了报表 {} {} 权限申请", applicantId, reportId, applyRole);
    }

    /**
     * 撤回待审批申请
     *
     * @param applyId      申请ID
     * @param applicantId  申请人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelApply(Long applyId, Long applicantId) {
        ReportPermissionApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!apply.getApplicantId().equals(applicantId)) {
            throw new BusinessException("无权操作该申请");
        }
        if (apply.getStatus() != PermissionApplyStatus.PENDING.getCode()) {
            throw new BusinessException("该申请已处理，无法撤回");
        }

        applyMapper.deleteById(applyId);
        log.info("用户 {} 撤回了报表权限申请 {}", applicantId, applyId);
    }

    /**
     * 审批通过
     *
     * @param applyId 申请ID
     * @param ownerId 所有者ID
     * @param comment 审批意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applyId, Long ownerId, String comment) {
        ReportPermissionApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!apply.getOwnerId().equals(ownerId)) {
            throw new BusinessException("您不是该报表所有者，无权审批");
        }
        if (apply.getStatus() != PermissionApplyStatus.PENDING.getCode()) {
            throw new BusinessException("该申请已处理，请勿重复操作");
        }

        // 校验报表是否仍存在
        Report report = reportService.getById(apply.getReportId());
        if (report == null || report.getDeleted() != null && report.getDeleted() == 1) {
            throw new BusinessException("该报表已删除，无法审批");
        }

        // 更新申请状态
        apply.setStatus(PermissionApplyStatus.APPROVED.getCode());
        apply.setApproveTime(java.time.LocalDateTime.now());
        apply.setApproveComment(StringUtils.hasText(comment) ? comment : "同意");
        applyMapper.updateById(apply);

        // 添加协作者
        try {
            collaboratorService.addCollaborator(apply.getReportId(), apply.getApplicantId(), apply.getApplyRole());
        } catch (BusinessException e) {
            // 如果添加协作者失败（例如已经是协作者），不影响审批结果
            log.warn("添加协作者失败，但审批通过: {}", e.getMessage());
        }

        log.info("所有者 {} 通过了报表权限申请 {}，添加 {} 为 {}",
                ownerId, applyId, apply.getApplicantId(), apply.getApplyRole());
    }

    /**
     * 审批拒绝
     *
     * @param applyId 申请ID
     * @param ownerId 所有者ID
     * @param comment 拒绝理由
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applyId, Long ownerId, String comment) {
        ReportPermissionApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!apply.getOwnerId().equals(ownerId)) {
            throw new BusinessException("您不是该报表所有者，无权审批");
        }
        if (apply.getStatus() != PermissionApplyStatus.PENDING.getCode()) {
            throw new BusinessException("该申请已处理，请勿重复操作");
        }

        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("拒绝时请填写审批意见");
        }

        apply.setStatus(PermissionApplyStatus.REJECTED.getCode());
        apply.setApproveTime(java.time.LocalDateTime.now());
        apply.setApproveComment(comment);
        applyMapper.updateById(apply);

        log.info("所有者 {} 拒绝了报表权限申请 {}，原因: {}", ownerId, applyId, comment);
    }

    /**
     * 我的申请列表
     *
     * @param applicantId 申请人ID
     * @param page        页码
     * @param size        每页大小
     * @return 申请列表
     */
    public Page<ReportPermissionApply> getMyApplies(Long applicantId, long page, long size) {
        LambdaQueryWrapper<ReportPermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportPermissionApply::getApplicantId, applicantId);
        wrapper.orderByDesc(ReportPermissionApply::getCreateTime);
        Page<ReportPermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    /**
     * 待我审批的列表
     *
     * @param ownerId 所有者ID
     * @param page    页码
     * @param size    每页大小
     * @return 申请列表
     */
    public Page<ReportPermissionApply> getPendingApprovals(Long ownerId, long page, long size) {
        LambdaQueryWrapper<ReportPermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportPermissionApply::getOwnerId, ownerId);
        wrapper.eq(ReportPermissionApply::getStatus, PermissionApplyStatus.PENDING.getCode());
        wrapper.orderByDesc(ReportPermissionApply::getCreateTime);
        Page<ReportPermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    /**
     * 我已审批的列表
     *
     * @param ownerId 所有者ID
     * @param page    页码
     * @param size    每页大小
     * @return 申请列表
     */
    public Page<ReportPermissionApply> getApprovalHistory(Long ownerId, long page, long size) {
        LambdaQueryWrapper<ReportPermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportPermissionApply::getOwnerId, ownerId);
        wrapper.ne(ReportPermissionApply::getStatus, PermissionApplyStatus.PENDING.getCode());
        wrapper.orderByDesc(ReportPermissionApply::getCreateTime);
        Page<ReportPermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    /**
     * 填充所有者姓名（如果为空）
     *
     * @param list 申请列表
     */
    private void fillOwnerName(java.util.List<ReportPermissionApply> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (ReportPermissionApply apply : list) {
            if (!StringUtils.hasText(apply.getOwnerName()) && apply.getOwnerId() != null) {
                User owner = userMapper.selectById(apply.getOwnerId());
                if (owner != null) {
                    apply.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
                } else {
                    apply.setOwnerName("未知用户");
                }
            }
        }
    }
}
