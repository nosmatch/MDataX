package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.SqlTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL任务Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface SqlTaskMapper extends BaseMapper<SqlTask> {
}
