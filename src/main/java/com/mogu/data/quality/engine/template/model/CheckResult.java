package com.mogu.data.quality.engine.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查结果
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {

    /**
     * 检查状态
     */
    private String status;

    /**
     * 实际值
     */
    private String actualValue;

    /**
     * 期望值
     */
    private String expectedValue;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 检查耗时（毫秒）
     */
    private Integer checkDuration;
}
