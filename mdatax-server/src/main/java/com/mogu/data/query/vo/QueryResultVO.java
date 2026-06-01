package com.mogu.data.query.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL执行结果
 *
 * @author fengzhu
 */
@Data
public class QueryResultVO {

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 数据行（每行是列名→值的映射）
     */
    private List<Map<String, Object>> rows;

    /**
     * 返回行数
     */
    private int rowCount;

    /**
     * 执行耗时（毫秒）
     */
    private long executionTime;

}
