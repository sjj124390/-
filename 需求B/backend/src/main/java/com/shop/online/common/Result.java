package com.shop.online.common;

/**
 * 统一响应体：{ code, message, data }，成功码固定为 200。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(200, "ok", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
