package com.shop.online.util;

import com.shop.online.exception.BusinessException;

import java.sql.SQLException;

/**
 * 组员C 存储过程异常解包工具
 *
 * JdbcTemplate 会把 SQLException 包装成 DataAccessException，
 * 这里递归追溯 cause 链，找到 SIGNAL SQLSTATE '45000' 的业务错误，
 * 提取 MESSAGE_TEXT 后包装成 BusinessException 抛出，
 * 让 GlobalExceptionHandler 捕获并返回给前端。
 */
public final class SpExceptionUtil {

    private SpExceptionUtil() {}

    /**
     * 从异常 cause 链里找 SQLException(SQLSTATE='45000')
     * @return 业务异常；找不到时返回 null（调用方继续 throw 原异常）
     */
    public static BusinessException extract(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SQLException) {
                SQLException se = (SQLException) cur;
                if ("45000".equals(se.getSQLState())) {
                    return new BusinessException(45000, extractMessage(se.getMessage()));
                }
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static String extractMessage(String sqlMsg) {
        if (sqlMsg == null) return "存储过程业务错误";
        // MySQL Connector/J 8 格式：`xxx for schema.table at line N`，
        // SIGNAL 的 MESSAGE_TEXT 在单引号对里（有时就是完整的 message）
        int q1 = sqlMsg.indexOf("'");
        int q2 = sqlMsg.indexOf("'", q1 + 1);
        if (q1 >= 0 && q2 > q1) return sqlMsg.substring(q1 + 1, q2);
        return sqlMsg;
    }
}
