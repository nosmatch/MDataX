package com.mogu.data.controller;

import com.mogu.data.common.BusinessException;
import com.mogu.data.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用 Controller（验证统一响应与全局异常处理）
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/success")
    public Result<String> success() {
        return Result.success("hello");
    }

    @GetMapping("/biz-error")
    public Result<String> bizError() {
        throw new BusinessException("测试业务异常");
    }

    @GetMapping("/sys-error")
    public Result<String> sysError() {
        throw new RuntimeException("测试系统异常");
    }

}
