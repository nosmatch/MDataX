package com.mogu.data.schedulerx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步线程池配置
 *
 * @author fengzhu
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("schedulerEventExecutor")
    public Executor schedulerEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("sx-event-");
        executor.initialize();
        return executor;
    }

    @Bean("retryExecutor")
    public ScheduledExecutorService retryExecutor() {
        return Executors.newScheduledThreadPool(4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sx-retry-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

}
