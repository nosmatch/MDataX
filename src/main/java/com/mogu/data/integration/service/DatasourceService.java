package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.enums.DatasourceType;
import com.mogu.data.integration.mapper.DatasourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

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

    public boolean testConnection(Datasource ds) throws Exception {
        DatasourceType type = DatasourceType.of(ds.getType());
        if (type == null) {
            throw new IllegalArgumentException("未知的数据源类型: " + ds.getType());
        }
        switch (type) {
            case MYSQL:
                return testMySQLConnection(ds);
            case CLICKHOUSE:
                return testClickHouseConnection(ds);
            case ELASTICSEARCH:
                return testElasticsearchConnection(ds);
            case KAFKA:
                return testKafkaConnection(ds);
            case LOCAL_EXCEL:
                return testLocalExcelConnection(ds);
            default:
                throw new IllegalArgumentException("未知的数据源类型: " + ds.getType());
        }
    }

    private boolean testMySQLConnection(Datasource ds) throws Exception {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private boolean testClickHouseConnection(Datasource ds) throws Exception {
        String url = String.format("jdbc:clickhouse://%s:%d/%s",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private boolean testElasticsearchConnection(Datasource ds) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        Request.Builder builder = new Request.Builder()
                .url("http://" + ds.getHost() + ":" + ds.getPort());
        boolean hasAuth = StringUtils.hasText(ds.getUsername()) && StringUtils.hasText(ds.getPassword());
        if (hasAuth) {
            String credential = okhttp3.Credentials.basic(ds.getUsername(), ds.getPassword());
            builder.header("Authorization", credential);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 401) {
                if (!hasAuth) {
                    throw new IllegalArgumentException("ES需要认证，请填写用户名和密码");
                } else {
                    throw new IllegalArgumentException("ES认证失败，请检查用户名和密码");
                }
            }
            return response.isSuccessful();
        }
    }

    private boolean testKafkaConnection(Datasource ds) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ds.getHost(), ds.getPort()), 5000);
            return socket.isConnected();
        }
    }

    private boolean testLocalExcelConnection(Datasource ds) {
        String filePath = ds.getExtraConfig();
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    public void validateDatasource(Datasource ds) {
        if (!StringUtils.hasText(ds.getType())) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        if (!DatasourceType.isValid(ds.getType())) {
            throw new IllegalArgumentException("不支持的数据源类型: " + ds.getType());
        }
        DatasourceType type = DatasourceType.of(ds.getType());
        if (type == DatasourceType.LOCAL_EXCEL) {
            if (!StringUtils.hasText(ds.getExtraConfig())) {
                throw new IllegalArgumentException("本地Excel数据源需要配置 extraConfig（文件路径）");
            }
            return;
        }
        if (!StringUtils.hasText(ds.getHost())) {
            throw new IllegalArgumentException("主机地址不能为空");
        }
        if (ds.getPort() == null) {
            throw new IllegalArgumentException("端口不能为空");
        }
        if (type == DatasourceType.MYSQL || type == DatasourceType.CLICKHOUSE) {
            if (!StringUtils.hasText(ds.getDatabaseName())) {
                throw new IllegalArgumentException("数据库名不能为空");
            }
            if (!StringUtils.hasText(ds.getUsername())) {
                throw new IllegalArgumentException("用户名不能为空");
            }
            if (!StringUtils.hasText(ds.getPassword())) {
                throw new IllegalArgumentException("密码不能为空");
            }
        }
    }

    public void createDatasource(Datasource ds) {
        validateDatasource(ds);
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
