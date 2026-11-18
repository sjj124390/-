package com.shop.online.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class CartRepository {

    private static final String BASE_SELECT =
            "SELECT ci.product_id, p.name, p.price, p.stock, ci.quantity, p.image_url " +
            "FROM cart_item ci JOIN product p ON p.id = ci.product_id ";

    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CartRow(long productId,
                          String name,
                          BigDecimal price,
                          int stock,
                          int quantity,
                          String imageUrl) {
    }

    private static final RowMapper<CartRow> ROW_MAPPER = (rs, i) -> new CartRow(
            rs.getLong("product_id"),
            rs.getString("name"),
            rs.getBigDecimal("price"),
            rs.getInt("stock"),
            rs.getInt("quantity"),
            rs.getString("image_url"));

    public List<CartRow> list(long customerId) {
        return jdbc.query(BASE_SELECT + "WHERE ci.customer_id = ? ORDER BY ci.updated_at DESC, ci.id DESC",
                ROW_MAPPER, customerId);
    }

    public Optional<CartRow> find(long customerId, long productId) {
        List<CartRow> list = jdbc.query(
                BASE_SELECT + "WHERE ci.customer_id = ? AND ci.product_id = ?",
                ROW_MAPPER, customerId, productId);
        return list.stream().findFirst();
    }

    public List<CartRow> listByIds(long customerId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = BASE_SELECT + "WHERE ci.customer_id = ? AND ci.product_id IN ("
                + placeholders(productIds.size()) + ")";
        Object[] args = new Object[productIds.size() + 1];
        args[0] = customerId;
        for (int i = 0; i < productIds.size(); i++) {
            args[i + 1] = productIds.get(i);
        }
        return jdbc.query(sql, ROW_MAPPER, args);
    }

    public void insert(long customerId, long productId, int quantity) {
        jdbc.update("INSERT INTO cart_item (customer_id, product_id, quantity) VALUES (?, ?, ?)",
                customerId, productId, quantity);
    }

    public void updateQuantity(long customerId, long productId, int quantity) {
        jdbc.update("UPDATE cart_item SET quantity = ? WHERE customer_id = ? AND product_id = ?",
                quantity, customerId, productId);
    }

    public int delete(long customerId, long productId) {
        return jdbc.update("DELETE FROM cart_item WHERE customer_id = ? AND product_id = ?",
                customerId, productId);
    }

    public void deleteMany(long customerId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM cart_item WHERE customer_id = ? AND product_id IN ("
                + placeholders(productIds.size()) + ")";
        Object[] args = new Object[productIds.size() + 1];
        args[0] = customerId;
        for (int i = 0; i < productIds.size(); i++) {
            args[i + 1] = productIds.get(i);
        }
        jdbc.update(sql, args);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
