package com.mogu.data.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表定义实体
 * <p>
 * 注意：为了向后兼容，保留了 chartType、xAxisField、yAxisField 字段。
 * 新版报表支持多图表，请使用 report_chart 表存储图表配置。
 * 单图表报表会自动将 report 表中的配置同步到 report_chart 表。
 *
 * @author fengzhu
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("sql_content")
    @JsonProperty("sqlContent")
    private String sqlContent;

    /**
     * @deprecated 单图表模式字段，保留向后兼容。多图表请使用 report_chart 表
     */
    @Deprecated
    private String chartType;

    /**
     * @deprecated 单图表模式字段，保留向后兼容。多图表请使用 report_chart 表
     */
    @Deprecated
    @TableField("x_axis_field")
    @JsonProperty("xAxisField")
    private String xAxisField;

    /**
     * @deprecated 单图表模式字段，保留向后兼容。多图表请使用 report_chart 表
     */
    @Deprecated
    @TableField("y_axis_field")
    @JsonProperty("yAxisField")
    private String yAxisField;

    private String description;

    /**
     * 可见性：private-私有, public-公开
     */
    private String visibility;

    /**
     * 报表所有者ID
     */
    @TableField("owner_id")
    @JsonProperty("ownerId")
    private Long ownerId;

    private Integer status;

    @TableField("create_user_id")
    private Long createUserId;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
