package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskDependency;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务依赖关系Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {

    /**
     * 查询任务的上游依赖
     */
    List<TaskDependency> selectByDownstreamTaskId(Long downstreamTaskId);

    /**
     * 查询任务的下游依赖
     */
    List<TaskDependency> selectByUpstreamTaskId(Long upstreamTaskId);
}
