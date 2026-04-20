package com.mogu.data.interceptor;

import com.mogu.data.common.JwtUtils;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.ResultCode;
import com.mogu.data.entity.User;
import com.mogu.data.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 认证拦截器
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + ResultCode.UNAUTHORIZED.getCode() + ",\"message\":\"未登录或Token无效\"}");
            return false;
        }

        token = token.substring(7);

        if (!JwtUtils.validateToken(token) || JwtUtils.isTokenExpired(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + ResultCode.UNAUTHORIZED.getCode() + ",\"message\":\"Token已过期\"}");
            return false;
        }

        Long userId = JwtUtils.getUserIdFromToken(token);
        String username = JwtUtils.getUsernameFromToken(token);

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + ResultCode.UNAUTHORIZED.getCode() + ",\"message\":\"用户不存在或已禁用\"}");
            return false;
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(username);
        LoginUser.set(loginUser);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        LoginUser.remove();
    }

}
