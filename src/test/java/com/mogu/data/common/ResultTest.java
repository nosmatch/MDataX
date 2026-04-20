package com.mogu.data.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一响应与全局异常处理测试
 *
 * @author fengzhu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ResultTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSuccessResponse() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/test/success", String.class);
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals(200, result.get("code"));
        assertEquals("操作成功", result.get("message"));
        assertEquals("hello", result.get("data"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    void testBusinessException() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/test/biz-error", String.class);
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals(1000, result.get("code"));
        assertEquals("测试业务异常", result.get("message"));
    }

    @Test
    void testSystemException() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/test/sys-error", String.class);
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
        assertEquals(500, result.get("code"));
        assertEquals("系统繁忙，请稍后再试", result.get("message"));
    }

}
