package com.mogu.data.integration.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 数据源类型枚举
 *
 * @author fengzhu
 */
@Getter
public enum DatasourceType {

    MYSQL("MySQL"),
    CLICKHOUSE("ClickHouse"),
    ELASTICSEARCH("Elasticsearch"),
    KAFKA("Kafka"),
    LOCAL_EXCEL("本地Excel");

    private final String label;

    DatasourceType(String label) {
        this.label = label;
    }

    public static boolean isValid(String type) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(t -> t.name().equalsIgnoreCase(type) || t.label.equalsIgnoreCase(type));
    }

    public static DatasourceType of(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(type) || t.label.equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }
}
