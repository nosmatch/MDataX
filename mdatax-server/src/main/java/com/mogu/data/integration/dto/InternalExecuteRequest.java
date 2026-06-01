package com.mogu.data.integration.dto;

import lombok.Data;

/**
 * 内部任务执行回调请求 DTO
 *
 * <p>由 DolphinScheduler Worker 通过 HTTP 回调传入。
 *
 * @author fengzhu
 */
@Data
public class InternalExecuteRequest {

    /**
     * 任务类型: SYNC / SQL
     */
    private String taskType;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 回调密钥（防止未授权调用）
     */
    private String secret;

    /**
     * DS 流程实例ID（可选，用于日志关联）
     */
    private String instanceId;
}
