package com.shop.online.service.impl;

import com.shop.online.entity.OfflineTrade;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.TradeService;
import com.shop.online.util.SpExceptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

/**
 * 线下交易服务实现（配合组员C freeze_product / finish_trade 存储过程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final JdbcTemplate jdbc;

    private static final RowMapper<OfflineTrade> TRADE_MAPPER = (rs, rowNum) -> {
        OfflineTrade t = new OfflineTrade();
        t.setId(rs.getLong("id"));
        t.setProductId(rs.getLong("product_id"));
        t.setIntentId(rs.getLong("intent_id"));
        t.setAgreedPrice(rs.getBigDecimal("agreed_price"));
        t.setStatus(rs.getString("status"));
        Object pending = rs.getObject("pending_product");
        t.setPendingProduct(pending != null ? ((Number) pending).longValue() : null);
        t.setFailureReason(rs.getString("failure_reason"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        java.sql.Timestamp fin = rs.getTimestamp("finished_at");
        t.setFinishedAt(fin != null ? fin.toLocalDateTime() : null);
        return t;
    };

    @Override
    public Long freeze(Long productId, Long intentId) {
        try {
            Long tradeId = jdbc.execute((Connection con) -> {
                CallableStatement cs = con.prepareCall("{CALL freeze_product(?, ?)}");
                cs.setLong(1, productId);
                cs.setLong(2, intentId);
                cs.execute();
                ResultSet rs = cs.getResultSet();
                if (rs != null && rs.next()) return rs.getLong("trade_id");
                throw new RuntimeException("freeze_product 未返回 trade_id");
            });
            log.info("冻结成功：tradeId={}, productId={}, intentId={}", tradeId, productId, intentId);
            return tradeId;
        } catch (DataAccessException dae) {
            BusinessException be = SpExceptionUtil.extract(dae);
            throw be != null ? be : dae;
        }
    }

    @Override
    public void finish(Long productId, Long tradeId, boolean success, String reason) {
        try {
            jdbc.execute((Connection con) -> {
                CallableStatement cs = con.prepareCall("{CALL finish_trade(?, ?, ?, ?)}");
                cs.setLong(1, productId);
                cs.setLong(2, tradeId);
                cs.setBoolean(3, success);
                cs.setString(4, reason);
                cs.execute();
                return null;
            });
            log.info("交易 {}：tradeId={}, productId={}", success ? "成功" : "失败", tradeId, productId);
        } catch (DataAccessException dae) {
            BusinessException be = SpExceptionUtil.extract(dae);
            throw be != null ? be : dae;
        }
    }

    @Override
    public List<OfflineTrade> listPending() {
        return jdbc.query(
                "SELECT * FROM offline_trade WHERE status='PENDING' ORDER BY id DESC",
                TRADE_MAPPER);
    }

    @Override
    public OfflineTrade findById(Long tradeId) {
        List<OfflineTrade> list = jdbc.query(
                "SELECT * FROM offline_trade WHERE id=?", TRADE_MAPPER, tradeId);
        return list.isEmpty() ? null : list.get(0);
    }
}
