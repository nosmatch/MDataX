package com.mogu.data.quality.constants;

/**
 * 质量监控常量类
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class QualityConstants {

    /**
     * 质量分数范围
     */
    public static final int MIN_QUALITY_SCORE = 0;
    public static final int MAX_QUALITY_SCORE = 100;

    /**
     * 质量等级阈值
     */
    public static final int EXCELLENT_MIN_SCORE = 90;
    public static final int GOOD_MIN_SCORE = 75;
    public static final int PASS_MIN_SCORE = 60;

    /**
     * 默认规则权重
     */
    public static final double DEFAULT_RULE_WEIGHT = 1.0;

    /**
     * 默认检查超时时间（秒）
     */
    public static final int DEFAULT_CHECK_TIMEOUT = 300;

    /**
     * 默认最大返回行数
     */
    public static final int DEFAULT_MAX_ROWS = 1000;

    /**
     * 默认告警抑制时长（分钟）
     */
    public static final int DEFAULT_ALERT_SILENCE = 60;

    /**
     * 检查缓存时间（分钟）
     */
    public static final int CHECK_CACHE_MINUTES = 10;

    /**
     * 默认线程池大小
     */
    public static final int CORE_POOL_SIZE = 10;
    public static final int MAX_POOL_SIZE = 20;
    public static final int QUEUE_CAPACITY = 100;

    /**
     * 优先级
     */
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_MEDIUM = 2;
    public static final int PRIORITY_LOW = 3;

    /**
     * 质量分数扣分规则
     */
    public static final int FAIL_RULE_DEDUCTION = 10;
    public static final int WARN_RULE_DEDUCTION = 5;
    public static final int ERROR_RULE_DEDUCTION = 20;

    /**
     * 默认质量分数
     */
    public static final int DEFAULT_QUALITY_SCORE = 100;

    /**
     * 检查耗时阈值（毫秒）
     */
    public static final int SLOW_CHECK_THRESHOLD = 5000;

    /**
     * 质量趋势默认天数
     */
    public static final int DEFAULT_TREND_DAYS = 30;

    /**
     * 质量报告保留天数
     */
    public static final int REPORT_RETENTION_DAYS = 90;

    private QualityConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
