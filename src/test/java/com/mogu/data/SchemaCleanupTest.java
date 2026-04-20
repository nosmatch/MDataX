package com.mogu.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 清理 mdatax 数据库中非本次创建的表
 *
 * @author fengzhu
 */
@SpringBootTest
public class SchemaCleanupTest {

    @Autowired
    private DataSource dataSource;

    /**
     * 本次 schema.sql 创建的表
     */
    private static final Set<String> EXPECTED_TABLES = new HashSet<>(Arrays.asList(
        "sys_user", "sys_role", "sys_user_role", "sys_role_permission",
        "sys_operation_log", "datasource", "sync_task", "sync_task_log",
        "sql_task", "sql_task_log", "metadata_table", "metadata_column",
        "user_table_visit"
    ));

    @Test
    void cleanupExtraTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // 查询 mdatax 库中所有表
        List<String> allTables = jdbcTemplate.queryForList(
            "SHOW TABLES FROM mdatax", String.class);

        System.out.println("=== mdatax 库中所有表 ===");
        for (String table : allTables) {
            System.out.println("  - " + table);
        }

        int dropped = 0;
        for (String table : allTables) {
            if (!EXPECTED_TABLES.contains(table)) {
                System.out.println("删除非预期表: " + table);
                jdbcTemplate.execute("DROP TABLE IF EXISTS mdatax." + table);
                dropped++;
            }
        }

        System.out.println("=== 清理完成，删除 " + dropped + " 张非预期表 ===");

        // 再次列出确认
        List<String> remaining = jdbcTemplate.queryForList(
            "SHOW TABLES FROM mdatax", String.class);
        System.out.println("剩余表:");
        for (String table : remaining) {
            System.out.println("  - " + table);
        }
    }

}
