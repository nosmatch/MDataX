package com.mogu.data.quality.enums;

/**
 * 规则模板枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum RuleTemplate {

    /**
     * 空值检查
     */
    NULL_CHECK("NULL_CHECK", "空值检查", "检查字段空值比例"),

    /**
     * 行数检查
     */
    ROW_COUNT("ROW_COUNT", "行数检查", "检查表行数是否在范围内"),

    /**
     * 行数波动检查
     */
    ROW_COUNT_FLUCTUATION("ROW_COUNT_FLUCTUATION", "行数波动检查", "检查表行数相对于历史数据的波动"),

    /**
     * 唯一值检查
     */
    UNIQUE_CHECK("UNIQUE_CHECK", "唯一值检查", "检查字段值是否唯一"),

    /**
     * 枚举值检查
     */
    ENUM_CHECK("ENUM_CHECK", "枚举值检查", "检查字段值是否在枚举范围内"),

    /**
     * 正则表达式检查
     */
    REGEX_CHECK("REGEX_CHECK", "正则表达式检查", "检查字段值是否符合正则表达式"),

    /**
     * 数值范围检查
     */
    NUMERIC_RANGE_CHECK("NUMERIC_RANGE_CHECK", "数值范围检查", "检查数值字段是否在范围内"),

    /**
     * 日期范围检查
     */
    DATE_RANGE_CHECK("DATE_RANGE_CHECK", "日期范围检查", "检查日期字段是否在范围内"),

    /**
     * 业务规则检查
     */
    BUSINESS_RULE("BUSINESS_RULE", "业务规则检查", "自定义业务规则检查");

    private final String code;
    private final String name;
    private final String description;

    RuleTemplate(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static RuleTemplate fromCode(String code) {
        for (RuleTemplate template : RuleTemplate.values()) {
            if (template.getCode().equals(code)) {
                return template;
            }
        }
        throw new IllegalArgumentException("Unknown rule template code: " + code);
    }
}
