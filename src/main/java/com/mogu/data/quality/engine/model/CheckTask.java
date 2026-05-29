package com.mogu.data.quality.engine.model;

import com.mogu.data.quality.engine.template.model.ColumnMetadata;
import com.mogu.data.quality.engine.template.model.TableMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 质量检查任务
 * 封装单次质量检查的所有信息
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckTask {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则模板名称
     */
    private String templateName;

    /**
     * 表元数据
     */
    private TableMetadata table;

    /**
     * 字段元数据（字段级规则使用）
     */
    private ColumnMetadata column;

    /**
     * 规则参数
     */
    private Map<String, Object> params;

    /**
     * 检查类型（REALTIME/SCHEDULED/MANUAL）
     */
    private String checkType;

    /**
     * 触发者
     */
    private String triggeredBy;

    /**
     * 检查SQL
     */
    private String checkSql;

    /**
     * 优先级（用于并行执行时的排序）
     */
    private Integer priority;
}
