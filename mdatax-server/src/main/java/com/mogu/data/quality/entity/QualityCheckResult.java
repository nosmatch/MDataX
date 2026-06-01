package com.mogu.data.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质量检查结果实体
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_check_result")
public class QualityCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的规则ID
     */
    private Long ruleId;

    /**
     * 检查类型：REALTIME-实时/SCHEDULED-定时/MANUAL-手动
     */
    private String checkType;

    /**
     * 检查时间
     */
    private LocalDateTime checkTime;

    /**
     * 状态：PASS-通过/FAIL-失败/WARN-警告
     */
    private String status;

    /**
     * 实际值
     */
    private String actualValue;

    /**
     * 期望值
     */
    private String expectedValue;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 检查耗时（毫秒）
     */
    private Integer checkDuration;

    /**
     * 触发人
     */
    private String triggeredBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    // ========== 非数据库字段，用于前端展示 ==========

    /**
     * 规则名称（非数据库字段）
     */
    @TableField(exist = false)
    private String ruleName;

    /**
     * 关联的报告ID（非数据库字段，用于查询）
     */
    @TableField(exist = false)
    private Long reportId;
}
