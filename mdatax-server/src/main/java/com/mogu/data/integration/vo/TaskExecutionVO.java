package com.mogu.data.integration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mogu.data.integration.entity.TaskExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务执行记录视图对象
 *
 * <p>在执行记录原有字段基础上，补充关联的任务名称、任务类型等信息。
 *
 * @author fengzhu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskExecutionVO extends TaskExecution {

    /**
     * 任务名称（冗余展示）
     */
    private String taskName;

    /**
     * 任务类型（SQL/SYNC/QUALITY）
     */
    private String taskType;
}
