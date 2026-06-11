package com.mogu.data.schedulerx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.schedulerx.entity.DagTaskEntity;
import com.mogu.data.schedulerx.mapper.DagTaskMapper;
import com.mogu.data.schedulerx.service.DagTaskService;
import org.springframework.stereotype.Service;

/**
 * DAG 任务节点定义 Service 实现
 *
 * @author fengzhu
 */
@Service
public class DagTaskServiceImpl extends ServiceImpl<DagTaskMapper, DagTaskEntity> implements DagTaskService {

}
