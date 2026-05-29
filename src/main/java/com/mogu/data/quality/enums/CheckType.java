package com.mogu.data.quality.enums;

/**
 * 检查类型枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum CheckType {

    /**
     * 实时检查（SQL任务执行后自动触发）
     */
    REALTIME("REALTIME", "实时检查"),

    /**
     * 定时检查（按Cron表达式定时触发）
     */
    SCHEDULED("SCHEDULED", "定时检查"),

    /**
     * 手动检查（用户主动触发）
     */
    MANUAL("MANUAL", "手动检查");

    private final String code;
    private final String description;

    CheckType(String code, String description) {
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
    public static CheckType fromCode(String code) {
        for (CheckType type : CheckType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown check type code: " + code);
    }
}
