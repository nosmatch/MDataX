package com.mogu.data.integration.dolphinscheduler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DS DAG 节点定义
 *
 * @author fengzhu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DagNode {

    /**
     * DS 节点编码（需全局唯一且稳定）
     */
    private Long taskCode;

    /**
     * 节点显示名称
     */
    private String taskName;

    /**
     * SHELL 回调脚本内容
     */
    private String callbackScript;
}
