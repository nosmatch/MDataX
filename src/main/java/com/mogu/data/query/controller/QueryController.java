package com.mogu.data.query.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.query.service.QueryService;
import com.mogu.data.query.vo.QueryExecuteRequest;
import com.mogu.data.query.vo.QueryResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * SQL查询控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /**
     * 执行SQL查询
     */
    @PostMapping("/execute")
    public Result<QueryResultVO> execute(@RequestBody QueryExecuteRequest request) {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        QueryResultVO result = queryService.execute(request.getSql(), userId);
        return Result.success(result);
    }

}
