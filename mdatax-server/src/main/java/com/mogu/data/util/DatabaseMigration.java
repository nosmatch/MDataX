package com.mogu.data.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库迁移工具类
 * 用于执行 schema-update 目录下的 SQL 脚本
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class DatabaseMigration {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseMigration(@Qualifier("dataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
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

    /**
     * 检查列是否存在
     */
    public boolean columnExists(String tableName, String columnName) {
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

    /**
     * 执行 SQL 脚本文件
     *
     * @param scriptPath SQL 脚本路径（classpath 路径）
     */
    public void executeScript(String scriptPath) {
        log.info("开始执行数据库迁移脚本: {}", scriptPath);

        try {
            List<String> sqlStatements = readSqlStatements(scriptPath);

            for (String sql : sqlStatements) {
                if (sql != null && !sql.trim().isEmpty()) {
                    executeSql(sql);
                }
            }

            log.info("数据库迁移脚本执行完成: {}", scriptPath);
        } catch (Exception e) {
            log.error("执行数据库迁移脚本失败: {}", scriptPath, e);
            throw new RuntimeException("数据库迁移失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取 SQL 脚本文件并拆分为多个语句
     */
    private List<String> readSqlStatements(String scriptPath) throws Exception {
        List<String> statements = new ArrayList<>();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream(scriptPath),
                        StandardCharsets.UTF_8
                )
        );

        StringBuilder currentStatement = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            // 跳过注释
            if (line.trim().startsWith("--")) {
                continue;
            }

            currentStatement.append(line).append("\n");

            // 检查是否是完整的语句（以分号结尾）
            if (line.trim().endsWith(";")) {
                String statement = currentStatement.toString();
                // 移除末尾的分号和空白
                statement = statement.trim().replaceAll(";$", "");
                statements.add(statement);
                currentStatement = new StringBuilder();
            }
        }

        reader.close();
        return statements;
    }

    /**
     * 执行单条 SQL
     */
    private void executeSql(String sql) {
        try {
            log.debug("执行 SQL: {}", sql);
            jdbcTemplate.execute(sql);
            log.info("SQL 执行成功");
        } catch (Exception e) {
            log.error("SQL 执行失败: {}", sql, e);
            throw e;
        }
    }

    /**
     * 验证迁移结果
     */
    public boolean validateMigration() {
        boolean reportTableValid = true;
        boolean collaboratorTableValid = true;

        // 检查 report 表的新字段
        if (!columnExists("report", "owner_id")) {
            log.error("report 表缺少 owner_id 字段");
            reportTableValid = false;
        }
        if (!columnExists("report", "visibility")) {
            log.error("report 表缺少 visibility 字段");
            reportTableValid = false;
        }

        // 检查 report_collaborator 表是否存在
        if (!tableExists("report_collaborator")) {
            log.error("report_collaborator 表不存在");
            collaboratorTableValid = false;
        }

        return reportTableValid && collaboratorTableValid;
    }
}
