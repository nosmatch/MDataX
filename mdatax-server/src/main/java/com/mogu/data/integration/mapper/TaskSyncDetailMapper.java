package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskSyncDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 同步任务详情Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskSyncDetailMapper extends BaseMapper<TaskSyncDetail> {

    /**
     * 根据任务ID查询详情
     */
    TaskSyncDetail selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID更新详情
     */
    int updateByTaskId(TaskSyncDetail detail);
}
