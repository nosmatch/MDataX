package com.mogu.data.schedulerx.enums;

/**
 * DAG 失败策略枚举
 *
 * @author fengzhu
 */
public enum FailureStrategy {

    STOP_ALL("全部停止"),
    CONTINUE("继续执行");

    private final String label;

    FailureStrategy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
