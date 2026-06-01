package com.mogu.data.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.common.BusinessException;
import com.mogu.data.report.dto.ReportCollaboratorDTO;
import com.mogu.data.report.entity.Report;
import com.mogu.data.report.entity.ReportCollaborator;
import com.mogu.data.report.mapper.ReportCollaboratorMapper;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 报表协作者服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCollaboratorService extends ServiceImpl<ReportCollaboratorMapper, ReportCollaborator> {

    private final ReportService reportService;
    private final UserMapper userMapper;

    /**
     * 根据报表ID查询协作者列表
     *
     * @param reportId 报表ID
     * @return 协作者列表
     */
    public List<ReportCollaborator> getCollaboratorsByReportId(Long reportId) {
        LambdaQueryWrapper<ReportCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportCollaborator::getReportId, reportId);
        return list(wrapper);
    }

    /**
     * 根据报表ID查询协作者列表（包含用户信息）
     *
     * @param reportId 报表ID
     * @return 协作者DTO列表
     */
    public List<ReportCollaboratorDTO> getCollaboratorsWithUserInfo(Long reportId) {
        List<ReportCollaborator> collaborators = getCollaboratorsByReportId(reportId);

        return collaborators.stream().map(collaborator -> {
            ReportCollaboratorDTO dto = new ReportCollaboratorDTO();
            dto.setId(collaborator.getId());
            dto.setReportId(collaborator.getReportId());
            dto.setUserId(collaborator.getUserId());
            dto.setRole(collaborator.getRole());
            dto.setCreateTime(collaborator.getCreateTime());

            // 查询用户信息
            User user = userMapper.selectById(collaborator.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setNickname(user.getNickname());
                dto.setEmail(user.getEmail());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 根据报表ID和用户ID查询协作者关系
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @return 协作者关系，不存在时返回null
     */
    public ReportCollaborator getByReportIdAndUserId(Long reportId, Long userId) {
        LambdaQueryWrapper<ReportCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportCollaborator::getReportId, reportId);
        wrapper.eq(ReportCollaborator::getUserId, userId);
        return getOne(wrapper);
    }

    /**
     * 添加协作者
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @param role     角色（viewer/editor）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addCollaborator(Long reportId, Long userId, String role) {
        // 验证报表存在且为私有
        Report report = reportService.getById(reportId);
        if (report == null) {
            throw new BusinessException("报表不存在");
        }
        if (!"private".equals(report.getVisibility())) {
            throw new BusinessException("只有私有报表才能添加协作者");
        }

        // 检查是否已经是协作者
        ReportCollaborator existing = getByReportIdAndUserId(reportId, userId);
        if (existing != null) {
            throw new BusinessException("该用户已经是协作者");
        }

        // 不能添加所有者为协作者
        if (userId.equals(report.getOwnerId())) {
            throw new BusinessException("不能添加报表所有者为协作者");
        }

        // 创建协作者关系
        ReportCollaborator collaborator = new ReportCollaborator();
        collaborator.setReportId(reportId);
        collaborator.setUserId(userId);
        collaborator.setRole(role);
        save(collaborator);

        log.info("添加报表协作者成功: reportId={}, userId={}, role={}", reportId, userId, role);
    }

    /**
     * 移除协作者
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeCollaborator(Long reportId, Long userId) {
        ReportCollaborator collaborator = getByReportIdAndUserId(reportId, userId);
        if (collaborator == null) {
            throw new BusinessException("协作者关系不存在");
        }

        removeById(collaborator.getId());
        log.info("移除报表协作者成功: reportId={}, userId={}", reportId, userId);
    }

    /**
     * 更新协作者角色
     *
     * @param reportId 报表ID
     * @param userId   用户ID
     * @param newRole  新角色（viewer/editor）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCollaboratorRole(Long reportId, Long userId, String newRole) {
        ReportCollaborator collaborator = getByReportIdAndUserId(reportId, userId);
        if (collaborator == null) {
            throw new BusinessException("协作者关系不存在");
        }

        collaborator.setRole(newRole);
        updateById(collaborator);

        log.info("更新协作者角色成功: reportId={}, userId={}, newRole={}", reportId, userId, newRole);
    }

    /**
     * 批量保存协作者（用于报表编辑）
     *
     * @param reportId      报表ID
     * @param collaborators 协作者列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveCollaborators(Long reportId, List<ReportCollaborator> collaborators) {
        // 删除现有的协作者关系
        LambdaQueryWrapper<ReportCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportCollaborator::getReportId, reportId);
        remove(wrapper);

        // 添加新的协作者关系
        if (collaborators != null && !collaborators.isEmpty()) {
            for (ReportCollaborator collaborator : collaborators) {
                collaborator.setReportId(reportId);
                // 验证角色有效性
                if (!"viewer".equals(collaborator.getRole()) && !"editor".equals(collaborator.getRole())) {
                    throw new BusinessException("无效的协作者角色: " + collaborator.getRole());
                }
            }
            saveBatch(collaborators);
        }

        log.info("批量保存协作者成功: reportId={}, count={}", reportId, collaborators != null ? collaborators.size() : 0);
    }

    /**
     * 执行报表图表的SQL（用于协作者执行报表）
     *
     * @param chartId 图表ID
     * @param userId  用户ID
     * @return 查询结果
     */
    public Object executeChart(Long chartId, Long userId) {
        // TODO: 实现图表执行逻辑
        // 这个方法应该调用 ReportChartService.executeChart
        // 这里需要注入 ReportChartService
        throw new BusinessException("功能开发中");
    }

    /**
     * 获取用户有权限查看的报表ID列表
     *
     * @param userId 用户ID
     * @return 报表ID列表
     */
    public List<Long> getAccessibleReportIds(Long userId) {
        // TODO: 实现获取用户可访问报表的逻辑
        // 1. 用户拥有的报表
        // 2. 公开的报表
        // 3. 用户作为协作者的报表
        throw new BusinessException("功能开发中");
    }
}
