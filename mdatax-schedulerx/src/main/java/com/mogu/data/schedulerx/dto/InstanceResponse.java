package com.mogu.data.schedulerx.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DAG 实例响应
 *
 * @author fengzhu
 */
@Data
public class InstanceResponse {

    private String instanceId;

    private String dagId;

    private String dagName;

    private String triggerType;

    private LocalDateTime triggerTime;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMs;

    private String failureTaskId;

    private Integer retryCount;

    private LocalDateTime createTime;

}
