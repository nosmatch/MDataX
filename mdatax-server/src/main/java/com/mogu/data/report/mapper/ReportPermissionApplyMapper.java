package com.mogu.data.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.report.entity.ReportPermissionApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表权限申请 Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface ReportPermissionApplyMapper extends BaseMapper<ReportPermissionApply> {

    /**
     * 查询用户指定报表和角色的待审批申请
     */
    @Select("SELECT * FROM report_permission_apply WHERE applicant_id = #{applicantId} " +
            "AND report_id = #{reportId} AND apply_role = #{applyRole} AND status = 0 LIMIT 1")
    ReportPermissionApply selectPendingApply(@Param("applicantId") Long applicantId,
                                             @Param("reportId") Long reportId,
                                             @Param("applyRole") String applyRole);

    /**
     * 查询用户的所有待审批申请
     */
    @Select("SELECT * FROM report_permission_apply WHERE applicant_id = #{applicantId} AND status = 0")
    List<ReportPermissionApply> selectPendingAppliesByApplicantId(@Param("applicantId") Long applicantId);
}
