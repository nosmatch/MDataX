package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质量监控任务详情实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_quality_detail")
public class TaskQualityDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /**
     * 规则模板：NULL_CHECK/ROW_COUNT_CHECK/ROW_COUNT_FLUCTUATION/UNIQUE_CHECK/ENUM_CHECK/REGEX_CHECK/NUMERIC_RANGE_CHECK/DATE_RANGE_CHECK/BUSINESS_RULE
     */
    private String ruleTemplate;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 关联的表ID（metadata_table表）
     */
    private Long tableId;

    /**
     * 字段名（字段级规则）
     */
    private String columnName;

    /**
     * 检查参数JSON
     */
    private String checkParams;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
