package com.mogu.data.quality.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 质量大盘视图对象
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 整体质量概况
     */
    private OverviewVO overview;

    /**
     * 质量趋势列表
     */
    private List<TrendVO> trendList;

    /**
     * 质量分布
     */
    private DistributionVO distribution;

    /**
     * 质量TOP榜
     */
    private List<TopTableVO> topTables;

    /**
     * 最近异常列表
     */
    private List<AnomalyVO> recentAnomalies;

    /**
     * 整体质量概况视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 总表数
         */
        private Long totalTables;

        /**
         * 优秀表数量
         */
        private Long excellentTables;

        /**
         * 良好表数量
         */
        private Long goodTables;

        /**
         * 及格表数量
         */
        private Long passTables;

        /**
         * 不及格表数量
         */
        private Long failTables;

        /**
         * 平均质量分数
         */
        private Integer avgQualityScore;

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
         * 今日检查次数
         */
        private Integer checkToday;
    }

    /**
     * 质量趋势视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendVO implements Serializable {

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
         * 检查次数
         */
        private Integer checkCount;
    }

    /**
     * 质量分布视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 优秀表数量
         */
        private Integer excellent;

        /**
         * 良好表数量
         */
        private Integer good;

        /**
         * 及格表数量
         */
        private Integer pass;

        /**
         * 不及格表数量
         */
        private Integer fail;
    }

    /**
     * 质量TOP榜视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTableVO implements Serializable {

        private static final long serialVersionUID = 1L;

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
         * 最后检查时间
         */
        private LocalDateTime checkTime;
    }

    /**
     * 异常视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyVO implements Serializable {

        private static final long serialVersionUID = 1L;

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
         * 质量分数
         */
        private Integer qualityScore;

        /**
         * 质量等级
         */
        private String qualityLevel;

        /**
         * 失败规则数
         */
        private Integer failedRules;

        /**
         * 最后检查时间
         */
        private LocalDateTime checkTime;

        /**
         * 检查类型
         */
        private String checkType;
    }
}
