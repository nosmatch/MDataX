package com.mogu.data.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.common.LoginUser;
import com.mogu.data.system.entity.OperationLog;
import com.mogu.data.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 操作日志AOP切面
 *
 * @author fengzhu
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
              "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
              "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void writeOperation() {
    }

    @Pointcut("execution(* com.mogu.data..controller.*.*(..))")
    public void controllerPointcut() {
    }

    @Around("writeOperation() && controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getName() + "." + method.getName();

        Object[] args = joinPoint.getArgs();
        String params = truncate(toJson(args), 2000);

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        String ip = getIpAddress(request);

        Long userId = LoginUser.currentUserId();
        String username = LoginUser.currentUsername();

        String operation = getOperationDescription(method);

        Object result = null;
        String resultStr = "";
        int status = 1;

        try {
            result = joinPoint.proceed();
            resultStr = truncate(toJson(result), 2000);
        } catch (Throwable e) {
            status = 0;
            resultStr = truncate(e.getMessage(), 2000);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            OperationLog operationLog = new OperationLog();
            operationLog.setUserId(userId);
            operationLog.setUsername(username);
            operationLog.setOperation(operation);
            operationLog.setMethod(methodName);
            operationLog.setParams(params);
            operationLog.setResult(resultStr);
            operationLog.setIp(ip);
            operationLog.setDuration(duration);
            operationLog.setStatus(status);

            try {
                operationLogMapper.insert(operationLog);
            } catch (Exception e) {
                log.error("操作日志记录失败", e);
            }
        }

        return result;
    }

    private String getOperationDescription(Method method) {
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        if (className.contains("User")) {
            if (methodName.contains("create")) return "创建用户";
            if (methodName.contains("update")) return "修改用户";
            if (methodName.contains("delete")) return "删除用户";
            if (methodName.contains("assignRoles")) return "分配用户角色";
            return "用户操作";
        }
        if (className.contains("Role")) {
            if (methodName.contains("create")) return "创建角色";
            if (methodName.contains("update")) return "修改角色";
            if (methodName.contains("delete")) return "删除角色";
            if (methodName.contains("assignPermissions")) return "配置角色权限";
            return "角色操作";
        }
        if (className.contains("Auth")) {
            if (methodName.contains("login")) return "用户登录";
            if (methodName.contains("register")) return "用户注册";
            return "认证操作";
        }

        return className + "." + methodName;
    }

    private String getIpAddress(HttpServletRequest request) {
        if (request == null) return "";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

}
