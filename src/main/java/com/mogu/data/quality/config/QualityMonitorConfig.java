package com.mogu.data.quality.config;

import com.mogu.data.quality.constants.QualityConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 质量监控配置类
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Configuration
@ConfigurationProperties(prefix = "quality.monitor")
public class QualityMonitorConfig {

    /**
     * 检查缓存时间（分钟）
     */
    private Integer checkCacheMinutes = QualityConstants.CHECK_CACHE_MINUTES;

    /**
     * 默认告警渠道
     */
    private List<String> defaultAlertChannels = Arrays.asList(
        "DINGTALK",
        "EMAIL"
    );

    /**
     * 检查超时时间（秒）
     */
    private Integer checkTimeout = QualityConstants.DEFAULT_CHECK_TIMEOUT;

    /**
     * 最大返回行数
     */
    private Integer maxRows = QualityConstants.DEFAULT_MAX_ROWS;

    /**
     * 默认告警抑制时长（分钟）
     */
    private Integer defaultAlertSilence = QualityConstants.DEFAULT_ALERT_SILENCE;

    /**
     * 质量分数范围
     */
    private Integer minQualityScore = QualityConstants.MIN_QUALITY_SCORE;
    private Integer maxQualityScore = QualityConstants.MAX_QUALITY_SCORE;

    /**
     * 质量等级阈值
     */
    private Integer excellentMinScore = QualityConstants.EXCELLENT_MIN_SCORE;
    private Integer goodMinScore = QualityConstants.GOOD_MIN_SCORE;
    private Integer passMinScore = QualityConstants.PASS_MIN_SCORE;

    /**
     * 检查耗时阈值（毫秒）
     */
    private Integer slowCheckThreshold = QualityConstants.SLOW_CHECK_THRESHOLD;

    /**
     * 质量趋势默认天数
     */
    private Integer defaultTrendDays = QualityConstants.DEFAULT_TREND_DAYS;

    /**
     * 质量报告保留天数
     */
    private Integer reportRetentionDays = QualityConstants.REPORT_RETENTION_DAYS;

    // Getter and Setter

    public Integer getCheckCacheMinutes() {
        return checkCacheMinutes;
    }

    public void setCheckCacheMinutes(Integer checkCacheMinutes) {
        this.checkCacheMinutes = checkCacheMinutes;
    }

    public List<String> getDefaultAlertChannels() {
        return defaultAlertChannels;
    }

    public void setDefaultAlertChannels(List<String> defaultAlertChannels) {
        this.defaultAlertChannels = defaultAlertChannels;
    }

    public Integer getCheckTimeout() {
        return checkTimeout;
    }

    public void setCheckTimeout(Integer checkTimeout) {
        this.checkTimeout = checkTimeout;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }

    public Integer getDefaultAlertSilence() {
        return defaultAlertSilence;
    }

    public void setDefaultAlertSilence(Integer defaultAlertSilence) {
        this.defaultAlertSilence = defaultAlertSilence;
    }

    public Integer getMinQualityScore() {
        return minQualityScore;
    }

    public void setMinQualityScore(Integer minQualityScore) {
        this.minQualityScore = minQualityScore;
    }

    public Integer getMaxQualityScore() {
        return maxQualityScore;
    }

    public void setMaxQualityScore(Integer maxQualityScore) {
        this.maxQualityScore = maxQualityScore;
    }

    public Integer getExcellentMinScore() {
        return excellentMinScore;
    }

    public void setExcellentMinScore(Integer excellentMinScore) {
        this.excellentMinScore = excellentMinScore;
    }

    public Integer getGoodMinScore() {
        return goodMinScore;
    }

    public void setGoodMinScore(Integer goodMinScore) {
        this.goodMinScore = goodMinScore;
    }

    public Integer getPassMinScore() {
        return passMinScore;
    }

    public void setPassMinScore(Integer passMinScore) {
        this.passMinScore = passMinScore;
    }

    public Integer getSlowCheckThreshold() {
        return slowCheckThreshold;
    }

    public void setSlowCheckThreshold(Integer slowCheckThreshold) {
        this.slowCheckThreshold = slowCheckThreshold;
    }

    public Integer getDefaultTrendDays() {
        return defaultTrendDays;
    }

    public void setDefaultTrendDays(Integer defaultTrendDays) {
        this.defaultTrendDays = defaultTrendDays;
    }

    public Integer getReportRetentionDays() {
        return reportRetentionDays;
    }

    public void setReportRetentionDays(Integer reportRetentionDays) {
        this.reportRetentionDays = reportRetentionDays;
    }

    public Integer getQueryTimeout() {
        return checkTimeout;
    }
}
