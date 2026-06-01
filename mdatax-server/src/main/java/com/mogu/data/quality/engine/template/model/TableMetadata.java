package com.mogu.data.quality.engine.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表元数据
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetadata {

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
     * 完整表名（database.tableName）
     */
    private String fullName;

    /**
     * 表描述
     */
    private String description;
}
