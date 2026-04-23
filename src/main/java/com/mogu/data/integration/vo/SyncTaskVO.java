package com.mogu.data.integration.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务展示对象
 *
 * @author fengzhu
 */
@Data
public class SyncTaskVO {

    private Long id;

    private String taskName;

    private Long datasourceId;

    private String datasourceName;

    private String sourceTable;

    private String targetTable;

    private String syncType;

    private String timeField;

    private String cronExpression;

    private Integer status;

    private Long workflowId;

    private String workflowName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
