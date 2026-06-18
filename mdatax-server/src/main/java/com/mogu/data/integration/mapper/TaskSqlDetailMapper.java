package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskSqlDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL任务详情Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskSqlDetailMapper extends BaseMapper<TaskSqlDetail> {

    /**
     * 根据任务ID查询详情
     */
    TaskSqlDetail selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID更新详情
     */
    int updateByTaskId(TaskSqlDetail detail);
}
