package com.mogu.data.schedulerx.config;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz 配置
 *
 * @author fengzhu
 */
@Configuration
public class QuartzConfig {

    /**
     * 支持 Spring 依赖注入的 JobFactory
     */
    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        return new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
                Object jobInstance = super.createJobInstance(bundle);
                beanFactory.autowireBean(jobInstance);
                return jobInstance;
            }
        };
    }
}
