package com.shop.online.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final String BASE_SELECT =
            "SELECT o.id, o.order_no, o.customer_id, c.username AS buyer_name, c.phone AS buyer_phone, " +
            "o.status, o.total_amount, o.trade_place, o.cancelled_by, o.created_at " +
            "FROM orders o JOIN customer c ON c.id = o.customer_id ";

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OrderRow(long id,
                           String orderNo,
                           long customerId,
                           String buyerName,
                           String buyerPhone,
                           String status,
                           BigDecimal totalAmount,
                           String tradePlace,
                           String cancelledBy,
                           LocalDateTime createdAt) {
    }

    public record OrderItemRow(long orderId,
                               long productId,
                               String productName,
                               BigDecimal price,
                               int quantity,
                               String imageUrl) {
    }

    public record OrderLockRow(long id, long customerId, String status) {
    }

    private static final RowMapper<OrderRow> ROW_MAPPER = (rs, i) -> new OrderRow(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("customer_id"),
            rs.getString("buyer_name"),
            rs.getString("buyer_phone"),
            rs.getString("status"),
            rs.getBigDecimal("total_amount"),
            rs.getString("trade_place"),
            rs.getString("cancelled_by"),
            rs.getTimestamp("created_at").toLocalDateTime());

    public Optional<OrderRow> findById(long id) {
        List<OrderRow> list = jdbc.query(BASE_SELECT + "WHERE o.id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<OrderRow> findByCustomer(long customerId) {
        return jdbc.query(BASE_SELECT + "WHERE o.customer_id = ? ORDER BY o.id DESC",
                ROW_MAPPER, customerId);
    }

    public List<OrderRow> findAll() {
        return jdbc.query(BASE_SELECT + "ORDER BY o.id DESC", ROW_MAPPER);
    }

    public List<OrderItemRow> findItems(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT order_id, product_id, product_name, price, quantity, image_url " +
                "FROM order_item WHERE order_id IN (" + placeholders(orderIds.size()) + ") ORDER BY id";
        return jdbc.query(sql, (rs, i) -> new OrderItemRow(
                rs.getLong("order_id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getString("image_url")), orderIds.toArray());
    }

    public long insertOrder(String orderNo, long customerId, String status,
                            BigDecimal totalAmount, String tradePlace) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO orders (order_no, customer_id, status, total_amount, trade_place) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setLong(2, customerId);
            ps.setString(3, status);
            ps.setBigDecimal(4, totalAmount);
            ps.setString(5, tradePlace);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void insertItem(long orderId, long productId, String productName,
                           BigDecimal price, int quantity, String imageUrl) {
        jdbc.update("INSERT INTO order_item (order_id, product_id, product_name, price, quantity, image_url) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                orderId, productId, productName, price, quantity, imageUrl);
    }

    public Optional<OrderLockRow> lockById(long id) {
        List<OrderLockRow> list = jdbc.query(
                "SELECT id, customer_id, status FROM orders WHERE id = ? FOR UPDATE",
                (rs, i) -> new OrderLockRow(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("status")), id);
        return list.stream().findFirst();
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", status, id);
    }

    public void cancel(long id, String cancelledBy, String reason) {
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancelled_by = ?, cancel_reason = ? WHERE id = ?",
                cancelledBy, reason, id);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
