package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常视图对象
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 异常ID
     */
    private Long anomalyId;

    /**
     * 表ID
     */
    private Long tableId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 数据库
     */
    private String database;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型
     */
    private String ruleType;

    /**
     * 规则模板
     */
    private String ruleTemplate;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 异常信息
     */
    private String anomalyMessage;

    /**
     * 异常详情
     */
    private String anomalyDetail;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 严重程度
     */
    private String severity;

    /**
     * 严重程度描述
     */
    private String severityDesc;

    /**
     * 影响范围
     */
    private String impactScope;

    /**
     * 发现时间
     */
    private LocalDateTime foundTime;

    /**
     * 处理人
     */
    private String handler;

    /**
     * 处理时间
     */
    private LocalDateTime handledTime;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 处理记录列表
     */
    private List<HandleRecordVO> handleRecords;

    /**
     * 检查结果信息
     */
    private CheckResultInfo checkResult;

    /**
     * 处理记录视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandleRecordVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 记录ID
         */
        private Long recordId;

        /**
         * 处理人
         */
        private String handler;

        /**
         * 处理时间
         */
        private LocalDateTime handleTime;

        /**
         * 处理动作
         */
        private String action;

        /**
         * 处理备注
         */
        private String remark;
    }

    /**
     * 检查结果信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResultInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 检查结果ID
         */
        private Long checkResultId;

        /**
         * 检查时间
         */
        private LocalDateTime checkTime;

        /**
         * 实际值
         */
        private String actualValue;

        /**
         * 期望值
         */
        private String expectedValue;

        /**
         * 差值
         */
        private String diffValue;

        /**
         * 检查SQL
         */
        private String checkSql;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 检查耗时
         */
        private Integer checkDuration;
    }
}
