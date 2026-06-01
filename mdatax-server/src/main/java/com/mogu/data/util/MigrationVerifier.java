package com.mogu.data.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 数据库迁移验证工具
 * 用于验证数据库表结构是否正确创建
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/admin/migration")
public class MigrationVerifier {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MigrationVerifier(@Qualifier("dataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 验证报表权限表结构
     */
    @GetMapping("/verify-report-permission")
    public String verifyReportPermissionMigration() {
        StringBuilder result = new StringBuilder();
        result.append("=== 报表权限功能数据库迁移验证 ===\n\n");

        try {
            // 1. 检查 report 表是否有 owner_id 字段
            boolean hasOwnerId = columnExists("report", "owner_id");
            result.append("1. report.owner_id 字段: ").append(hasOwnerId ? "✓ 存在" : "✗ 不存在").append("\n");

            // 2. 检查 report 表是否有 visibility 字段
            boolean hasVisibility = columnExists("report", "visibility");
            result.append("2. report.visibility 字段: ").append(hasVisibility ? "✓ 存在" : "✗ 不存在").append("\n");

            // 3. 检查 report_collaborator 表是否存在
            boolean hasCollaboratorTable = tableExists("report_collaborator");
            result.append("3. report_collaborator 表: ").append(hasCollaboratorTable ? "✓ 存在" : "✗ 不存在").append("\n");

            // 4. 检查现有报表数据
            try {
                Integer reportCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM report WHERE deleted = 0", Integer.class);
                Integer ownerSetCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM report WHERE owner_id IS NOT NULL AND deleted = 0", Integer.class);

                result.append("\n数据统计:\n");
                result.append("- 报表总数: ").append(reportCount).append("\n");
                result.append("- 已设置所有者的报表数: ").append(ownerSetCount).append("\n");
            } catch (Exception e) {
                result.append("\n数据统计: 查询失败 - ").append(e.getMessage()).append("\n");
            }

            // 5. 总体结果
            boolean success = hasOwnerId && hasVisibility && hasCollaboratorTable;
            result.append("\n").append(success ? "✓ 迁移验证通过！" : "✗ 迁移验证失败！").append("\n");

            return result.toString();

        } catch (Exception e) {
            result.append("\n✗ 验证过程出错: ").append(e.getMessage()).append("\n");
            log.error("验证迁移失败", e);
            return result.toString();
        }
    }

    /**
     * 手动执行迁移（如果自动迁移失败）
     */
    @GetMapping("/execute")
    public String executeMigration() {
        try {
            log.info("开始手动执行数据库迁移...");

            // 检查是否已经迁移过
            if (columnExists("report", "owner_id")) {
                return "迁移已执行，跳过重复执行。\n\n" + verifyReportPermissionMigration();
            }

            // 执行迁移
            DatabaseMigration migration = new DatabaseMigration(
                    jdbcTemplate.getDataSource());
            migration.executeScript("db/schema-update/2026-04-24-report-permission.sql");

            return "✓ 迁移执行成功！\n\n" + verifyReportPermissionMigration();

        } catch (Exception e) {
            log.error("手动执行迁移失败", e);
            return "✗ 迁移执行失败: " + e.getMessage();
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            ResultSet rs = connection.getMetaData().getTables(
                    connection.getCatalog(),
                    null,
                    tableName,
                    new String[]{"TABLE"}
            );
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException e) {
            log.error("检查表是否存在失败: {}", tableName, e);
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            ResultSet rs = connection.getMetaData().getColumns(
                    connection.getCatalog(),
                    null,
                    tableName,
                    columnName
            );
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException e) {
            log.error("检查列是否存在失败: {}.{}", tableName, columnName, e);
            return false;
        }
    }
}
