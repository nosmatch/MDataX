package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 异常处理请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyHandleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 异常ID
     */
    @NotNull(message = "异常ID不能为空")
    private Long anomalyId;

    /**
     * 处理动作：HANDLE/IGNORE/RESOLVE
     */
    @NotBlank(message = "处理动作不能为空")
    private String action;

    /**
     * 影响范围
     */
    private String impactScope;

    /**
     * 处理备注
     */
    private String handleRemark;
}
