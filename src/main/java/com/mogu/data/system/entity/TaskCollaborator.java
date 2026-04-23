package com.mogu.data.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务协作者实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_collaborator")
public class TaskCollaborator {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String taskType;

    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
