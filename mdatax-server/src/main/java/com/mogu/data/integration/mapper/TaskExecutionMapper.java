package com.mogu.data.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mogu.data.integration.entity.TaskExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行记录Mapper接口
 *
 * @author fengzhu
 */
@Mapper
public interface TaskExecutionMapper extends BaseMapper<TaskExecution> {

    /**
     * 根据执行ID查询
     */
    TaskExecution selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 查询任务成功执行的平均耗时（毫秒）
     */
    Long selectAvgDurationMsByTaskId(@Param("taskId") Long taskId);

    /**
     * 按状态统计执行记录数量
     */
    List<StatusCount> countByStatus(@Param("startTime") java.time.LocalDateTime startTime,
                                    @Param("endTime") java.time.LocalDateTime endTime);

    class StatusCount {
        private String status;
        private Long count;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }
}
