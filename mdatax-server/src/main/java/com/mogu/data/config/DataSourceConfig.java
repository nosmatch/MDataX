package com.mogu.data.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 手动定义 MySQL 主数据源（@Primary）和 ClickHouse 数据源
 * 避免 ClickHouse Bean 导致 Spring Boot 自动配置跳过 MySQL
 *
 * @author fengzhu
 */
@Configuration
public class DataSourceConfig {

    /**
     * MySQL 主数据源（@Primary，供 MyBatis-Plus 使用）
     */
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().type(DruidDataSource.class).build();
    }

    /**
     * ClickHouse 数据源
     */
    @Bean(name = "clickHouseDataSource")
    @ConfigurationProperties(prefix = "clickhouse.datasource")
    public DataSource clickHouseDataSource() {
        return DataSourceBuilder.create().type(DruidDataSource.class).build();
    }

}
