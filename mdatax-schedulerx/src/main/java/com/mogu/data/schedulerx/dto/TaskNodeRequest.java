package com.mogu.data.schedulerx.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 任务节点请求
 *
 * @author fengzhu
 */
@Data
public class TaskNodeRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    private String taskType = "HTTP_CALLBACK";

    private Map<String, Object> taskConfig;

    private List<String> upstream;

    private Integer retryTimes = 0;

    private Integer timeoutSeconds = 600;

    private Integer priority = 0;

}
