package com.mogu.data.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表列表视图对象（包含图表统计信息）
 *
 * @author fengzhu
 */
@Data
public class ReportListVO {

    private Long id;

    private String name;

    private String description;

    @JsonProperty("chartCount")
    private Integer chartCount;

    @JsonProperty("chartTypes")
    private List<String> chartTypes;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
