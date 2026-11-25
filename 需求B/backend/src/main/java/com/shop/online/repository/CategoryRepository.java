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
public class CategoryRepository {

    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CategoryRow(long id, String name, Long parentId, int sortOrder) {
    }

    private static final RowMapper<CategoryRow> ROW_MAPPER = (rs, i) -> {
        long parent = rs.getLong("parent_id");
        Long parentId = rs.wasNull() ? null : parent;
        return new CategoryRow(
                rs.getLong("id"),
                rs.getString("name"),
                parentId,
                rs.getInt("sort_order"));
    };

    public List<CategoryRow> findAll() {
        return jdbc.query(
                "SELECT id, name, parent_id, sort_order FROM category ORDER BY sort_order, id",
                ROW_MAPPER);
    }

    public Optional<CategoryRow> findById(long id) {
        List<CategoryRow> list = jdbc.query(
                "SELECT id, name, parent_id, sort_order FROM category WHERE id = ?",
                ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<CategoryRow> findByParentId(long parentId) {
        return jdbc.query(
                "SELECT id, name, parent_id, sort_order FROM category WHERE parent_id = ? ORDER BY sort_order, id",
                ROW_MAPPER, parentId);
    }

    public long insert(String name, Long parentId, int sortOrder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO category (name, parent_id, sort_order) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            if (parentId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, parentId);
            }
            ps.setInt(3, sortOrder);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void update(long id, String name, Long parentId, int sortOrder) {
        jdbc.update("UPDATE category SET name = ?, parent_id = ?, sort_order = ? WHERE id = ?",
                name, parentId, sortOrder, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM category WHERE id = ?", id);
    }

    public int countChildren(long parentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM category WHERE parent_id = ?", Integer.class, parentId);
        return count == null ? 0 : count;
    }

    public int countProducts(long categoryId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE category_id = ?", Integer.class, categoryId);
        return count == null ? 0 : count;
    }
}
