package com.shop.online.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 所有响应视图对象，字段命名与前端 api.js 契约一致。
 */
public final class Views {

    private Views() {
    }

    public record LoginResult(String token, String role, String username, long id) {
    }

    public record ProductView(
            long id,
            String name,
            String description,
            BigDecimal price,
            int stock,
            String imageUrl,
            List<String> images,
            Long categoryId,
            String categoryName,
            String status,
            int sales,
            LocalDateTime createdAt) {
    }

    public record CartItemView(
            long productId,
            String name,
            BigDecimal price,
            int stock,
            int quantity,
            String imageUrl,
            String status) {
    }

    public record OrderItemView(
            long productId,
            String name,
            BigDecimal price,
            int quantity,
            String imageUrl) {
    }

    public record OrderView(
            long id,
            String orderNo,
            long customerId,
            String buyerName,
            String buyerPhone,
            String tradePlace,
            String status,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            List<OrderItemView> items,
            List<String> allowedActions,
            String cancelledBy) {
    }

    public record CategoryView(long id, String name, Long parentId, List<CategoryView> children) {
    }

    public record CustomerView(
            long id,
            String username,
            String phone,
            String defaultTradePlace,
            LocalDateTime createdAt,
            long orderCount,
            BigDecimal totalSpent) {
    }
}
