package com.shop.online.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class FavoriteRepository {

    private final JdbcTemplate jdbc;

    public FavoriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等收藏：重复收藏不报错。
     */
    public void add(long customerId, long productId) {
        jdbc.update("INSERT INTO favorite (customer_id, product_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE created_at = created_at", customerId, productId);
    }

    public int remove(long customerId, long productId) {
        return jdbc.update("DELETE FROM favorite WHERE customer_id = ? AND product_id = ?",
                customerId, productId);
    }

    public List<ProductRepository.ProductRow> findProducts(long customerId) {
        return jdbc.query(
                "SELECT p.id, p.name, p.description, p.price, p.stock, p.image_url, p.category_id, " +
                        "c.name AS category_name, p.status, p.off_shelf_reason, p.sales, p.created_at " +
                        "FROM favorite f JOIN product p ON p.id = f.product_id " +
                        "LEFT JOIN category c ON c.id = p.category_id " +
                        "WHERE f.customer_id = ? ORDER BY f.created_at DESC, f.product_id DESC",
                ProductRepository.ROW_MAPPER, customerId);
    }

    public List<Long> findProductIds(long customerId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT product_id FROM favorite WHERE customer_id = ? ORDER BY created_at DESC",
                Long.class, customerId);
        return ids == null ? Collections.emptyList() : ids;
    }
}
