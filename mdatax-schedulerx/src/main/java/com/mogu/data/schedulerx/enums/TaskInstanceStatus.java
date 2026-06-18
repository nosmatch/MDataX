package com.mogu.data.schedulerx.enums;

/**
 * 任务实例状态枚举
 *
 * @author fengzhu
 */
public enum TaskInstanceStatus {

    PENDING("待执行"),
    WAITING_UPSTREAM("等待上游"),
    RUNNING("运行中"),
    SUCCESS("成功"),
    FAILURE("失败"),
    TIMEOUT("超时"),
    KILLED("已终止"),
    SKIPPED("已跳过");

    private final String label;

    TaskInstanceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
