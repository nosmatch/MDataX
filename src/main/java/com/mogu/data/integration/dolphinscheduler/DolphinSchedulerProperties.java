package com.mogu.data.integration.dolphinscheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DolphinScheduler 配置属性
 *
 * @author fengzhu
 */
@Data
@Component
@ConfigurationProperties(prefix = "dolphinscheduler")
public class DolphinSchedulerProperties {

    private boolean enabled;
    private String baseUrl;
    private String token;
    private Long projectCode;
    private String tenantCode;
    private String workerGroup;
    private String callbackSecret;
    private String callbackUrl;
}
