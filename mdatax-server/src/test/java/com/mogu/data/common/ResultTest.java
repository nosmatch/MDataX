package com.mogu.data.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.controller.TestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一响应与全局异常处理测试
 *
 * <p>使用 MockMvc 独立测试 Controller + ControllerAdvice，绕过 JWT 拦截器。</p>
 *
 * @author fengzhu
 */
public class ResultTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testSuccessResponse() throws Exception {
        String response = mockMvc.perform(get("/test/success")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        assertEquals(200, result.get("code"));
        assertEquals("操作成功", result.get("message"));
        assertEquals("hello", result.get("data"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    void testBusinessException() throws Exception {
        String response = mockMvc.perform(get("/test/biz-error")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        assertEquals(1000, result.get("code"));
        assertEquals("测试业务异常", result.get("message"));
    }

    @Test
    void testSystemException() throws Exception {
        String response = mockMvc.perform(get("/test/sys-error")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        assertEquals(500, result.get("code"));
        assertEquals("测试系统异常", result.get("message"));
    }

}
