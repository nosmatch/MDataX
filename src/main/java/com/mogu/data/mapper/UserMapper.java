package com.mogu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
