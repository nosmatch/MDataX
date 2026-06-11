package com.mogu.data.schedulerx.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实例响应
 *
 * @author fengzhu
 */
@Data
public class TaskInstanceResponse {

    private String taskInstanceId;

    private String dagInstanceId;

    private String dagId;

    private String taskId;

    private String taskName;

    private String status;

    private Integer attemptNumber;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMs;

    private String responseBody;

    private String output;

    private String errorMsg;

    private LocalDateTime createTime;

}
