package com.mogu.data.schedulerx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DAG 执行实例实体
 *
 * @author fengzhu
 */
@Data
@TableName("sx_dag_instance")
public class DagInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String instanceId;

    private String dagId;

    private String triggerType;

    private LocalDateTime triggerTime;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMs;

    private String context;

    private String failureTaskId;

    private Integer retryCount;

    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
