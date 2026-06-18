package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务详情实体
 *
 * @author fengzhu
 */
@Data
@TableName("task_sync_detail")
public class TaskSyncDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long sourceDatasourceId;

    private String sourceTable;

    private Long targetDatasourceId;

    private String targetTable;

    private String syncType;

    private String timeField;

    private String whereCondition;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
