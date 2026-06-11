package com.mogu.data.schedulerx.enums;

/**
 * DAG 实例状态枚举
 *
 * @author fengzhu
 */
public enum DagInstanceStatus {

    PENDING("待执行"),
    RUNNING("运行中"),
    SUCCESS("成功"),
    FAILURE("失败"),
    STOPPED("已停止"),
    TIMEOUT("超时");

    private final String label;

    DagInstanceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
