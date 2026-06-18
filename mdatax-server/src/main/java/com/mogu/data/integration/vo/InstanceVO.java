package com.mogu.data.integration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DAG 实例视图对象
 *
 * @author fengzhu
 */
@Data
public class InstanceVO {

    private String instanceId;

    private String dagId;

    private String dagName;

    /**
     * 状态：PENDING / RUNNING / SUCCESS / FAILURE / STOPPED / TIMEOUT
     */
    private String status;

    /**
     * 触发类型：SCHEDULED / MANUAL / EVENT / RETRY
     */
    private String triggerType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggerTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer durationMs;

    private String failureTaskId;

    private Integer retryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
