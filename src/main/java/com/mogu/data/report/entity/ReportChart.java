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
 * 报表图表配置实体
 *
 * @author fengzhu
 */
@Data
@TableName("report_chart")
public class ReportChart {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("report_id")
    @JsonProperty("reportId")
    private Long reportId;

    @TableField("chart_type")
    @JsonProperty("chartType")
    private String chartType;

    private String title;

    @TableField("chart_description")
    @JsonProperty("chartDescription")
    private String chartDescription;

    @TableField("sql_content")
    @JsonProperty("sqlContent")
    private String sqlContent;

    @TableField("x_axis_field")
    @JsonProperty("xAxisField")
    private String xAxisField;

    @TableField("x_axis_label")
    @JsonProperty("xAxisLabel")
    private String xAxisLabel;

    @TableField("y_axis_field")
    @JsonProperty("yAxisField")
    private String yAxisField;

    @TableField("y_axis_label")
    @JsonProperty("yAxisLabel")
    private String yAxisLabel;

    @TableField("sort_order")
    @JsonProperty("sortOrder")
    private Integer sortOrder;

    @TableField("layout_span")
    @JsonProperty("layoutSpan")
    private Integer layoutSpan;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
