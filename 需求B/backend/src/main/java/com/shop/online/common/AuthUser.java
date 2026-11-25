package com.shop.online.common;

/**
 * 登录用户上下文，由 AuthInterceptor 解析 JWT 后写入 request attribute。
 */
public record AuthUser(String role, long id, String username) {

    public static final String ROLE_SELLER = "SELLER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public boolean isSeller() {
        return ROLE_SELLER.equals(role);
    }
}
