package com.mogu.data.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 权限查询控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/readable-tables")
    public Result<Set<String>> getReadableTables() {
        Long userId = LoginUser.currentUserId();
        return Result.success(permissionService.getReadableTables(userId));
    }

    @GetMapping("/writable-tables")
    public Result<Set<String>> getWritableTables() {
        Long userId = LoginUser.currentUserId();
        return Result.success(permissionService.getWritableTables(userId));
    }

    @GetMapping("/check")
    public Result<Boolean> checkPermission(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "READ") String type) {
        Long userId = LoginUser.currentUserId();
        boolean hasPermission;
        if ("WRITE".equalsIgnoreCase(type)) {
            hasPermission = permissionService.hasWritePermission(userId, tableName);
        } else {
            hasPermission = permissionService.hasReadPermission(userId, tableName);
        }
        return Result.success(hasPermission);
    }

}
