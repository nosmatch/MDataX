package com.mogu.data.integration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实例视图对象
 *
 * @author fengzhu
 */
@Data
public class TaskInstanceVO {

    private String taskInstanceId;

    private String dagInstanceId;

    private String dagId;

    private String taskId;

    private String taskName;

    /**
     * 状态：PENDING / WAITING_UPSTREAM / RUNNING / SUCCESS / FAILURE / TIMEOUT / SKIPPED
     */
    private String status;

    private Integer attemptNumber;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer durationMs;

    private String responseBody;

    private String output;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
