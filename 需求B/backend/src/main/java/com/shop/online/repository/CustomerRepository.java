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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CustomerRow(long id,
                              String username,
                              String passwordHash,
                              String phone,
                              String defaultTradePlace,
                              LocalDateTime createdAt) {
    }

    public record CustomerStat(long id,
                               String username,
                               String phone,
                               String defaultTradePlace,
                               LocalDateTime createdAt,
                               long orderCount,
                               BigDecimal totalSpent) {
    }

    private static final RowMapper<CustomerRow> ROW_MAPPER = (rs, i) -> new CustomerRow(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("phone"),
            rs.getString("default_location"),
            rs.getTimestamp("created_at").toLocalDateTime());

    public Optional<CustomerRow> findByUsername(String username) {
        List<CustomerRow> list = jdbc.query(
                "SELECT * FROM customer WHERE username = ?",
                ROW_MAPPER, username);
        return list.stream().findFirst();
    }

    public Optional<CustomerRow> findById(long id) {
        List<CustomerRow> list = jdbc.query(
                "SELECT * FROM customer WHERE id = ?",
                ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<CustomerRow> lockById(long id) {
        return jdbc.query("SELECT * FROM customer WHERE id = ? FOR UPDATE", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public long insert(String username, String passwordHash, String phone, String defaultTradePlace) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO customer (username, password_hash, phone, default_location) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, phone);
            ps.setString(4, defaultTradePlace);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    /**
     * 商家查看所有注册客户及其购买统计（已取消订单不计入消费金额）。
     */
    public List<CustomerStat> findAllWithStats() {
        return jdbc.query(
                "SELECT c.id, c.username, c.phone, c.default_location, c.created_at, " +
                        "COUNT(o.id) AS order_count, " +
                        "COALESCE(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0 END), 0) AS total_spent " +
                        "FROM customer c LEFT JOIN customer_order o ON o.customer_id = c.id " +
                        "GROUP BY c.id, c.username, c.phone, c.default_location, c.created_at " +
                        "ORDER BY c.id DESC",
                (rs, i) -> new CustomerStat(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("phone"),
                        rs.getString("default_location"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getLong("order_count"),
                        rs.getBigDecimal("total_spent")));
    }
}
