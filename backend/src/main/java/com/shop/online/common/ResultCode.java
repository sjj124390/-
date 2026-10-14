package com.shop.online.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // 商品相关
    PRODUCT_NOT_FOUND(1001, "商品不存在"),
    PRODUCT_NOT_ON_SALE(1002, "商品不在售，无法购买"),
    PRODUCT_ALREADY_ON_SALE(1003, "已有在售商品，同一时间仅允许一件商品在售"),
    PRODUCT_STATUS_ERROR(1004, "商品状态不允许该操作"),

    // 订单相关
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_STATUS_ERROR(2002, "订单状态不允许该操作"),

    // 卖家相关
    SELLER_LOGIN_FAIL(3001, "账号或密码错误"),
    SELLER_OLD_PWD_ERROR(3002, "原密码错误"),
    SELLER_NOT_FOUND(3003, "卖家账号不存在");

    private final int code;
    private final String message;
}
