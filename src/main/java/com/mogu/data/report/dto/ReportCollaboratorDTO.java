package com.mogu.data.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表协作者DTO（包含用户信息）
 *
 * @author fengzhu
 */
@Data
public class ReportCollaboratorDTO {

    /**
     * 协作者ID
     */
    @JsonProperty("id")
    private Long id;

    /**
     * 报表ID
     */
    @JsonProperty("reportId")
    private Long reportId;

    /**
     * 协作者用户ID
     */
    @JsonProperty("userId")
    private Long userId;

    /**
     * 用户名
     */
    @JsonProperty("username")
    private String username;

    /**
     * 昵称
     */
    @JsonProperty("nickname")
    private String nickname;

    /**
     * 邮箱
     */
    @JsonProperty("email")
    private String email;

    /**
     * 角色：viewer-查看者, editor-编辑者
     */
    @JsonProperty("role")
    private String role;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;
}
