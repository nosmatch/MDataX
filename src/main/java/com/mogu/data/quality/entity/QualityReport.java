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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质量报告实体
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_report")
public class QualityReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的表ID
     */
    private Long tableId;

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 质量分数（0-100）
     */
    private Integer qualityScore;

    /**
     * 质量等级：EXCELLENT-优秀/GOOD-良好/PASS-及格/FAIL-不及格
     */
    private String qualityLevel;

    /**
     * 表名（非数据库字段，用于前端显示）
     */
    @TableField(exist = false)
    private String tableName;

    /**
     * 数据库（非数据库字段，用于前端显示）
     */
    @TableField(exist = false)
    private String database;

    /**
     * 检查类型：REALTIME-实时/SCHEDULED-定时/MANUAL-手动（非数据库字段）
     */
    @TableField(exist = false)
    private String checkType;

    /**
     * 触发者（非数据库字段）
     */
    @TableField(exist = false)
    private String triggeredBy;

    /**
     * 检查时间（非数据库字段）
     */
    @TableField(exist = false)
    private LocalDateTime checkTime;

    /**
     * 检查耗时（毫秒）（非数据库字段）
     */
    @TableField(exist = false)
    private Integer checkDuration;

    /**
     * 通过率（%）（非数据库字段）
     */
    @TableField(exist = false)
    private Integer passRate;

    /**
     * 总规则数
     */
    private Integer totalRules;

    /**
     * 通过规则数
     */
    private Integer passedRules;

    /**
     * 失败规则数
     */
    private Integer failedRules;

    /**
     * 警告规则数（数据库字段名为warning_rules）
     */
    @TableField("warning_rules")
    private Integer warnedRules;

    /**
     * 最后一次检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
