package com.mogu.data.schedulerx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行实例实体
 *
 * @author fengzhu
 */
@Data
@TableName("sx_task_instance")
public class TaskInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    private Integer timeoutSeconds;

    private String callbackUrl;

    private String requestBody;

    private String responseBody;

    private String output;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
