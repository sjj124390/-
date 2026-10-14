package com.shop.online.exception;

import com.shop.online.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 MySQL 存储过程抛出的业务错误
     * 组员C 的 4 个 SP 统一使用 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='xxx' 抛错
     * JDBC 会抛 SQLException，SQLState="45000"，getMessage() 包含 MESSAGE_TEXT
     * 这里提取后转为标准业务异常返回前端
     */
    @ExceptionHandler(SQLException.class)
    public Result<Void> handleSQLException(SQLException e) {
        String state = e.getSQLState();
        log.warn("SQLException sqlState={} vendorCode={} msg={}", state, e.getErrorCode(), e.getMessage());
        if ("45000".equals(state)) {
            // MySQL SP 抛 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='xxx'
            // Connector/J 把 MESSAGE_TEXT 可能放在不同位置，这里尽力提取
            String cleanMsg = e.getMessage();
            if (cleanMsg != null) {
                int q1 = cleanMsg.indexOf("'");
                int q2 = cleanMsg.indexOf("'", q1 + 1);
                if (q1 >= 0 && q2 > q1) {
                    cleanMsg = cleanMsg.substring(q1 + 1, q2);
                }
            }
            log.warn("存储过程业务错误：{}", cleanMsg);
            return Result.error(45000, cleanMsg);
        }
        log.error("数据库异常（非存储过程业务错误）：", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }

    /** 处理参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验异常：{}", message);
        return Result.error(400, message);
    }

    /** 处理绑定异常 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        log.warn("参数绑定异常：{}", message);
        return Result.error(400, message);
    }

    /** 处理其它未知异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error("系统繁忙，请稍后再试");
    }
}
