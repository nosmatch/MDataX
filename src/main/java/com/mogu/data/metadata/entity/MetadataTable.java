package com.mogu.data.metadata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 元数据-表实体
 *
 * @author fengzhu
 */
@Data
@TableName("metadata_table")
public class MetadataTable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String databaseName;

    private String tableName;

    private String tableComment;

    private String engine;

    private Long totalRows;

    private Long totalBytes;

    private Long ownerId;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
