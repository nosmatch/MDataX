package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.integration.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 批量查询任务的最近执行记录
     */
    List<TaskExecutionExt> selectLastExecutionByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 按最近执行状态分页查询任务
     */
    Page<Task> pageTasksByLastExecutionStatus(
            @Param("keyword") String keyword,
            @Param("taskType") String taskType,
            @Param("status") Integer status,
            @Param("ownerUserId") Long ownerUserId,
            @Param("priority") Integer priority,
            @Param("tags") String tags,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime,
            @Param("lastExecutionStatus") String lastExecutionStatus,
            Page<Task> page);

    /**
     * 任务最近执行记录扩展
     */
    class TaskExecutionExt {
        private Long taskId;
        private String status;
        private java.time.LocalDateTime startTime;
        private String executionId;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(java.time.LocalDateTime startTime) { this.startTime = startTime; }
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
    }
}
