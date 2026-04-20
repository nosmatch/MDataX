package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.SyncTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步任务Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {
}
