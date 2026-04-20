package com.mogu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 角色权限Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 查询用户拥有的所有表权限
     */
    @Select("SELECT rp.table_name, rp.permission_type " +
            "FROM sys_role_permission rp " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<RolePermission> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户可读的表名列表
     */
    @Select("SELECT DISTINCT rp.table_name " +
            "FROM sys_role_permission rp " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND rp.permission_type = 'READ'")
    Set<String> selectReadableTablesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户可写的表名列表
     */
    @Select("SELECT DISTINCT rp.table_name " +
            "FROM sys_role_permission rp " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND rp.permission_type = 'WRITE'")
    Set<String> selectWritableTablesByUserId(@Param("userId") Long userId);

}
