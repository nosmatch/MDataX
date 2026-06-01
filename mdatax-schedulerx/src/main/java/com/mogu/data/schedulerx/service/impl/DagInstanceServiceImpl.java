package com.mogu.data.schedulerx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.mapper.DagInstanceMapper;
import com.mogu.data.schedulerx.service.DagInstanceService;
import org.springframework.stereotype.Service;

/**
 * DAG 执行实例 Service 实现
 *
 * @author fengzhu
 */
@Service
public class DagInstanceServiceImpl extends ServiceImpl<DagInstanceMapper, DagInstance> implements DagInstanceService {

}
