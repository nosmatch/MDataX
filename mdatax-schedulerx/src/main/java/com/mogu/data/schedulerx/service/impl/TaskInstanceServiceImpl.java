package com.mogu.data.schedulerx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.mapper.TaskInstanceMapper;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import org.springframework.stereotype.Service;

/**
 * 任务执行实例 Service 实现
 *
 * @author fengzhu
 */
@Service
public class TaskInstanceServiceImpl extends ServiceImpl<TaskInstanceMapper, TaskInstance> implements TaskInstanceService {

}
