package com.mogu.data.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mogu.data.system.enums.PermissionApplyStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表权限申请实体
 *
 * @author fengzhu
 */
@Data
@TableName("report_permission_apply")
public class ReportPermissionApply {

    /**
     * 申请ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 报表ID
     */
    @TableField("report_id")
    @JsonProperty("reportId")
    private Long reportId;

    /**
     * 报表名称
     */
    @TableField("report_name")
    @JsonProperty("reportName")
    private String reportName;

    /**
     * 申请人用户ID
     */
    @TableField("applicant_id")
    @JsonProperty("applicantId")
    private Long applicantId;

    /**
     * 申请人姓名
     */
    @TableField("applicant_name")
    @JsonProperty("applicantName")
    private String applicantName;

    /**
     * 报表所有者ID（审批人）
     */
    @TableField("owner_id")
    @JsonProperty("ownerId")
    private Long ownerId;

    /**
     * 所有者姓名
     */
    @TableField("owner_name")
    @JsonProperty("ownerName")
    private String ownerName;

    /**
     * 申请角色：viewer-查看者, editor-编辑者
     */
    @TableField("apply_role")
    @JsonProperty("applyRole")
    private String applyRole;

    /**
     * 申请理由
     */
    @TableField("apply_reason")
    @JsonProperty("applyReason")
    private String applyReason;

    /**
     * 状态：0-待审批 1-已通过 2-已拒绝
     */
    @JsonProperty("status")
    private Integer status;

    /**
     * 审批时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("approveTime")
    private LocalDateTime approveTime;

    /**
     * 审批意见
     */
    @TableField("approve_comment")
    @JsonProperty("approveComment")
    private String approveComment;

    /**
     * 申请时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updateTime")
    private LocalDateTime updateTime;

    /**
     * 获取状态枚举
     */
    public PermissionApplyStatus getStatusEnum() {
        return PermissionApplyStatus.of(this.status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(PermissionApplyStatus statusEnum) {
        this.status = statusEnum != null ? statusEnum.getCode() : null;
    }
}
