package com.shop.online.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 所有请求体 DTO。
 */
public final class Requests {

    private Requests() {
    }

    public record Login(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record Register(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名长度需为 3-20 个字符") String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度需为 6-32 个字符") String password,
            @NotBlank(message = "电话不能为空")
            @Size(max = 20, message = "电话长度不能超过 20 个字符") String phone,
            @NotBlank(message = "默认交易地点不能为空")
            @Size(max = 200, message = "默认交易地点不能超过 200 个字符") String defaultTradePlace) {
    }

    public record ProductSave(
            @NotBlank(message = "商品名称不能为空")
            @Size(max = 100, message = "商品名称不能超过 100 个字符") String name,
            @NotNull(message = "商品价格不能为空")
            @DecimalMin(value = "0.01", message = "商品价格必须大于 0") BigDecimal price,
            @NotNull(message = "库存数量不能为空")
            @Min(value = 0, message = "库存数量不能为负数") Integer stock,
            String description,
            String imageUrl,
            List<String> images,
            Long categoryId) {
    }

    public record CartAdd(
            @NotNull(message = "商品 ID 不能为空") Long productId,
            @Min(value = 1, message = "数量至少为 1") int quantity) {
    }

    public record CartUpdate(
            @Min(value = 1, message = "数量至少为 1") int quantity) {
    }

    public record Checkout(
            @NotEmpty(message = "请选择要下单的商品") List<Long> productIds,
            String tradePlace) {
    }

    public record StatusChange(
            @NotBlank(message = "目标状态不能为空") String status) {
    }

    public record CategorySave(
            @NotBlank(message = "类别名称不能为空")
            @Size(max = 50, message = "类别名称不能超过 50 个字符") String name,
            Long parentId,
            Integer sortOrder) {
    }

    public record PasswordChange(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "新密码长度需为 6-32 个字符") String newPassword) {
    }

    public record CartToFavorite(
            @NotEmpty(message = "请选择要收藏的商品") List<Long> productIds) {
    }
}
