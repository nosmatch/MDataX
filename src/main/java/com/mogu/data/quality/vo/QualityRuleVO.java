package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质量规则视图对象
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 规则类型描述
     */
    private String ruleTypeDesc;

    /**
     * 规则模板
     */
    private String ruleTemplate;

    /**
     * 规则模板描述
     */
    private String ruleTemplateDesc;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表ID
     */
    private Long tableId;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 检查参数描述
     */
    private String checkParamsDesc;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 优先级描述
     */
    private String priorityDesc;

    /**
     * 权重
     */
    private BigDecimal weight;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 最后检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 最后检查状态
     */
    private String lastCheckStatus;

    /**
     * 最后检查状态描述
     */
    private String lastCheckStatusDesc;

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
