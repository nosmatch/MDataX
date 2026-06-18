package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskTagRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务标签关联Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskTagRelationMapper extends BaseMapper<TaskTagRelation> {
}
