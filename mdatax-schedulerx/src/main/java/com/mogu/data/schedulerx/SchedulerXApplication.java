package com.mogu.data.schedulerx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SchedulerX 独立调度系统入口
 *
 * @author fengzhu
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class SchedulerXApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerXApplication.class, args);
    }

}
