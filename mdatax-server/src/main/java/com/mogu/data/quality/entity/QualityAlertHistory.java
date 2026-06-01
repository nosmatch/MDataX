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
 * 质量告警历史实体
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_alert_history")
public class QualityAlertHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的告警规则ID
     */
    private Long alertRuleId;

    /**
     * 关联的表ID
     */
    private Long tableId;

    /**
     * 关联的检查结果ID
     */
    private Long checkResultId;

    /**
     * 告警时间
     */
    private LocalDateTime alertTime;

    /**
     * 告警标题
     */
    private String alertTitle;

    /**
     * 告警内容
     */
    private String alertContent;

    /**
     * 告警渠道：DINGTALK/EMAIL/WEWORK
     */
    private String alertChannel;

    /**
     * 告警状态：SENT-已发送/FAILED-发送失败/SILENCED-已抑制
     */
    private String alertStatus;

    /**
     * 发送时间
     */
    private LocalDateTime sentTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
