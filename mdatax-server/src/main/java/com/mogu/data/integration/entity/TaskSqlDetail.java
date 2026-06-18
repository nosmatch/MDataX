package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL任务详情实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_sql_detail")
public class TaskSqlDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String sqlContent;

    private Long targetDatasourceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
