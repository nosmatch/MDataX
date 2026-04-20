package com.mogu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
