package com.mogu.data.query.vo;

import lombok.Builder;
import lombok.Data;

/**
 * SQL执行选项
 *
 * @author fengzhu
 */
@Data
@Builder
public class ExecuteOptions {

    /**
     * 是否只读（仅允许 SELECT）
     */
    @Builder.Default
    private boolean readonly = true;

    /**
     * 最大返回行数（SELECT 时生效，0 表示不限制）
     */
    @Builder.Default
    private int maxRows = 100;

    /**
     * 执行超时秒数（0 表示不限制）
     */
    @Builder.Default
    private int timeoutSeconds = 5;

    /**
     * 目标表（任务写入时使用）
     */
    private String targetTable;

}
