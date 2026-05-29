package com.mogu.data.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 质量规则实体
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_rule")
public class QualityRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型：TABLE-表级/COLUMN-字段级
     */
    private String ruleType;

    /**
     * 规则模板：NULL_CHECK/ROW_COUNT/UNIQUE_CHECK/ENUM_CHECK/REGEX_CHECK/NUMERIC_RANGE_CHECK/DATE_RANGE_CHECK/BUSINESS_RULE
     */
    private String ruleTemplate;

    /**
     * 关联的表ID（关联metadata_table表）
     */
    private Long tableId;

    /**
     * 表名（冗余字段，便于查询）
     */
    private String tableName;

    /**
     * 数据库名（冗余字段，便于查询）
     */
    @com.baomidou.mybatisplus.annotation.TableField("`database`")
    private String database;

    /**
     * 字段名（字段级规则必填）
     */
    private String columnName;

    /**
     * 检查SQL（自定义规则使用）
     */
    private String checkSql;

    /**
     * 检查参数JSON（仅用于数据库存储，不返回给前端）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String checkParams;

    /**
     * 规则参数对象（用于前端展示，不存储到数据库）
     */
    @com.fasterxml.jackson.annotation.JsonProperty("ruleParams")
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Map<String, Object> ruleParamsMap;

    /**
     * 优先级：1-高 2-中 3-低
     */
    private Integer priority;

    /**
     * 权重（用于计算质量分数，0-2）
     */
    private BigDecimal weight;

    /**
     * 是否启用：0-禁用 1-启用
     */
    private Boolean enabled;

    /**
     * 检查触发方式：REALTIME-实时/SCHEDULED-定时/MANUAL-手动，多个用逗号分隔
     */
    private String checkMode;

    /**
     * 定时检查的Cron表达式
     */
    private String scheduleCron;

    /**
     * 失败时是否告警：0-否 1-是
     */
    private Boolean alertOnFailure;

    /**
     * 规则说明
     */
    private String description;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
