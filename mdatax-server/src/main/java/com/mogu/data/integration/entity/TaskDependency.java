package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务依赖关系实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_dependency")
public class TaskDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long upstreamTaskId;

    private Long downstreamTaskId;

    private String dependencyType;

    private String conditionExpression;

    private Integer delaySeconds;

    private Long createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
