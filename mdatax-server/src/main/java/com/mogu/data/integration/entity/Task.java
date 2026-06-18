package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一任务实体
 *
 * @author fengzhu
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskCode;

    private String taskName;

    private String taskType;

    private String description;

    private Long ownerUserId;

    private String ownerUserName;

    private Long createUserId;

    private Integer priority;

    private String tags;

    private String cronExpression;

    private Integer status;

    private Long workflowId;

    private Long dsProcessCode;

    private Integer dsScheduleId;

    private Long dsTaskCode;

    private String schedulerxDagId;

    private Integer retryTimes;

    private Integer retryInterval;

    private Integer timeoutSeconds;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // 最近一次执行状态（非数据库字段，用于列表展示）
    @TableField(exist = false)
    private String lastExecutionStatus;

    // 最近一次执行时间（非数据库字段，用于列表展示）
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastExecutionTime;

    // 最近一次执行ID（非数据库字段，用于列表展示）
    @TableField(exist = false)
    private String lastExecutionId;
}
