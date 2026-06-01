package com.mogu.data.schedulerx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.schedulerx.entity.TaskInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行实例 Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstance> {

}
