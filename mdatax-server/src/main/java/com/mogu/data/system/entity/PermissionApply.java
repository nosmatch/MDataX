package com.mogu.data.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限申请实体
 *
 * @author fengzhu
 */
@Data
@TableName("permission_apply")
public class PermissionApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicantId;

    private String applicantName;

    private Long tableId;

    private String databaseName;

    private String tableName;

    private String tableComment;

    private String applyType;

    private String applyReason;

    private Integer status;

    private Long ownerId;

    private String ownerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approveTime;

    private String approveComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
