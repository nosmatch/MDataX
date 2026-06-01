package com.mogu.data.metadata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 元数据-字段实体
 *
 * @author fengzhu
 */
@Data
@TableName("metadata_column")
public class MetadataColumn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;

    private String columnName;

    private String dataType;

    private String columnComment;

    private String isNullable;

    private String columnDefault;

    private Integer ordinalPosition;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
