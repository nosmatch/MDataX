package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.SqlTaskDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SQL任务依赖关系Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface SqlTaskDependencyMapper extends BaseMapper<SqlTaskDependency> {

    /**
     * 查询某任务的所有上游依赖任务ID
     */
    @Select("SELECT depend_task_id FROM sql_task_dependency WHERE task_id = #{taskId}")
    List<Long> selectDependTaskIds(@Param("taskId") Long taskId);

    /**
     * 查询依赖某任务的所有下游任务ID
     */
    @Select("SELECT task_id FROM sql_task_dependency WHERE depend_task_id = #{dependTaskId}")
    List<Long> selectDownstreamTaskIds(@Param("dependTaskId") Long dependTaskId);

    /**
     * 按工作流ID查询所有依赖关系
     */
    @Select("SELECT * FROM sql_task_dependency WHERE workflow_id = #{workflowId}")
    List<SqlTaskDependency> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除某任务的所有依赖关系（作为下游）
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM sql_task_dependency WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);

    /**
     * 删除某工作流的所有依赖关系
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM sql_task_dependency WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
