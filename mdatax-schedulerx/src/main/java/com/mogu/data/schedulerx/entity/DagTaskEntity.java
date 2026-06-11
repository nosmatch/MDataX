package com.mogu.data.schedulerx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DAG 任务节点定义实体
 *
 * @author fengzhu
 */
@Data
@TableName("sx_dag_task")
public class DagTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dagId;

    private String taskId;

    private String taskName;

    private String taskType;

    private String taskConfig;

    private String upstreamTasks;

    private Integer retryTimes;

    private Integer timeoutSeconds;

    private Integer priority;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
