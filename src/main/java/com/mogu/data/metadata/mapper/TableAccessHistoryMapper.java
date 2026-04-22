package com.mogu.data.metadata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.metadata.entity.TableAccessHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表访问历史记录Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface TableAccessHistoryMapper extends BaseMapper<TableAccessHistory> {
}
