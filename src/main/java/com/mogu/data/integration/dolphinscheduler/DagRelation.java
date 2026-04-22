package com.mogu.data.integration.dolphinscheduler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DS DAG 节点依赖关系
 *
 * @author fengzhu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DagRelation {

    /**
     * 上游节点编码
     */
    private Long preTaskCode;

    /**
     * 下游节点编码
     */
    private Long postTaskCode;
}
