package com.mogu.data.quality.exception;

import lombok.Getter;

/**
 * 质量检查异常
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Getter
public class QualityCheckException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 错误消息
     */
    private final String errorMessage;

    /**
     * 构造函数
     *
     * @param errorMessage 错误消息
     */
    public QualityCheckException(String errorMessage) {
        super(errorMessage);
        this.errorCode = "QUALITY_CHECK_ERROR";
        this.errorMessage = errorMessage;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    public QualityCheckException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 构造函数
     *
     * @param errorMessage 错误消息
     * @param cause 原因
     */
    public QualityCheckException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = "QUALITY_CHECK_ERROR";
        this.errorMessage = errorMessage;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param cause 原因
     */
    public QualityCheckException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getMessage() {
        return String.format("[%s] %s", errorCode, errorMessage);
    }
}
