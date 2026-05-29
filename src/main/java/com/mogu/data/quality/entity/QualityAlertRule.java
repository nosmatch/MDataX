package com.mogu.data.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质量告警规则实体
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_alert_rule")
public class QualityAlertRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 告警名称
     */
    private String alertName;

    /**
     * 关联的质量规则ID（为空则表示全局告警）
     */
    private Long ruleId;

    /**
     * 关联的表ID（为空则表示所有表）
     */
    private Long tableId;

    /**
     * 告警条件：FAIL-失败/WARN-警告/SCORE_BELOW-分数低于阈值
     */
    private String alertCondition;

    /**
     * 告警阈值（如质量分数低于60）
     */
    private Integer alertThreshold;

    /**
     * 告警渠道JSON：["DINGTALK", "EMAIL", "WEWORK"]
     */
    private String alertChannels;

    /**
     * 告警接收人列表JSON：["user1", "user2"]
     */
    private String alertRecipients;

    /**
     * 邮件抄送列表JSON：["user3", "user4"]
     */
    private String alertCc;

    /**
     * 告警抑制时长（分钟），避免告警风暴
     */
    private Integer alertSilence;

    /**
     * 是否启用：0-禁用 1-启用
     */
    private Boolean alertEnabled;

    /**
     * 是否启用告警升级：0-否 1-是
     */
    private Boolean alertUpgradeEnabled;

    /**
     * 升级接收人列表JSON：["manager"]
     */
    private String alertUpgradeRecipients;

    /**
     * 升级时间（小时），如2小时后升级
     */
    private Integer alertUpgradeAfter;

    /**
     * 告警说明
     */
    private String description;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
