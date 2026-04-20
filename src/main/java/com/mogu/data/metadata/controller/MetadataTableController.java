package com.mogu.data.metadata.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.metadata.service.MetadataCollectorService;
import com.mogu.data.metadata.service.MetadataTableService;
import com.mogu.data.metadata.vo.TableDetailVO;
import com.mogu.data.metadata.vo.TablePageVO;
import com.mogu.data.metadata.vo.TablePermissionVO;
import com.mogu.data.query.service.QueryService;
import com.mogu.data.query.vo.QueryResultVO;
import com.mogu.data.system.service.PermissionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 元数据管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataTableController {

    private final MetadataCollectorService collectorService;
    private final MetadataTableMapper tableMapper;
    private final MetadataTableService tableService;
    private final PermissionService permissionService;
    private final QueryService queryService;

    /**
     * 手动触发 ClickHouse 元数据采集
     */
    @PostMapping("/collect")
    public Result<Void> collect() {
        collectorService.collect();
        return Result.success();
    }

    /**
     * 获取所有表（不分页，用于权限配置）
     */
    @GetMapping("/tables/all")
    public Result<List<MetadataTable>> listAll() {
        return Result.success(tableService.listAllTables());
    }

    /**
     * 获取当前用户有读权限的表列表
     */
    @GetMapping("/tables/accessible")
    public Result<List<MetadataTable>> listAccessible() {
        Long userId = LoginUser.currentUserId();
        Set<String> readable = permissionService.getReadableTables(userId);
        if (readable == null || readable.isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        List<MetadataTable> all = tableService.listAllTables();
        if (readable.contains("*")) {
            return Result.success(all);
        }
        List<MetadataTable> accessible = all.stream()
                .filter(t -> readable.contains(t.getDatabaseName() + "." + t.getTableName()))
                .collect(Collectors.toList());
        return Result.success(accessible);
    }

    /**
     * 数据目录-表列表分页查询
     */
    @GetMapping("/tables")
    public Result<Page<TablePageVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(tableService.pageTables(keyword, page, size));
    }

    /**
     * 表详情-基本信息（Tab2）
     */
    @GetMapping("/tables/{id}")
    public Result<TableDetailVO> getById(@PathVariable Long id) {
        TableDetailVO detail = tableService.getTableDetail(id);
        if (detail == null) {
            return Result.error("表不存在");
        }
        return Result.success(detail);
    }

    /**
     * 表详情-字段信息（Tab1）
     */
    @GetMapping("/tables/{id}/columns")
    public Result<List<MetadataColumn>> getColumns(@PathVariable Long id) {
        MetadataTable table = tableMapper.selectById(id);
        if (table == null || table.getDeleted() != null && table.getDeleted() == 1) {
            return Result.error("表不存在");
        }
        return Result.success(tableService.getColumns(id));
    }

    /**
     * 表详情-权限信息（Tab3）
     */
    @GetMapping("/tables/{id}/permission")
    public Result<TablePermissionVO> getPermission(@PathVariable Long id) {
        MetadataTable table = tableMapper.selectById(id);
        if (table == null || table.getDeleted() != null && table.getDeleted() == 1) {
            return Result.error("表不存在");
        }

        Long userId = LoginUser.currentUserId();
        String fullTableName = table.getDatabaseName() + "." + table.getTableName();

        TablePermissionVO vo = new TablePermissionVO();
        vo.setRead(permissionService.hasReadPermission(userId, fullTableName));
        vo.setWrite(permissionService.hasWritePermission(userId, fullTableName));
        return Result.success(vo);
    }

    /**
     * 修改表责任人
     */
    @PutMapping("/tables/{id}/owner")
    public Result<Void> updateOwner(@PathVariable Long id, @RequestBody UpdateOwnerRequest request) {
        MetadataTable table = tableMapper.selectById(id);
        if (table == null) {
            return Result.error("表不存在");
        }
        table.setOwnerId(request.getOwnerId());
        tableMapper.updateById(table);
        return Result.success();
    }

    /**
     * 表数据预览（前100条）
     */
    @GetMapping("/tables/{id}/preview")
    public Result<QueryResultVO> preview(@PathVariable Long id) {
        MetadataTable table = tableMapper.selectById(id);
        if (table == null || table.getDeleted() != null && table.getDeleted() == 1) {
            return Result.error("表不存在");
        }

        Long userId = LoginUser.currentUserId();
        String fullTableName = table.getDatabaseName() + "." + table.getTableName();
        if (!permissionService.hasReadPermission(userId, fullTableName)) {
            return Result.error("无权限预览该表");
        }

        String sql = "SELECT * FROM " + fullTableName + " LIMIT 100";
        QueryResultVO vo = queryService.execute(sql, userId);
        return Result.success(vo);
    }

    @Data
    public static class UpdateOwnerRequest {
        private Long ownerId;
    }
}
