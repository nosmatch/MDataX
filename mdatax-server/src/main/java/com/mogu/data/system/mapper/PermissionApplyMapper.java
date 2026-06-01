package com.mogu.data.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.system.entity.PermissionApply;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限申请 Mapper
 *
 * @author fengzhu
 */
public interface PermissionApplyMapper extends BaseMapper<PermissionApply> {

    /**
     * 查询用户指定表和类型的待审批申请
     */
    @Select("SELECT * FROM permission_apply WHERE applicant_id = #{userId} " +
            "AND database_name = #{databaseName} AND table_name = #{tableName} " +
            "AND apply_type = #{applyType} AND status = 0 LIMIT 1")
    PermissionApply selectPendingApply(@Param("userId") Long userId,
                                        @Param("databaseName") String databaseName,
                                        @Param("tableName") String tableName,
                                        @Param("applyType") String applyType);

    /**
     * 查询用户的所有待审批申请
     */
    @Select("SELECT * FROM permission_apply WHERE applicant_id = #{userId} AND status = 0")
    List<PermissionApply> selectPendingAppliesByUserId(@Param("userId") Long userId);
}
