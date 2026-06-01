package com.mogu.data.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 更新告警规则请求
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 告警规则ID
     */
    @NotNull(message = "告警规则ID不能为空")
    private Long alertRuleId;

    /**
     * 告警名称
     */
    private String alertName;

    /**
     * 告警条件
     */
    private String alertCondition;

    /**
     * 告警阈值
     */
    private Integer alertThreshold;

    /**
     * 告警渠道列表
     */
    private List<String> alertChannels;

    /**
     * 告警接收人列表
     */
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
     * 是否启用
     */
    private Boolean alertEnabled;

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
