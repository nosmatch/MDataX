package com.mogu.data.metadata.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据目录-表列表展示对象
 *
 * @author fengzhu
 */
@Data
public class TablePageVO {

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

    /** 数据最近更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastDataUpdateTime;

    private boolean read;

    private boolean write;

}
