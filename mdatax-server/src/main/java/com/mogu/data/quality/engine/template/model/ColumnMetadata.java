package com.mogu.data.quality.engine.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段元数据
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata {

    /**
     * 字段名
     */
    private String name;

    /**
     * 字段类型
     */
    private String type;

    /**
     * 字段描述
     */
    private String description;
}
