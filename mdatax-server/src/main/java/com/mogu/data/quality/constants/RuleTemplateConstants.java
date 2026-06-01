package com.mogu.data.quality.constants;

/**
 * 规则模板常量类
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class RuleTemplateConstants {

    /**
     * 规则模板名称
     */
    public static final String NULL_CHECK = "NULL_CHECK";
    public static final String ROW_COUNT = "ROW_COUNT";
    public static final String ROW_COUNT_FLUCTUATION = "ROW_COUNT_FLUCTUATION";
    public static final String UNIQUE_CHECK = "UNIQUE_CHECK";
    public static final String ENUM_CHECK = "ENUM_CHECK";
    public static final String REGEX_CHECK = "REGEX_CHECK";
    public static final String NUMERIC_RANGE_CHECK = "NUMERIC_RANGE_CHECK";
    public static final String DATE_RANGE_CHECK = "DATE_RANGE_CHECK";
    public static final String BUSINESS_RULE = "BUSINESS_RULE";

    /**
     * 空值检查参数
     */
    public static final String PARAM_MAX_NULL_RATIO = "maxNullRatio";

    /**
     * 行数检查参数
     */
    public static final String PARAM_MIN_ROWS = "minRows";
    public static final String PARAM_MAX_ROWS = "maxRows";

    /**
     * 行数波动检查参数
     */
    public static final String PARAM_FLUCTUATION_THRESHOLD = "fluctuationThreshold";
    public static final String PARAM_BASELINE_DAYS = "baselineDays";

    /**
     * 枚举值检查参数
     */
    public static final String PARAM_ALLOWED_VALUES = "allowedValues";

    /**
     * 正则表达式检查参数
     */
    public static final String PARAM_REGEX = "regex";

    /**
     * 数值范围检查参数
     */
    public static final String PARAM_MIN_VALUE = "minValue";
    public static final String PARAM_MAX_VALUE = "maxValue";

    /**
     * 日期范围检查参数
     */
    public static final String PARAM_START_DATE = "startDate";
    public static final String PARAM_END_DATE = "endDate";

    /**
     * 业务规则检查参数
     */
    public static final String PARAM_EXPRESSION = "expression";

    /**
     * 默认参数值
     */
    public static final double DEFAULT_MAX_NULL_RATIO = 0.05; // 5%
    public static final double DEFAULT_FLUCTUATION_THRESHOLD = 0.20; // 20%
    public static final int DEFAULT_BASELINE_DAYS = 7; // 7天
    public static final int DEFAULT_ALERT_THRESHOLD = 60; // 60分

    /**
     * 检查状态
     */
    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_WARN = "WARN";

    /**
     * 检查类型
     */
    public static final String CHECK_TYPE_REALTIME = "REALTIME";
    public static final String CHECK_TYPE_SCHEDULED = "SCHEDULED";
    public static final String CHECK_TYPE_MANUAL = "MANUAL";

    /**
     * 质量等级
     */
    public static final String QUALITY_LEVEL_EXCELLENT = "EXCELLENT";
    public static final String QUALITY_LEVEL_GOOD = "GOOD";
    public static final String QUALITY_LEVEL_PASS = "PASS";
    public static final String QUALITY_LEVEL_FAIL = "FAIL";

    /**
     * 告警渠道
     */
    public static final String ALERT_CHANNEL_DINGTALK = "DINGTALK";
    public static final String ALERT_CHANNEL_WEWORK = "WEWORK";
    public static final String ALERT_CHANNEL_EMAIL = "EMAIL";

    /**
     * 告警条件
     */
    public static final String ALERT_CONDITION_FAIL = "FAIL";
    public static final String ALERT_CONDITION_WARN = "WARN";
    public static final String ALERT_CONDITION_SCORE_BELOW = "SCORE_BELOW";

    private RuleTemplateConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
