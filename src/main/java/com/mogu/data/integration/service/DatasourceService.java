package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.mapper.DatasourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据源服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService extends ServiceImpl<DatasourceMapper, Datasource> {

    public Page<Datasource> pageDatasources(String keyword, long page, long size) {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Datasource::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Datasource::getName, keyword);
        }
        wrapper.orderByDesc(Datasource::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public boolean testConnection(Datasource ds) {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception e) {
            log.warn("数据源连接测试失败: {} - {}", ds.getName(), e.getMessage());
            return false;
        }
    }

    public void createDatasource(Datasource ds) {
        if (lambdaQuery().eq(Datasource::getName, ds.getName()).eq(Datasource::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("数据源名称已存在");
        }
        ds.setStatus(1);
        save(ds);
    }

    public void updateDatasource(Datasource ds) {
        Datasource exist = getById(ds.getId());
        if (exist == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        if (StringUtils.hasText(ds.getName()) && !exist.getName().equals(ds.getName())) {
            if (lambdaQuery().eq(Datasource::getName, ds.getName()).eq(Datasource::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("数据源名称已存在");
            }
        }
        updateById(ds);
    }

}
