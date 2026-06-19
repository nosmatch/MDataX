package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.IdGenerator;
import com.mogu.data.integration.mapper.IdGeneratorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ID生成器服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
public class IdGeneratorService extends ServiceImpl<IdGeneratorMapper, IdGenerator> {

    /**
     * 生成8位数字执行ID
     * 使用数据库自增ID确保唯一性和递增性
     */
    @Transactional
    public synchronized String generateExecutionId() {
        try {
            IdGenerator idGenerator = new IdGenerator();
            idGenerator.setPrefix("EXEC");

            // 插入记录获取自增ID
            baseMapper.insertAndGetId(idGenerator);

            // 将ID格式化为8位数字
            String executionId = String.format("%08d", idGenerator.getId());

            log.debug("生成执行ID: {}", executionId);
            return executionId;
        } catch (Exception e) {
            log.error("生成执行ID失败", e);
            throw new RuntimeException("生成执行ID失败", e);
        }
    }
}
