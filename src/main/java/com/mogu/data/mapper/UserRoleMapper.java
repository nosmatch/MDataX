package com.mogu.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
