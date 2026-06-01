package com.mogu.data.schedulerx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.schedulerx.entity.DagTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * DAG 任务节点定义 Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface DagTaskMapper extends BaseMapper<DagTask> {

}
