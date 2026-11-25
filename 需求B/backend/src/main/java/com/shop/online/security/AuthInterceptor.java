package com.shop.online.security;

import com.shop.online.common.AuthUser;
import com.shop.online.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器：
 * /api/admin/**       需要卖家身份（/api/admin/login 除外）
 * /api/customers/me/** 需要客户身份
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "authUser";

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("请先登录");
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(header.substring(7).trim());
        } catch (Exception e) {
            throw BusinessException.unauthorized("登录已失效，请重新登录");
        }

        String role = claims.get("role", String.class);
        String path = request.getRequestURI();
        if (path.startsWith("/api/admin") && !AuthUser.ROLE_SELLER.equals(role)) {
            throw BusinessException.forbidden("该操作仅限商家");
        }
        if (path.startsWith("/api/customers/me") && !AuthUser.ROLE_CUSTOMER.equals(role)) {
            throw BusinessException.forbidden("请使用买家账号登录");
        }

        long userId = Long.parseLong(claims.getSubject());
        request.setAttribute(ATTR, new AuthUser(role, userId, claims.get("username", String.class)));
        return true;
    }
}
