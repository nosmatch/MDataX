package com.mogu.data.schedulerx.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DAG 详情响应
 *
 * @author fengzhu
 */
@Data
public class DagResponse {

    private String dagId;

    private String dagName;

    private String owner;

    private String ownerRefId;

    private String cronExpression;

    private String timezone;

    private Integer timeoutSeconds;

    private String failureStrategy;

    private Integer maxRetryTimes;

    private Integer retryIntervalSeconds;

    private Integer status;

    private String description;

    private List<TaskNodeResponse> tasks;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
