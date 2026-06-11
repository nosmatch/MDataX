package com.mogu.data.schedulerx.dto;

import com.mogu.data.schedulerx.enums.FailureStrategy;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 更新 DAG 请求
 *
 * @author fengzhu
 */
@Data
public class DagUpdateRequest {

    @NotBlank(message = "DAG 名称不能为空")
    private String dagName;

    private String cronExpression;

    private String timezone = "Asia/Shanghai";

    private Integer timeoutSeconds = 3600;

    private String failureStrategy = FailureStrategy.STOP_ALL.name();

    private Integer maxRetryTimes = 0;

    private Integer retryIntervalSeconds = 0;

    private String description;

    @NotEmpty(message = "任务节点列表不能为空")
    @Valid
    private List<TaskNodeRequest> tasks;

}
