package com.mogu.data.quality.enums;

/**
 * 检查状态枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum CheckStatus {

    /**
     * 通过
     */
    PASS("PASS", "通过", "质量检查通过"),

    /**
     * 失败
     */
    FAIL("FAIL", "失败", "质量检查失败"),

    /**
     * 警告
     */
    WARN("WARN", "警告", "质量检查警告");

    private final String code;
    private final String name;
    private final String description;

    CheckStatus(String code, String name, String description) {
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
    public static CheckStatus fromCode(String code) {
        for (CheckStatus status : CheckStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown check status code: " + code);
    }
}
