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
 * SQL开发任务实体
 *
 * @author fengzhu
 */
@Data
@TableName("sql_task")
public class SqlTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private String sqlContent;

    private String description;

    private String cronExpression;

    private Integer status;

    private Long createUserId;

    private Long workflowId;

    private Long dsProcessCode;

    private Integer dsScheduleId;

    private Long dsTaskCode;

    private Integer retryTimes;

    private Integer retryInterval;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean canOperate;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
