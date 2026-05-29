package com.mogu.data.quality.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.quality.engine.model.CheckExecutionResult;
import com.mogu.data.quality.engine.model.QualityCheckContext;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Webhook 告警通知服务
 *
 * @author fengzhu
 * @since 2026-05-19
 */
@Slf4j
@Service
public class WebhookAlertService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${quality.alert.webhook.url:}")
    private String webhookUrl;

    @Value("${quality.alert.webhook.enabled:true}")
    private boolean enabled;

    /**
     * 根据检查结果发送告警
     */
    public void sendAlertIfNeeded(QualityCheckContext context) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        // 只在有失败规则时告警
        if (context.getFailedRules() == null || context.getFailedRules() == 0) {
            return;
        }

        try {
            String message = buildAlertMessage(context);
            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "text");
            Map<String, String> text = new HashMap<>();
            text.put("content", message);
            payload.put("text", text);

            String json = objectMapper.writeValueAsString(payload);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder().url(webhookUrl).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("质量告警发送成功: tableId={}, failedRules={}", context.getTableId(), context.getFailedRules());
                } else {
                    log.warn("质量告警发送失败: status={}, body={}", response.code(), response.body() != null ? response.body().string() : "");
                }
            }
        } catch (Exception e) {
            log.error("发送质量告警异常: {}", e.getMessage(), e);
        }
    }

    private String buildAlertMessage(QualityCheckContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 数据质量告警\n");
        sb.append("━━━━━━━━━━━━━━━\n");
        sb.append("表ID: ").append(context.getTableId()).append("\n");
        sb.append("检查类型: ").append(context.getCheckType()).append("\n");
        sb.append("质量分数: ").append(context.getQualityScore()).append("/100\n");
        sb.append("质量等级: ").append(context.getQualityLevel()).append("\n");
        sb.append("失败规则: ").append(context.getFailedRules()).append("/").append(context.getTotalRules()).append("\n");

        // 列出失败的规则
        if (context.getResults() != null) {
            List<CheckExecutionResult> failedResults = context.getResults().stream()
                    .filter(r -> r.getSuccess() && r.getCheckResult() != null && "FAIL".equals(r.getCheckResult().getStatus()))
                    .collect(Collectors.toList());

            if (!failedResults.isEmpty()) {
                sb.append("\n失败详情:\n");
                for (CheckExecutionResult r : failedResults) {
                    sb.append("  • ").append(r.getRuleName());
                    if (r.getCheckResult() != null && r.getCheckResult().getActualValue() != null) {
                        sb.append(" (实际值: ").append(r.getCheckResult().getActualValue()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("\n时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return sb.toString();
    }
}
