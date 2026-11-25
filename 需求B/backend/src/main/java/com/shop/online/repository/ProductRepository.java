package com.shop.online.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ProductRepository {

    private static final String BASE_SELECT =
            "SELECT p.id, p.name, p.description, p.price, p.stock, p.image_url, p.category_id, " +
            "c.name AS category_name, p.status, p.off_shelf_reason, p.sales, p.created_at " +
            "FROM product p LEFT JOIN category c ON c.id = p.category_id";

    private final JdbcTemplate jdbc;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProductRow(long id,
                             String name,
                             String description,
                             BigDecimal price,
                             int stock,
                             String imageUrl,
                             Long categoryId,
                             String categoryName,
                             String status,
                             String offShelfReason,
                             int sales,
                             LocalDateTime createdAt) {
    }

    public record ProductLockRow(long id,
                                 String name,
                                 BigDecimal price,
                                 int stock,
                                 int sales,
                                 String status,
                                 String offShelfReason,
                                 String imageUrl) {
    }

    static final RowMapper<ProductRow> ROW_MAPPER = (rs, i) -> {
        long categoryId = rs.getLong("category_id");
        Long category = rs.wasNull() ? null : categoryId;
        return new ProductRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("image_url"),
                category,
                rs.getString("category_name"),
                rs.getString("status"),
                rs.getString("off_shelf_reason"),
                rs.getInt("sales"),
                rs.getTimestamp("created_at").toLocalDateTime());
    };

    public Optional<ProductRow> findById(long id) {
        List<ProductRow> list = jdbc.query(BASE_SELECT + " WHERE p.id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<ProductRow> search(String keyword, List<Long> categoryIds, String status, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = buildFilter(keyword, categoryIds, status, args);
        String sql = BASE_SELECT + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql, ROW_MAPPER, args.toArray());
    }

    public long count(String keyword, List<Long> categoryIds, String status) {
        List<Object> args = new ArrayList<>();
        String where = buildFilter(keyword, categoryIds, status, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM product p" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private String buildFilter(String keyword, List<Long> categoryIds, String status, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (p.name LIKE ? OR p.description LIKE ?)");
            args.add(like);
            args.add(like);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            where.append(" AND p.category_id IN (").append(placeholders(categoryIds.size())).append(")");
            args.addAll(categoryIds);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = ?");
            args.add(status);
        }
        return where.toString();
    }

    /**
     * 查询商品图片，按商品分组，避免列表接口出现 N+1 查询。
     */
    public Map<Long, List<String>> findImages(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT product_id, url FROM product_image WHERE product_id IN ("
                + placeholders(productIds.size()) + ") ORDER BY sort_order, id";
        return jdbc.query(sql, rs -> {
            Map<Long, List<String>> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.computeIfAbsent(rs.getLong("product_id"), key -> new ArrayList<>())
                        .add(rs.getString("url"));
            }
            return map;
        }, productIds.toArray());
    }

    public long insert(String name, String description, BigDecimal price, int stock,
                       String imageUrl, Long categoryId, String status, String offShelfReason) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO product (name, description, price, stock, image_url, category_id, status, off_shelf_reason) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBigDecimal(3, price);
            ps.setInt(4, stock);
            ps.setString(5, imageUrl);
            if (categoryId == null) {
                ps.setNull(6, Types.BIGINT);
            } else {
                ps.setLong(6, categoryId);
            }
            ps.setString(7, status);
            ps.setString(8, offShelfReason);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void update(long id, String name, String description, BigDecimal price, int stock,
                       String imageUrl, Long categoryId, String status, String offShelfReason) {
        jdbc.update(
                "UPDATE product SET name = ?, description = ?, price = ?, stock = ?, image_url = ?, " +
                        "category_id = ?, status = ?, off_shelf_reason = ? WHERE id = ?",
                name, description, price, stock, imageUrl, categoryId, status, offShelfReason, id);
    }

    public void updateStatus(long id, String status, String offShelfReason) {
        jdbc.update("UPDATE product SET status = ?, off_shelf_reason = ? WHERE id = ?",
                status, offShelfReason, id);
    }

    public void updateStockAndSales(long id, int stock, int sales, String status, String offShelfReason) {
        jdbc.update("UPDATE product SET stock = ?, sales = ?, status = ?, off_shelf_reason = ? WHERE id = ?",
                stock, sales, status, offShelfReason, id);
    }

    /**
     * 按主键升序加行锁，调用方需保证 ids 已去重并排序，避免死锁。
     */
    public List<ProductLockRow> lockByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT id, name, price, stock, sales, status, off_shelf_reason, image_url " +
                "FROM product WHERE id IN (" + placeholders(ids.size()) + ") ORDER BY id FOR UPDATE";
        return jdbc.query(sql, (rs, i) -> new ProductLockRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getInt("sales"),
                rs.getString("status"),
                rs.getString("off_shelf_reason"),
                rs.getString("image_url")), ids.toArray());
    }

    public void replaceImages(long productId, List<String> images) {
        jdbc.update("DELETE FROM product_image WHERE product_id = ?", productId);
        if (images == null || images.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            batch.add(new Object[]{productId, images.get(i), i});
        }
        jdbc.batchUpdate("INSERT INTO product_image (product_id, url, sort_order) VALUES (?, ?, ?)", batch);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
