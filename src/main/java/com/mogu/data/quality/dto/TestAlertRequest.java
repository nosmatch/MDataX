package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 测试告警请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestAlertRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 告警规则ID
     */
    @NotNull(message = "告警规则ID不能为空")
    private Long alertRuleId;

    /**
     * 告警渠道：DINGTALK/EMAIL/WEWORK
     */
    private String alertChannel;

    /**
     * 测试消息内容（可选，为空则使用默认内容）
     */
    private String testMessage;
}
