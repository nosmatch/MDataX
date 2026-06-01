package com.mogu.data.quality.engine.model;

import com.mogu.data.quality.engine.template.model.TableMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 质量检查上下文
 * 封装一次质量检查过程的完整上下文信息
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckContext {

    /**
     * 检查ID（唯一标识一次检查）
     */
    private String checkId;

    /**
     * 表ID
     */
    private Long tableId;

    /**
     * 表元数据
     */
    private TableMetadata table;

    /**
     * 检查类型（REALTIME/SCHEDULED/MANUAL）
     */
    private String checkType;

    /**
     * 触发者
     */
    private String triggeredBy;

    /**
     * 检查开始时间
     */
    private Date startTime;

    /**
     * 检查结束时间
     */
    private Date endTime;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDuration;

    /**
     * 所有检查任务
     */
    private List<CheckTask> tasks;

    /**
     * 所有检查执行结果
     */
    private List<CheckExecutionResult> results;

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
     * 错误规则数
     */
    private Integer errorRules;

    /**
     * 质量分数（0-100）
     */
    private Integer qualityScore;

    /**
     * 质量等级（EXCELLENT/GOOD/PASS/FAIL）
     */
    private String qualityLevel;
}
