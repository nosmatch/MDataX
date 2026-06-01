package com.mogu.data.common;

/**
 * 当前登录用户信息（ThreadLocal存储）
 *
 * @author fengzhu
 */
public class LoginUser {

    private Long userId;
    private String username;
    private boolean admin;

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public static void set(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser get() {
        return CURRENT_USER.get();
    }

    public static Long currentUserId() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getUserId() : null;
    }

    public static String currentUsername() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getUsername() : null;
    }

    public static boolean isCurrentAdmin() {
        LoginUser user = CURRENT_USER.get();
        return user != null && user.isAdmin();
    }

    public static void remove() {
        CURRENT_USER.remove();
    }

}
