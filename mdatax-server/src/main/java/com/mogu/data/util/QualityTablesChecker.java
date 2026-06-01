package com.mogu.data.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 质量监控数据库表检查工具
 * 用于确认质量监控相关的数据库表是否已创建
 *
 * @author fengzhu
 * @since 2026-05-12
 */
public class QualityTablesChecker {

    // 数据库配置（与application-dev.yml保持一致）
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/mdatax?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root123";

    // 需要检查的表名
    private static final String[] EXPECTED_TABLES = {
        "quality_rule",
        "quality_check_result",
        "quality_report",
        "quality_alert_rule",
        "quality_alert_history"
    };

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("质量监控数据库表检查工具");
        System.out.println("========================================");
        System.out.println();

        Connection conn = null;
        try {
            // 1. 连接数据库
            System.out.println("正在连接数据库...");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("✓ 数据库连接成功");
            System.out.println();

            // 2. 检查表是否存在
            System.out.println("正在检查质量监控相关表...");
            System.out.println("----------------------------------------");

            DatabaseMetaData meta = conn.getMetaData();
            List<String> existingTables = new ArrayList<>();
            List<String> missingTables = new ArrayList<>();

            for (String tableName : EXPECTED_TABLES) {
                ResultSet rs = meta.getTables(null, null, tableName, null);
                if (rs.next()) {
                    existingTables.add(tableName);
                    System.out.println("✓ " + tableName + " - 存在");

                    // 显示表结构信息
                    showTableInfo(conn, tableName);
                } else {
                    missingTables.add(tableName);
                    System.out.println("✗ " + tableName + " - 不存在");
                }
                rs.close();
            }

            System.out.println();
            System.out.println("========================================");
            System.out.println("检查结果统计");
            System.out.println("========================================");
            System.out.println("预计表数量: " + EXPECTED_TABLES.length);
            System.out.println("已创建表数量: " + existingTables.size());
            System.out.println("缺失表数量: " + missingTables.size());

            if (!existingTables.isEmpty()) {
                System.out.println();
                System.out.println("已创建的表:");
                for (String table : existingTables) {
                    System.out.println("  • " + table);
                }
            }

            if (!missingTables.isEmpty()) {
                System.out.println();
                System.out.println("缺失的表:");
                for (String table : missingTables) {
                    System.out.println("  • " + table);
                }
                System.out.println();
                System.out.println("⚠️  建议: 请执行数据库初始化脚本");
                System.out.println("脚本路径: src/main/resources/db/schema-update/2026-05-09-quality-monitor-tables.sql");
            } else {
                System.out.println();
                System.out.println("✅ 所有质量监控表已创建完成！");
                System.out.println();
                System.out.println("下一步:");
                System.out.println("  1. 启动应用: mvn exec:java -Dexec.mainClass=\"com.mogu.data.MDataXApplication\"");
                System.out.println("  2. 访问质量监控接口开始使用");
            }

        } catch (SQLException e) {
            System.out.println();
            System.out.println("✗ 数据库连接失败");
            System.out.println("错误信息: " + e.getMessage());
            System.out.println();
            System.out.println("请检查:");
            System.out.println("  1. MySQL服务是否启动");
            System.out.println("  2. 数据库配置是否正确 (application-dev.yml)");
            System.out.println("  3. 数据库 'mdatax' 是否已创建");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("检查完成");
        System.out.println("========================================");
    }

    /**
     * 显示表的详细信息
     */
    private static void showTableInfo(Connection conn, String tableName) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM " + tableName);
            if (rs.next()) {
                int count = rs.getInt("count");
                System.out.println("    └─ 数据量: " + count + " 行");
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.out.println("    └─ 无法查询数据量: " + e.getMessage());
        }
    }
}
