package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作流执行实例Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {

    /**
     * 查询某工作流的最近一次执行实例
     */
    @Select("SELECT * FROM sql_task_workflow_instance WHERE workflow_id = #{workflowId} ORDER BY create_time DESC LIMIT 1")
    WorkflowInstance selectLatestByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据 DS 实例ID查询
     */
    @Select("SELECT * FROM sql_task_workflow_instance WHERE ds_instance_id = #{dsInstanceId} LIMIT 1")
    WorkflowInstance selectByDsInstanceId(@Param("dsInstanceId") Long dsInstanceId);

    /**
     * 查询某工作流最新的 RUNNING 实例
     */
    @Select("SELECT * FROM sql_task_workflow_instance WHERE workflow_id = #{workflowId} AND status = 'RUNNING' ORDER BY create_time DESC LIMIT 1")
    WorkflowInstance selectLatestRunningByWorkflowId(@Param("workflowId") Long workflowId);
}
