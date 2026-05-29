package com.mogu.data.quality.enums;

/**
 * 告警渠道枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum AlertChannel {

    /**
     * 钉钉
     */
    DINGTALK("DINGTALK", "钉钉", "钉钉机器人告警"),

    /**
     * 企业微信
     */
    WEWORK("WEWORK", "企业微信", "企业微信机器人告警"),

    /**
     * 邮件
     */
    EMAIL("EMAIL", "邮件", "邮件告警");

    private final String code;
    private final String name;
    private final String description;

    AlertChannel(String code, String name, String description) {
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
    public static AlertChannel fromCode(String code) {
        for (AlertChannel channel : AlertChannel.values()) {
            if (channel.getCode().equals(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown alert channel code: " + code);
    }
}
