package com.shop.online.service.impl;

import com.shop.online.entity.PurchaseIntent;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.IntentService;
import com.shop.online.util.PasscodeUtil;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购买意向服务实现（配合组员C submit_intent 存储过程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentServiceImpl implements IntentService {

    private final JdbcTemplate jdbc;

    private static final RowMapper<PurchaseIntent> INTENT_MAPPER = (rs, rowNum) -> {
        PurchaseIntent i = new PurchaseIntent();
        i.setId(rs.getLong("id"));
        i.setProductId(rs.getLong("product_id"));
        i.setBuyerName(rs.getString("buyer_name"));
        i.setContact(rs.getString("contact"));
        i.setNote(rs.getString("note"));
        i.setCodeHash(rs.getBytes("code_hash"));
        Timestamp exp = rs.getTimestamp("code_expires_at");
        i.setCodeExpiresAt(exp != null ? exp.toLocalDateTime() : null);
        i.setStatus(rs.getString("status"));
        i.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        i.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return i;
    };

    @Override
    public Map<String, Object> submitIntent(Long productId, String name, String contact, String note) {
        String rawCode = PasscodeUtil.generateRawCode();
        byte[] codeHash = PasscodeUtil.computeHash(rawCode);
        LocalDateTime expiry = PasscodeUtil.defaultExpiry();

        Long intentId;
        try {
            intentId = jdbc.execute((Connection con) -> {
                CallableStatement cs = con.prepareCall("{CALL submit_intent(?, ?, ?, ?, ?, ?)}");
                cs.setLong(1, productId);
                cs.setString(2, name);
                cs.setString(3, contact);
                cs.setString(4, note);
                cs.setBytes(5, codeHash);
                cs.setTimestamp(6, Timestamp.valueOf(expiry));
                cs.execute();
                ResultSet rs = cs.getResultSet();
                if (rs != null && rs.next()) return rs.getLong("intent_id");
                throw new RuntimeException("submit_intent 未返回 intent_id");
            });
        } catch (DataAccessException dae) {
            BusinessException be = SpExceptionUtil.extract(dae);
            throw be != null ? be : dae;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("intentId", intentId);
        result.put("rawCode", rawCode);
        result.put("expiresAt", expiry);
        log.info("提交意向成功：intentId={}, productId={}", intentId, productId);
        return result;
    }

    @Override
    public Map<String, Object> lookupByRawCode(String rawCode) {
        byte[] hash = PasscodeUtil.computeHash(rawCode);
        String hexHash = bytesToHex(hash);

        String sql = "SELECT i.id AS intent_id, i.status AS intent_status, "
                + "i.code_expires_at, "
                + "p.name AS product_name, p.status AS product_status "
                + "FROM purchase_intent i "
                + "JOIN product p ON p.id = i.product_id "
                + "WHERE HEX(i.code_hash) = ? "
                + "
                + "ORDER BY i.id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, hexHash);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "口令码无效或已过期");
        }
        Map<String, Object> result = rows.get(0);
        String status = String.valueOf(result.get("intent_status"));
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status)) {
            throw new BusinessException(410, "口令码已失效");
        }
        Object exp = result.get("code_expires_at");
        LocalDateTime expiresAt = exp instanceof LocalDateTime
                ? (LocalDateTime) exp
                : ((Timestamp) exp).toLocalDateTime();
        if (!expiresAt.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new BusinessException(410, "口令码已失效");
        }
        // 仅返回商品和状态，不暴露买家联系方式或口令摘要。
        result.remove("code_expires_at");
        return result;
    }

    @Override
    public List<PurchaseIntent> listByProduct(Long productId) {
        return jdbc.query(
                "SELECT * FROM purchase_intent WHERE product_id=? ORDER BY id DESC",
                INTENT_MAPPER, productId);
    }

    @Override
    public PurchaseIntent findById(Long intentId) {
        List<PurchaseIntent> list = jdbc.query(
                "SELECT * FROM purchase_intent WHERE id=?", INTENT_MAPPER, intentId);
        return list.isEmpty() ? null : list.get(0);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
