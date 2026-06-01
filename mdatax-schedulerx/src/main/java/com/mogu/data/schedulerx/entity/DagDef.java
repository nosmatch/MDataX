package com.mogu.data.schedulerx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DAG 定义实体
 *
 * @author fengzhu
 */
@Data
@TableName("sx_dag_def")
public class DagDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dagId;

    private String dagName;

    private String owner;

    private String ownerRefId;

    private String cronExpression;

    private String timezone;

    private Integer timeoutSeconds;

    private String failureStrategy;

    private Integer maxRetryTimes;

    private Integer retryIntervalSeconds;

    private Integer status;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
