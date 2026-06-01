package com.mogu.data.metadata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表访问历史记录实体
 *
 * @author fengzhu
 */
@Data
@TableName("table_access_history")
public class TableAccessHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private Long tableId;

    private String databaseName;

    private String tableName;

    /**
     * 访问类型：READ-读，WRITE-写
     */
    private String accessType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime accessTime;

    private String ip;

}
