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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

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
        // 使用当前 INSERT 的自增主键，避免并发发布时取到其他请求的商品。
        KeyHolder keys = new GeneratedKeyHolder();
        int inserted = jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO product(name, description, image_url, price) VALUES(?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, imageUrl);
            ps.setBigDecimal(4, price);
            return ps;
        }, keys);
        if (inserted != 1 || keys.getKey() == null) {
            throw new BusinessException(500, "商品草稿创建失败");
        }
        return findById(keys.getKey().longValue());
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

    /** 手动下架影响预览：供后台二次确认。下架时仍以条件 UPDATE 为准。 */
    @Override
    public Map<String, Object> withdrawPreview(Long productId) {
        Product p = findById(productId);
        if (p == null) {
            throw new BusinessException(1001, "商品不存在");
        }
        boolean allowed = "ON_SALE".equals(p.getStatus());
        Integer count = allowed ? jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_intent WHERE product_id=? AND status='PENDING'",
                Integer.class, productId) : 0;
        int pending = count == null ? 0 : count;
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("productId", productId);
        preview.put("productStatus", p.getStatus());
        preview.put("allowed", allowed);
        preview.put("pendingIntentCount", pending);
        preview.put("willNotifyBuyers", false);
        if (allowed) {
            preview.put("message", "确定下架？将失效 " + pending
                    + " 个购买意向，且系统不会自动通知买家。");
        } else if ("FROZEN".equals(p.getStatus())) {
            preview.put("message", "商品交易中，请先标记交易结果后再下架");
        } else {
            preview.put("message", "仅在售商品可以手动下架");
        }
        return preview;
    }

    @Override
    @Transactional
    public void withdraw(Long productId) {
        int rows = jdbc.update(
                "UPDATE product SET status='OFF_SHELF' WHERE id=? AND status='ON_SALE'",
                productId);
        if (rows != 1) {
            // FROZEN 表示交易进行中：不能直接跳过交易结果将商品下架。
            throw new BusinessException(1004, "仅在售商品可以下架；交易中请先标记交易结果");
        }
        int cancelled = jdbc.update(
                "UPDATE purchase_intent SET status='CANCELLED' WHERE product_id=? AND status='PENDING'",
                productId);
        // 事务覆盖商品变更和意向失效；第二条语句失败会回滚商品状态。
        log.info("商品 [id={}] 手动下架，失效意向数={}", productId, cancelled);
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
