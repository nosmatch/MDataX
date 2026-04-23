package com.mogu.data.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mogu.data.common.LoginUser;
import com.mogu.data.system.entity.TaskCollaborator;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.mapper.TaskCollaboratorMapper;
import com.mogu.data.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务协作者服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCollaboratorService {

    private final TaskCollaboratorMapper collaboratorMapper;
    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * 添加协作者
     */
    @Transactional
    public void addCollaborator(Long taskId, String taskType, Long userId) {
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 检查是否已是协作者
        int count = collaboratorMapper.countByTaskAndUser(taskId, taskType, userId);
        if (count > 0) {
            throw new IllegalArgumentException("该用户已是协作者");
        }
        TaskCollaborator tc = new TaskCollaborator();
        tc.setTaskId(taskId);
        tc.setTaskType(taskType);
        tc.setUserId(userId);
        collaboratorMapper.insert(tc);
        log.info("为任务 {} {} 添加协作者 {}", taskId, taskType, userId);
    }

    /**
     * 删除协作者
     */
    @Transactional
    public void removeCollaborator(Long id) {
        collaboratorMapper.deleteById(id);
    }

    /**
     * 查询任务的协作者列表
     */
    public List<Map<String, Object>> listCollaborators(Long taskId, String taskType) {
        List<TaskCollaborator> list = collaboratorMapper.selectList(
                new LambdaQueryWrapper<TaskCollaborator>()
                        .eq(TaskCollaborator::getTaskId, taskId)
                        .eq(TaskCollaborator::getTaskType, taskType)
        );
        if (list.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> userIds = list.stream().map(TaskCollaborator::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return list.stream().map(tc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tc.getId());
            map.put("userId", tc.getUserId());
            User u = userMap.get(tc.getUserId());
            map.put("userName", u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "");
            map.put("createTime", tc.getCreateTime());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 判断用户是否为某任务的协作者
     */
    public boolean isCollaborator(Long taskId, String taskType, Long userId) {
        if (userId == null) {
            return false;
        }
        return collaboratorMapper.countByTaskAndUser(taskId, taskType, userId) > 0;
    }

    /**
     * 综合权限判定：创建人 / 协作者 / 管理员 均可操作
     */
    public boolean canOperate(Long taskId, String taskType, Long userId, Long createUserId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(createUserId)) {
            return true;
        }
        if (userService.isAdmin(userId)) {
            return true;
        }
        return isCollaborator(taskId, taskType, userId);
    }
}
