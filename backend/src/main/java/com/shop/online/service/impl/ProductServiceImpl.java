package com.shop.online.service.impl;

import com.shop.online.entity.Product;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.ProductService;
import com.shop.online.util.SpExceptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.List;

/**
 * 商品服务实现（配合组员C 数据库 + 存储过程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Product> PRODUCT_MAPPER = (rs, rowNum) -> {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setSellerId(rs.getInt("seller_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setImageUrl(rs.getString("image_url"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStatus(rs.getString("status"));
        Object activeSlot = rs.getObject("active_slot");
        p.setActiveSlot(activeSlot != null ? ((Number) activeSlot).intValue() : null);
        p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return p;
    };

    @Override
    public Product getCurrent() {
        List<Product> list = jdbc.query(
                "SELECT * FROM product WHERE status IN ('ON_SALE','FROZEN') ORDER BY id DESC LIMIT 1",
                PRODUCT_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Product createDraft(String name, String description, String imageUrl, BigDecimal price) {
        jdbc.update(
                "INSERT INTO product(name, description, image_url, price) VALUES(?, ?, ?, ?)",
                name, description, imageUrl, price);
        List<Product> list = jdbc.query(
                "SELECT * FROM product ORDER BY id DESC LIMIT 1", PRODUCT_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void publish(Long productId) {
        try {
            // 显式 ConnectionCallback 类型消歧义
            jdbc.execute((Connection con) -> {
                CallableStatement cs = con.prepareCall("{CALL publish_product(?)}");
                cs.setLong(1, productId);
                cs.execute();
                return null;
            });
            log.info("商品 [id={}] 已发布", productId);
        } catch (DataAccessException dae) {
            BusinessException be = SpExceptionUtil.extract(dae);
            throw be != null ? be : dae;
        }
    }

    @Override
    @Transactional
    public void withdraw(Long productId) {
        int rows = jdbc.update(
                "UPDATE product SET status='OFF_SHELF' WHERE id=? AND status='ON_SALE'",
                productId);
        if (rows != 1) {
            throw new BusinessException(500, "商品状态不允许下架");
        }
        jdbc.update(
                "UPDATE purchase_intent SET status='CANCELLED' WHERE product_id=? AND status='PENDING'",
                productId);
        log.info("商品 [id={}] 已下架", productId);
    }

    @Override
    public List<Product> history() {
        return jdbc.query("SELECT * FROM product ORDER BY created_at DESC", PRODUCT_MAPPER);
    }

    @Override
    public Product findById(Long productId) {
        List<Product> list = jdbc.query(
                "SELECT * FROM product WHERE id=?", PRODUCT_MAPPER, productId);
        return list.isEmpty() ? null : list.get(0);
    }
}
