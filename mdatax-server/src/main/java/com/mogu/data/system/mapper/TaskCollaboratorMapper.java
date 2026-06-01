package com.mogu.data.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.system.entity.TaskCollaborator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务协作者 Mapper
 *
 * @author fengzhu
 */
@Mapper
public interface TaskCollaboratorMapper extends BaseMapper<TaskCollaborator> {

    /**
     * 查询任务的协作者列表
     */
    @Select("SELECT tc.*, u.nickname AS user_name, u.username AS user_name2 " +
            "FROM task_collaborator tc " +
            "LEFT JOIN sys_user u ON tc.user_id = u.id " +
            "WHERE tc.task_id = #{taskId} AND tc.task_type = #{taskType}")
    List<TaskCollaborator> selectByTask(@Param("taskId") Long taskId, @Param("taskType") String taskType);

    /**
     * 判断用户是否为某任务的协作者
     */
    @Select("SELECT COUNT(*) FROM task_collaborator " +
            "WHERE task_id = #{taskId} AND task_type = #{taskType} AND user_id = #{userId}")
    int countByTaskAndUser(@Param("taskId") Long taskId, @Param("taskType") String taskType, @Param("userId") Long userId);
}
