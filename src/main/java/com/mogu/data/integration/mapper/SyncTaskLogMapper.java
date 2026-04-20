package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.SyncTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步任务日志Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface SyncTaskLogMapper extends BaseMapper<SyncTaskLog> {
}
