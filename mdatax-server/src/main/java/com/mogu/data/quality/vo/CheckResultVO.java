package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检查结果视图对象
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 检查结果ID
     */
    private Long resultId;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型
     */
    private String ruleType;

    /**
     * 规则模板
     */
    private String ruleTemplate;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 检查类型
     */
    private String checkType;

    /**
     * 检查类型描述
     */
    private String checkTypeDesc;

    /**
     * 检查时间
     */
    private LocalDateTime checkTime;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 状态图标
     */
    private String statusIcon;

    /**
     * 实际值
     */
    private String actualValue;

    /**
     * 期望值
     */
    private String expectedValue;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 检查耗时（毫秒）
     */
    private Integer checkDuration;

    /**
     * 检查耗时描述（如：1.2秒）
     */
    private String checkDurationDesc;

    /**
     * 触发人/任务ID
     */
    private String triggeredBy;

    /**
     * 触发来源描述
     */
    private String triggeredByDesc;
}
