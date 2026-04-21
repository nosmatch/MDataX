package com.mogu.data.query.vo;

import com.mogu.data.metadata.entity.MetadataColumn;
import lombok.Data;

import java.util.List;

/**
 * 表信息 VO（含字段列表）
 *
 * @author fengzhu
 */
@Data
public class TableInfoVO {

    /**
     * 表 ID
     */
    private Long id;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;

    /**
     * 字段列表
     */
    private List<MetadataColumn> columns;

}
