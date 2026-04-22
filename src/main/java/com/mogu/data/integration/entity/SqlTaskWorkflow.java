package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL任务工作流（DAG）实体
 *
 * @author fengzhu
 */
@Data
@TableName("sql_task_workflow")
public class SqlTaskWorkflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowName;

    private String description;

    private String cronExpression;

    private Integer status;

    private Long dsProcessCode;

    private Integer dsScheduleId;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
