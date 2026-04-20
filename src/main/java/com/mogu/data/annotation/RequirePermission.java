package com.mogu.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 标注在Controller方法上，用于校验用户对指定表的权限
 *
 * @author fengzhu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 表名（支持SpEL表达式，从方法参数中获取）
     */
    String table() default "";

    /**
     * 权限类型：READ 或 WRITE
     */
    String type() default "READ";

}
