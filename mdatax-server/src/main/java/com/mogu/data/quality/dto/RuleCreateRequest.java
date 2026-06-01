package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 创建质量规则请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /**
     * 规则类型：TABLE/COLUMN
     */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /**
     * 规则模板
     */
    @NotBlank(message = "规则模板不能为空")
    private String ruleTemplate;

    /**
     * 关联的表ID（可选，如果不关联metadata_table表）
     */
    private Long tableId;

    /**
     * 表名
     */
    @NotBlank(message = "表名不能为空")
    private String tableName;

    /**
     * 数据库名
     */
    @NotBlank(message = "数据库名不能为空")
    private String database;

    /**
     * 字段名（字段级规则必填）
     */
    private String columnName;

    /**
     * 检查参数（Map格式）
     */
    private Map<String, Object> ruleParams;

    /**
     * 优先级：1-高 2-中 3-低
     */
    private Integer priority;

    /**
     * 权重（用于计算质量分数，0-2）
     */
    private BigDecimal weight;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 检查触发方式：REALTIME/SCHEDULED/MANUAL
     */
    private List<String> checkModes;

    /**
     * 定时检查的Cron表达式
     */
    private String scheduleCron;

    /**
     * 失败时是否告警
     */
    private Boolean alertOnFailure;

    /**
     * 规则说明
     */
    private String description;
}
