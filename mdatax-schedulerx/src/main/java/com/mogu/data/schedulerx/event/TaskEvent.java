package com.mogu.data.schedulerx.event;

import com.mogu.data.schedulerx.enums.EventType;
import lombok.Data;

/**
 * 任务事件
 *
 * @author fengzhu
 */
@Data
public class TaskEvent {

    private String taskInstanceId;

    private String dagInstanceId;

    private EventType type;

    private String output;

    private String errorMsg;

    private Integer durationMs;

    public TaskEvent() {
    }

    public TaskEvent(String taskInstanceId, String dagInstanceId, EventType type) {
        this.taskInstanceId = taskInstanceId;
        this.dagInstanceId = dagInstanceId;
        this.type = type;
    }

    public TaskEvent(String taskInstanceId, String dagInstanceId, EventType type, String output) {
        this.taskInstanceId = taskInstanceId;
        this.dagInstanceId = dagInstanceId;
        this.type = type;
        this.output = output;
    }

}
