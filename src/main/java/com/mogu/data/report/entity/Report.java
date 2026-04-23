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

    private String chartType;

    @TableField("x_axis_field")
    @JsonProperty("xAxisField")
    private String xAxisField;

    @TableField("y_axis_field")
    @JsonProperty("yAxisField")
    private String yAxisField;

    private String description;

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
