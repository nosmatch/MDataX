package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.IdGenerator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

/**
 * ID生成器Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface IdGeneratorMapper extends BaseMapper<IdGenerator> {

    /**
     * 插入记录并返回自增ID
     */
    @Insert("INSERT INTO id_generator(prefix) VALUES(#{prefix})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAndGetId(IdGenerator idGenerator);
}
