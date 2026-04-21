package com.mogu.data.query.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.service.MetadataTableService;
import com.mogu.data.query.service.QueryService;
import com.mogu.data.query.vo.QueryExecuteRequest;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.query.vo.TableInfoVO;
import com.mogu.data.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PermissionService permissionService;
    private final MetadataTableService metadataTableService;

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

    /**
     * 获取当前用户有读权限的表列表（含字段）
     */
    @GetMapping("/tables")
    public Result<List<TableInfoVO>> getReadableTables() {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.success(Collections.emptyList());
        }

        Set<String> readable = permissionService.getReadableTables(userId);
        if (readable == null || readable.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<MetadataTable> all = metadataTableService.listAllTables();
        if (all == null || all.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<TableInfoVO> voList = all.stream()
                .filter(t -> readable.contains("*")
                        || readable.contains(t.getDatabaseName() + "." + t.getTableName()))
                .map(t -> {
                    TableInfoVO vo = new TableInfoVO();
                    vo.setId(t.getId());
                    vo.setDatabaseName(t.getDatabaseName());
                    vo.setTableName(t.getTableName());
                    vo.setTableComment(t.getTableComment());
                    List<MetadataColumn> columns = metadataTableService.getColumns(t.getId());
                    vo.setColumns(columns);
                    return vo;
                })
                .collect(Collectors.toList());

        return Result.success(voList);
    }

}
