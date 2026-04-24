package com.mogu.data.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移执行器
 * 通过 Spring Boot 启动参数控制是否执行迁移
 *
 * 使用方式：
 * java -jar app.jar --migration.enabled=true --migration.script=db/schema-update/2026-04-24-report-permission.sql
 *
 * @author fengzhu
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "migration.enabled", havingValue = "true")
public class MigrationRunner implements CommandLineRunner {

    @Autowired
    private DatabaseMigration databaseMigration;

    @org.springframework.beans.factory.annotation.Value("${migration.script:db/schema-update/2026-04-24-report-permission.sql}")
    private String migrationScript;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("开始执行数据库迁移");
        log.info("迁移脚本: {}", migrationScript);
        log.info("========================================");

        try {
            // 执行迁移
            databaseMigration.executeScript(migrationScript);

            // 验证迁移结果
            if (databaseMigration.validateMigration()) {
                log.info("========================================");
                log.info("数据库迁移成功完成！");
                log.info("========================================");
            } else {
                log.error("========================================");
                log.error("数据库迁移验证失败！");
                log.error("========================================");
                System.exit(1);
            }
        } catch (Exception e) {
            log.error("========================================");
            log.error("数据库迁移执行失败！");
            log.error("========================================", e);
            System.exit(1);
        }
    }
}
