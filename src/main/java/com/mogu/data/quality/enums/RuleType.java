package com.mogu.data.quality.enums;

/**
 * 规则类型枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum RuleType {

    /**
     * 表级规则
     */
    TABLE("TABLE", "表级"),

    /**
     * 字段级规则
     */
    COLUMN("COLUMN", "字段级");

    private final String code;
    private final String description;

    RuleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static RuleType fromCode(String code) {
        for (RuleType type : RuleType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown rule type code: " + code);
    }
}
