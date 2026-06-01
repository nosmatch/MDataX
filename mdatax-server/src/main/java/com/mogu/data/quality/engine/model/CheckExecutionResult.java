package com.mogu.data.quality.engine.model;

import com.mogu.data.quality.engine.template.model.CheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 检查执行结果
 * 封装单次检查执行的完整结果
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckExecutionResult {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 检查任务
     */
    private CheckTask task;

    /**
     * 检查结果
     */
    private CheckResult checkResult;

    /**
     * 执行SQL
     */
    private String executedSql;

    /**
     * 执行开始时间
     */
    private Date startTime;

    /**
     * 执行结束时间
     */
    private Date endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long duration;

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 错误消息（执行失败时）
     */
    private String errorMessage;

    /**
     * 异常堆栈（执行失败时）
     */
    private String errorStack;
}
