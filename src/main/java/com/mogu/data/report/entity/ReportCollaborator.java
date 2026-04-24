package com.mogu.data.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表协作者实体
 *
 * @author fengzhu
 */
@Data
@TableName("report_collaborator")
public class ReportCollaborator {

    /**
     * 协作者ID
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
     * 协作者用户ID
     */
    @TableField("user_id")
    @JsonProperty("userId")
    private Long userId;

    /**
     * 角色：viewer-查看者, editor-编辑者
     */
    private String role;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
