package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 质量报告视图对象
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告ID
     */
    private Long id;

    /**
     * 表ID
     */
    private Long tableId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 数据库
     */
    private String database;

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 质量分数
     */
    private Integer qualityScore;

    /**
     * 质量等级
     */
    private String qualityLevel;

    /**
     * 质量状态
     */
    private String qualityStatus;

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
     * 警告规则数
     */
    private Integer warnedRules;

    /**
     * 通过率
     */
    private Integer passRate;

    /**
     * 检查时间
     */
    private LocalDateTime checkTime;

    /**
     * 检查耗时（毫秒）
     */
    private Integer checkDuration;

    /**
     * 检查类型
     */
    private String checkType;

    /**
     * 触发者
     */
    private String triggeredBy;
}
