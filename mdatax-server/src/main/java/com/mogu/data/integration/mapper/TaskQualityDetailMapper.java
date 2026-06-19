package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskQualityDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 质量监控任务详情Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskQualityDetailMapper extends BaseMapper<TaskQualityDetail> {

    /**
     * 根据任务ID查询详情
     */
    TaskQualityDetail selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID更新详情
     */
    int updateByTaskId(TaskQualityDetail detail);
}
