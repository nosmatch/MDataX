package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 手动检查请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 表ID
     */
    @NotNull(message = "表ID不能为空")
    private Long tableId;

    /**
     * 规则ID列表（可选，为空则检查该表的所有规则）
     */
    private List<Long> ruleIds;

    /**
     * 检查类型
     */
    private String checkType;

    /**
     * 是否异步执行
     */
    private Boolean async;
}
