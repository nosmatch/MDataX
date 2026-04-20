package com.mogu.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限实体（表级权限）
 *
 * @author fengzhu
 */
@Data
@TableName("sys_role_permission")
public class RolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private String tableName;

    private String permissionType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
