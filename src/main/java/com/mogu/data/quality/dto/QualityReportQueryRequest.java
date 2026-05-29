package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 质量报告查询请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityReportQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 表ID（可选，为空则查询所有表）
     */
    private Long tableId;

    /**
     * 数据库（可选）
     */
    private String database;

    /**
     * 质量等级（可选）
     */
    private String qualityLevel;

    /**
     * 开始日期（可选）
     */
    private LocalDate startDate;

    /**
     * 结束日期（可选）
     */
    private LocalDate endDate;

    /**
     * 页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方向：ASC/DESC
     */
    private String orderDirection;
}
