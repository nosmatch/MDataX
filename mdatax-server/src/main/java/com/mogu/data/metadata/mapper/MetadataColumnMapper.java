package com.mogu.data.metadata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.metadata.entity.MetadataColumn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 元数据-字段Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface MetadataColumnMapper extends BaseMapper<MetadataColumn> {
}
