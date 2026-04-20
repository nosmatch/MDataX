package com.mogu.data.metadata.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表详情展示对象
 *
 * @author fengzhu
 */
@Data
public class TableDetailVO {

    private Long id;

    private String databaseName;

    private String tableName;

    private String tableComment;

    private String engine;

    private Long totalRows;

    private Long totalBytes;

    private Long ownerId;

    private String ownerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
