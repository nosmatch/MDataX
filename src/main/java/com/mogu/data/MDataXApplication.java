package com.mogu.data;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MDataX 数据管理平台启动类
 *
 * @author fengzhu
 */
@SpringBootApplication
@MapperScan("com.mogu.data")
public class MDataXApplication {

    public static void main(String[] args) {
        SpringApplication.run(MDataXApplication.class, args);
    }

}
