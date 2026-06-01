package com.mogu.data.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.BusinessException;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.system.entity.PermissionApply;
import com.mogu.data.system.entity.RolePermission;
import com.mogu.data.system.enums.PermissionApplyStatus;
import com.mogu.data.system.mapper.PermissionApplyMapper;
import com.mogu.data.system.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限申请服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionApplyService {

    private final PermissionApplyMapper applyMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final PermissionService permissionService;
    private final RolePermissionMapper rolePermissionMapper;
    private final com.mogu.data.system.mapper.UserMapper userMapper;

    /**
     * 提交权限申请
     */
    @Transactional
    public void submitApply(Long applicantId, String applicantName,
                            String databaseName, String tableName,
                            String applyType, String applyReason) {
        String fullTableName = databaseName + "." + tableName;

        // 1. 校验是否已有该权限
        boolean hasPermission;
        if ("WRITE".equalsIgnoreCase(applyType)) {
            hasPermission = permissionService.hasWritePermission(applicantId, fullTableName);
        } else {
            hasPermission = permissionService.hasReadPermission(applicantId, fullTableName);
        }
        if (hasPermission) {
            throw new BusinessException("您已拥有该表的" + ("WRITE".equalsIgnoreCase(applyType) ? "写" : "读") + "权限，无需申请");
        }

        // 2. 校验是否有待审批的重复申请
        PermissionApply pending = applyMapper.selectPendingApply(applicantId, databaseName, tableName, applyType.toUpperCase());
        if (pending != null) {
            throw new BusinessException("您已有一个待审批的相同申请，请勿重复提交");
        }

        // 3. 获取表信息及责任人
        MetadataTable table = findTable(databaseName, tableName);
        if (table == null) {
            throw new BusinessException("表不存在");
        }
        if (table.getOwnerId() == null) {
            throw new BusinessException("该表暂未设置责任人，无法提交申请，请联系管理员");
        }

        // 4. 申请人是责任人时直接提示
        if (applicantId.equals(table.getOwnerId())) {
            throw new BusinessException("您是该表责任人，已自动拥有权限");
        }

        // 5. 保存申请
        PermissionApply apply = new PermissionApply();
        apply.setApplicantId(applicantId);
        apply.setApplicantName(applicantName);
        apply.setTableId(table.getId());
        apply.setDatabaseName(databaseName);
        apply.setTableName(tableName);
        apply.setTableComment(table.getTableComment());
        apply.setApplyType(applyType.toUpperCase());
        apply.setApplyReason(applyReason);
        apply.setStatus(PermissionApplyStatus.PENDING.getCode());
        apply.setOwnerId(table.getOwnerId());
        com.mogu.data.system.entity.User owner = userMapper.selectById(table.getOwnerId());
        apply.setOwnerName(owner != null ? (owner.getNickname() != null ? owner.getNickname() : owner.getUsername()) : "");
        applyMapper.insert(apply);

        log.info("用户 {} 提交了表 {} {} 权限申请", applicantId, fullTableName, applyType);
    }

    /**
     * 撤回待审批申请
     */
    @Transactional
    public void cancelApply(Long applyId, Long applicantId) {
        PermissionApply apply = applyMapper.selectById(applyId);
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
        log.info("用户 {} 撤回了申请 {}", applicantId, applyId);
    }

    /**
     * 审批通过
     */
    @Transactional
    public void approve(Long applyId, Long ownerId, String comment) {
        PermissionApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!apply.getOwnerId().equals(ownerId)) {
            throw new BusinessException("您不是该表责任人，无权审批");
        }
        if (apply.getStatus() != PermissionApplyStatus.PENDING.getCode()) {
            throw new BusinessException("该申请已处理，请勿重复操作");
        }

        // 校验表是否仍存在
        MetadataTable table = findTable(apply.getDatabaseName(), apply.getTableName());
        if (table == null) {
            throw new BusinessException("该表已删除，无法审批");
        }

        // 更新申请状态
        apply.setStatus(PermissionApplyStatus.APPROVED.getCode());
        apply.setApproveTime(LocalDateTime.now());
        apply.setApproveComment(StringUtils.hasText(comment) ? comment : "同意");
        applyMapper.updateById(apply);

        // 给申请人的个人角色授权
        Long personalRoleId = permissionService.getOrCreatePersonalRole(apply.getApplicantId());
        String fullTableName = apply.getDatabaseName() + "." + apply.getTableName();
        permissionService.grantPermission(personalRoleId, fullTableName, apply.getApplyType());

        log.info("责任人 {} 通过了申请 {}，给角色 {} 授予 {} {}", ownerId, applyId, personalRoleId, fullTableName, apply.getApplyType());
    }

    /**
     * 审批拒绝
     */
    @Transactional
    public void reject(Long applyId, Long ownerId, String comment) {
        PermissionApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!apply.getOwnerId().equals(ownerId)) {
            throw new BusinessException("您不是该表责任人，无权审批");
        }
        if (apply.getStatus() != PermissionApplyStatus.PENDING.getCode()) {
            throw new BusinessException("该申请已处理，请勿重复操作");
        }

        apply.setStatus(PermissionApplyStatus.REJECTED.getCode());
        apply.setApproveTime(LocalDateTime.now());
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("拒绝时请填写审批意见");
        }
        apply.setApproveComment(comment);
        applyMapper.updateById(apply);

        log.info("责任人 {} 拒绝了申请 {}，原因: {}", ownerId, applyId, comment);
    }

    /**
     * 我的申请列表
     */
    public Page<PermissionApply> getMyApplies(Long applicantId, long page, long size) {
        LambdaQueryWrapper<PermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionApply::getApplicantId, applicantId);
        wrapper.orderByDesc(PermissionApply::getCreateTime);
        Page<PermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    /**
     * 待我审批的列表
     */
    public Page<PermissionApply> getPendingApprovals(Long ownerId, long page, long size) {
        LambdaQueryWrapper<PermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionApply::getOwnerId, ownerId);
        wrapper.eq(PermissionApply::getStatus, PermissionApplyStatus.PENDING.getCode());
        wrapper.orderByDesc(PermissionApply::getCreateTime);
        Page<PermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    /**
     * 我已审批的列表
     */
    public Page<PermissionApply> getApprovalHistory(Long ownerId, long page, long size) {
        LambdaQueryWrapper<PermissionApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionApply::getOwnerId, ownerId);
        wrapper.ne(PermissionApply::getStatus, PermissionApplyStatus.PENDING.getCode());
        wrapper.orderByDesc(PermissionApply::getCreateTime);
        Page<PermissionApply> result = applyMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerName(result.getRecords());
        return result;
    }

    private void fillOwnerName(List<PermissionApply> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (PermissionApply apply : list) {
            if (!StringUtils.hasText(apply.getOwnerName()) && apply.getOwnerId() != null) {
                com.mogu.data.system.entity.User owner = userMapper.selectById(apply.getOwnerId());
                if (owner != null) {
                    apply.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
                } else {
                    apply.setOwnerName("未知用户");
                }
            }
        }
    }

    /**
     * 批量查询用户的待审批申请
     */
    public Map<String, List<String>> getPendingApplyMap(Long userId, List<String> tableNames) {
        if (userId == null || tableNames == null || tableNames.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<PermissionApply> list = applyMapper.selectPendingAppliesByUserId(userId);
        Set<String> tableNameSet = new java.util.HashSet<>(tableNames);
        return list.stream()
                .filter(a -> tableNameSet.contains(a.getDatabaseName() + "." + a.getTableName()))
                .collect(Collectors.groupingBy(
                        a -> a.getDatabaseName() + "." + a.getTableName(),
                        Collectors.mapping(PermissionApply::getApplyType, Collectors.toList())
                ));
    }

    private MetadataTable findTable(String databaseName, String tableName) {
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>()
                        .eq(MetadataTable::getDatabaseName, databaseName)
                        .eq(MetadataTable::getTableName, tableName)
                        .eq(MetadataTable::getDeleted, 0)
                        .last("LIMIT 1")
        );
        return (tables != null && !tables.isEmpty()) ? tables.get(0) : null;
    }
}
