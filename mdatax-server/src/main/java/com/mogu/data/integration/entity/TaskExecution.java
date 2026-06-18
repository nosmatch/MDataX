package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行记录实体（Job）
 *
 * @author fengzhu
 */
@Data
@TableName("task_execution")
public class TaskExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;

    private Long taskId;

    private String triggerType;

    private Long triggerUserId;

    private String triggerUserName;

    private String parentExecutionId;

    private String schedulerInstanceId;

    private Long workflowInstanceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String status;

    private Long durationMs;

    private Integer affectedRows;

    private Integer syncCount;

    private String logUrl;

    private String errorMsg;

    private Integer attemptNumber;

    private Integer maxAttempts;

    @com.baomidou.mybatisplus.annotation.TableLogic
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
