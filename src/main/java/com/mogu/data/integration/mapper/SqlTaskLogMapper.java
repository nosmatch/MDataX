package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.SqlTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL任务日志Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface SqlTaskLogMapper extends BaseMapper<SqlTaskLog> {
}
