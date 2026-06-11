package com.mogu.data.schedulerx.engine.model;

import lombok.Data;

import java.util.Map;

/**
 * 任务执行配置
 *
 * @author fengzhu
 */
@Data
public class TaskConfig {

    private String url;

    private String method;

    private Map<String, String> headers;

    private Map<String, Object> body;

    private Integer timeout;

}
