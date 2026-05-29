package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 质量趋势视图对象
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日期（字符串格式）
     */
    private String date;

    /**
     * 质量分数
     */
    private Integer qualityScore;

    /**
     * 质量等级
     */
    private String qualityLevel;

    /**
     * 通过率
     */
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
}
