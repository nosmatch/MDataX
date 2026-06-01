package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 创建告警规则请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 告警名称
     */
    @NotBlank(message = "告警名称不能为空")
    private String alertName;

    /**
     * 关联的规则ID（为空则表示全局告警）
     */
    private Long ruleId;

    /**
     * 关联的表ID（为空则表示所有表）
     */
    private Long tableId;

    /**
     * 告警条件：FAIL/WARN/SCORE_BELOW
     */
    @NotBlank(message = "告警条件不能为空")
    private String alertCondition;

    /**
     * 告警阈值（如质量分数低于60）
     */
    private Integer alertThreshold;

    /**
     * 告警渠道列表：DINGTALK/EMAIL/WEWORK
     */
    @NotNull(message = "告警渠道不能为空")
    private List<String> alertChannels;

    /**
     * 告警接收人列表
     */
    @NotNull(message = "告警接收人不能为空")
    private List<String> alertRecipients;

    /**
     * 邮件抄送列表
     */
    private List<String> alertCc;

    /**
     * 告警抑制时长（分钟）
     */
    private Integer alertSilence;

    /**
     * 是否启用告警升级
     */
    private Boolean alertUpgradeEnabled;

    /**
     * 升级接收人列表
     */
    private List<String> alertUpgradeRecipients;

    /**
     * 升级时间（小时）
     */
    private Integer alertUpgradeAfter;

    /**
     * 告警说明
     */
    private String description;
}
