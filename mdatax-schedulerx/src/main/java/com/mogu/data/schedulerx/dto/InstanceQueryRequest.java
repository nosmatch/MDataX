package com.mogu.data.schedulerx.dto;

import lombok.Data;

/**
 * 实例查询请求
 *
 * @author fengzhu
 */
@Data
public class InstanceQueryRequest {

    private String dagId;

    private String status;

    private String triggerType;

    private String startTime;

    private String endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

}
