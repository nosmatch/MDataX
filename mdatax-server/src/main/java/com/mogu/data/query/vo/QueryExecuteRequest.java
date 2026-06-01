package com.mogu.data.query.vo;

import lombok.Data;

/**
 * SQL执行请求
 *
 * @author fengzhu
 */
@Data
public class QueryExecuteRequest {

    private String sql;

    /**
     * 是否只读模式（仅允许 SELECT），默认真。
     * SQL 开发页面传 false 以支持 DDL/DML。
     */
    private boolean readonly = true;

}
