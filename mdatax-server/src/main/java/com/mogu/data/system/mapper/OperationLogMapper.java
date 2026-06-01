package com.mogu.data.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
