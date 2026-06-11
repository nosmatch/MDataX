package com.mogu.data.schedulerx.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务节点响应
 *
 * @author fengzhu
 */
@Data
public class TaskNodeResponse {

    private String taskId;

    private String taskName;

    private String taskType;

    private Map<String, Object> taskConfig;

    private List<String> upstream;

    private Integer retryTimes;

    private Integer timeoutSeconds;

    private Integer priority;

}
