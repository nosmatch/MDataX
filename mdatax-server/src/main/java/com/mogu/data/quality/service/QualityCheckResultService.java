package com.mogu.data.quality.service;

import com.mogu.data.quality.entity.QualityCheckResult;
import com.mogu.data.quality.mapper.QualityCheckResultMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 质量检查结果服务（临时实现）
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Service
public class QualityCheckResultService {

    @Autowired
    private QualityCheckResultMapper qualityCheckResultMapper;

    /**
     * 批量保存检查结果
     *
     * @param results 检查结果列表
     */
    public void batchSaveResults(List<QualityCheckResult> results) {
        results.forEach(qualityCheckResultMapper::insert);
    }
}
