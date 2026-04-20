package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源配置实体
 *
 * @author fengzhu
 */
@Data
@TableName("datasource")
public class Datasource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    private String host;

    private Integer port;

    private String databaseName;

    private String username;

    private String password;

    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
