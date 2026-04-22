package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL任务依赖关系实体（同一Workflow内）
 *
 * @author fengzhu
 */
@Data
@TableName("sql_task_dependency")
public class SqlTaskDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private Long taskId;

    private Long dependTaskId;

    private LocalDateTime createTime;
}
