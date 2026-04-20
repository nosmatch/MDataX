package com.mogu.data.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ClickHouse 数据源配置
 * MySQL 数据源由 Spring Boot 自动配置
 *
 * @author fengzhu
 */
@Configuration
public class DataSourceConfig {

    /**
     * ClickHouse 数据源
     */
    @Bean(name = "clickHouseDataSource")
    @ConfigurationProperties(prefix = "clickhouse.datasource")
    public DataSource clickHouseDataSource() {
        return DataSourceBuilder.create().type(DruidDataSource.class).build();
    }

}
