package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.service.DatasourceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据源管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;

    @GetMapping("/page")
    public Result<Page<Datasource>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(datasourceService.pageDatasources(keyword, page, size));
    }

    @GetMapping("/list")
    public Result<java.util.List<Datasource>> listAll() {
        return Result.success(datasourceService.lambdaQuery()
                .eq(Datasource::getDeleted, 0)
                .eq(Datasource::getStatus, 1)
                .list());
    }

    @GetMapping("/{id}")
    public Result<Datasource> getById(@PathVariable Long id) {
        Datasource ds = datasourceService.getById(id);
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            return Result.error("数据源不存在");
        }
        return Result.success(ds);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody DatasourceCreateRequest request) {
        Datasource ds = new Datasource();
        ds.setName(request.getName());
        ds.setType(request.getType());
        ds.setHost(request.getHost());
        ds.setPort(request.getPort());
        ds.setDatabaseName(request.getDatabaseName());
        ds.setUsername(request.getUsername());
        ds.setPassword(request.getPassword());
        ds.setExtraConfig(request.getExtraConfig());
        datasourceService.createDatasource(ds);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DatasourceUpdateRequest request) {
        Datasource ds = new Datasource();
        ds.setId(id);
        ds.setName(request.getName());
        ds.setHost(request.getHost());
        ds.setPort(request.getPort());
        ds.setDatabaseName(request.getDatabaseName());
        ds.setUsername(request.getUsername());
        ds.setPassword(request.getPassword());
        ds.setStatus(request.getStatus());
        ds.setExtraConfig(request.getExtraConfig());
        datasourceService.updateDatasource(ds);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasourceService.removeById(id);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(@RequestBody TestConnectionRequest request) {
        Datasource ds = new Datasource();
        ds.setType(request.getType());
        ds.setHost(request.getHost());
        ds.setPort(request.getPort());
        ds.setDatabaseName(request.getDatabaseName());
        ds.setUsername(request.getUsername());
        ds.setPassword(request.getPassword());
        ds.setExtraConfig(request.getExtraConfig());
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = datasourceService.testConnection(ds);
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败，请检查配置");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return Result.success(result);
    }

    @Data
    public static class DatasourceCreateRequest {
        @NotBlank(message = "数据源名称不能为空")
        private String name;
        @NotBlank(message = "类型不能为空")
        private String type;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        /** 类型特定配置（JSON格式） */
        private String extraConfig;
    }

    @Data
    public static class DatasourceUpdateRequest {
        private String name;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        private Integer status;
        /** 类型特定配置（JSON格式） */
        private String extraConfig;
    }

    @Data
    public static class TestConnectionRequest {
        @NotBlank(message = "类型不能为空")
        private String type;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        /** 类型特定配置（JSON格式） */
        private String extraConfig;
    }

}
