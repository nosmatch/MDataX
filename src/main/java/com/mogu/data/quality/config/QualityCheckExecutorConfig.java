package com.mogu.data.quality.config;

import com.mogu.data.quality.constants.QualityConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 质量检查线程池配置类
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Configuration
@EnableAsync
public class QualityCheckExecutorConfig {

    /**
     * 质量检查线程池
     * 用于异步执行质量检查任务
     *
     * @return Executor
     */
    @Bean("qualityCheckExecutor")
    public Executor qualityCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(QualityConstants.CORE_POOL_SIZE);

        // 最大线程数
        executor.setMaxPoolSize(QualityConstants.MAX_POOL_SIZE);

        // 队列容量
        executor.setQueueCapacity(QualityConstants.QUEUE_CAPACITY);

        // 线程名称前缀
        executor.setThreadNamePrefix("quality-check-");

        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待最大时间（秒）
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        return executor;
    }

    /**
     * 定时检查线程池
     * 用于执行定时质量检查任务
     *
     * @return Executor
     */
    @Bean("scheduledCheckExecutor")
    public Executor scheduledCheckExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数（定时检查不需要太多线程）
        executor.setCorePoolSize(5);

        // 最大线程数
        executor.setMaxPoolSize(10);

        // 队列容量
        executor.setQueueCapacity(50);

        // 线程名称前缀
        executor.setThreadNamePrefix("scheduled-check-");

        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：记录日志并丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待最大时间（秒）
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        return executor;
    }
}
