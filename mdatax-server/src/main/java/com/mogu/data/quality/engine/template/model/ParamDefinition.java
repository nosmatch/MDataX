package com.mogu.data.quality.engine.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数定义
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamDefinition {

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数类型
     */
    private String type;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 是否必填
     */
    private boolean required;

    /**
     * 默认值
     */
    private Object defaultValue;

    /**
     * 示例值
     */
    private String example;
}
