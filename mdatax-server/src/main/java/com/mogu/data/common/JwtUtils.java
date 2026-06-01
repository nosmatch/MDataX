package com.mogu.data.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * JWT 工具类
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret:mdatax-secret-key-2024}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    private static String STATIC_SECRET;
    private static Long STATIC_EXPIRATION;

    @PostConstruct
    public void init() {
        STATIC_SECRET = secret;
        STATIC_EXPIRATION = expiration;
    }

    /**
     * 生成Token
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + STATIC_EXPIRATION);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, STATIC_SECRET)
                .compact();
    }

    /**
     * 从Token中获取用户ID
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从Token中获取用户名
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 解析Token
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(STATIC_SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否有效
     */
    public static boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(STATIC_SECRET).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断Token是否过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

}
