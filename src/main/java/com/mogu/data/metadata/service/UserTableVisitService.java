package com.mogu.data.metadata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.metadata.entity.UserTableVisit;
import com.mogu.data.metadata.mapper.UserTableVisitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户表访问记录服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTableVisitService extends ServiceImpl<UserTableVisitMapper, UserTableVisit> {

    public void recordVisit(Long userId, String databaseName, String tableName) {
        if (userId == null || databaseName == null || tableName == null) {
            return;
        }
        LambdaQueryWrapper<UserTableVisit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTableVisit::getUserId, userId)
                .eq(UserTableVisit::getDatabaseName, databaseName)
                .eq(UserTableVisit::getTableName, tableName);
        UserTableVisit visit = getOne(wrapper);
        if (visit == null) {
            visit = new UserTableVisit();
            visit.setUserId(userId);
            visit.setDatabaseName(databaseName);
            visit.setTableName(tableName);
            visit.setVisitCount(1);
            visit.setLastVisitTime(LocalDateTime.now());
            save(visit);
        } else {
            visit.setVisitCount(visit.getVisitCount() + 1);
            visit.setLastVisitTime(LocalDateTime.now());
            updateById(visit);
        }
    }

    public List<UserTableVisit> listRecentVisits(Long userId, int limit) {
        LambdaQueryWrapper<UserTableVisit> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(UserTableVisit::getUserId, userId);
        }
        wrapper.orderByDesc(UserTableVisit::getLastVisitTime);
        wrapper.last("LIMIT " + limit);
        return list(wrapper);
    }

}
