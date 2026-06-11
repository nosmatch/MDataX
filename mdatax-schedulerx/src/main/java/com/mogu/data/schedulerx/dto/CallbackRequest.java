package com.mogu.data.schedulerx.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 任务回调请求
 *
 * @author fengzhu
 */
@Data
public class CallbackRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    private String output;

    private String logs;

    private Integer durationMs;

}
