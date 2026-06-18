package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务标签关联实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_tag_relation")
public class TaskTagRelation {

    private Long taskId;

    private String tagName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
