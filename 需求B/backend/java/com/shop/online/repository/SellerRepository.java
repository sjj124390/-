package com.shop.online.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SellerRepository {

    private final JdbcTemplate jdbc;

    public SellerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SellerRow(long id, String username, String passwordHash) {
    }

    private static final RowMapper<SellerRow> ROW_MAPPER = (rs, i) -> new SellerRow(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"));

    public Optional<SellerRow> findByUsername(String username) {
        List<SellerRow> list = jdbc.query(
                "SELECT id, username, password_hash FROM seller WHERE username = ?",
                ROW_MAPPER, username);
        return list.stream().findFirst();
    }

    public Optional<SellerRow> findById(long id) {
        List<SellerRow> list = jdbc.query(
                "SELECT id, username, password_hash FROM seller WHERE id = ?",
                ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM seller", Long.class);
        return count == null ? 0 : count;
    }

    public long insert(String username, String passwordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO seller (username, password_hash) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE seller SET password_hash = ? WHERE id = ?", passwordHash, id);
    }
}
