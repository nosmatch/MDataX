package com.mogu.data.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
