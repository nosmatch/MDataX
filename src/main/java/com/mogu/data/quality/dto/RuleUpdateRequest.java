package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 更新质量规则请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型：TABLE/COLUMN
     */
    private String ruleType;

    /**
     * 规则模板
     */
    private String ruleTemplate;

    /**
     * 数据库名
     */
    private String database;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段名
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
