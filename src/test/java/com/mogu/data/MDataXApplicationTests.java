package com.mogu.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用基础连接测试
 *
 * @author fengzhu
 */
@SpringBootTest
public class MDataXApplicationTests {

    @Autowired
    @Qualifier("dataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("clickHouseJdbcTemplate")
    private JdbcTemplate clickHouseJdbcTemplate;

    @Test
    void contextLoads() {
        assertNotNull(mysqlDataSource);
        assertNotNull(clickHouseJdbcTemplate);
    }

    @Test
    void testMySQLConnection() throws SQLException {
        try (Connection connection = mysqlDataSource.getConnection()) {
            assertTrue(connection.isValid(5));
        }
    }

    @Test
    void testClickHouseConnection() {
        Integer result = clickHouseJdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertNotNull(result);
        assertTrue(result == 1);
    }

}
