package com.mogu.data.system.controller;

import com.mogu.data.common.Result;
import com.mogu.data.system.service.TaskCollaboratorService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务协作者控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/task-collaborator")
@RequiredArgsConstructor
public class TaskCollaboratorController {

    private final TaskCollaboratorService collaboratorService;

    /**
     * 添加协作者
     */
    @PostMapping
    public Result<Void> add(@RequestBody AddRequest request) {
        collaboratorService.addCollaborator(request.getTaskId(), request.getTaskType(), request.getUserId());
        return Result.success();
    }

    /**
     * 删除协作者
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        collaboratorService.removeCollaborator(id);
        return Result.success();
    }

    /**
     * 查询任务的协作者列表
     */
    @GetMapping("/{taskId}/{taskType}")
    public Result<List<Map<String, Object>>> list(
            @PathVariable Long taskId,
            @PathVariable String taskType) {
        return Result.success(collaboratorService.listCollaborators(taskId, taskType));
    }

    @Data
    public static class AddRequest {
        private Long taskId;
        private String taskType;
        private Long userId;
    }
}
