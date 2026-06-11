package com.mogu.data.schedulerx.engine.model;

import lombok.Data;

import java.util.List;

/**
 * DAG 任务节点内存模型
 *
 * @author fengzhu
 */
@Data
public class DagTask {

    private String taskId;

    private String taskName;

    private String taskType;

    private TaskConfig config;

    private List<String> upstream;

    private Integer retryTimes;

    private Integer timeoutSeconds;

}
