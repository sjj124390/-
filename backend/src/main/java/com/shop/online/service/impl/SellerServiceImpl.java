package com.shop.online.service.impl;

import com.shop.online.entity.Seller;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.SellerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 卖家服务实现
 *
 * 组员C 约束：seller.id 固定 1（唯一卖家）
 * 所有后台操作都查/改 id=1 那一条
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerServiceImpl implements SellerService {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final RowMapper<Seller> SELLER_MAPPER = (rs, rowNum) -> {
        Seller s = new Seller();
        s.setId(rs.getInt("id"));
        s.setUsername(rs.getString("username"));
        s.setPasswordHash(rs.getString("password_hash"));
        s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        s.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return s;
    };

    private Seller getOnlySeller() {
        List<Seller> list = jdbc.query(
                "SELECT * FROM seller WHERE id=1", SELLER_MAPPER);
        if (list.isEmpty()) {
            throw new BusinessException(3003, "卖家账号未初始化");
        }
        return list.get(0);
    }

    @Override
    public Seller login(String username, String rawPassword) {
        Seller seller = getOnlySeller();
        if (!passwordEncoder.matches(rawPassword, seller.getPasswordHash())) {
            throw new BusinessException(3001, "账号或密码错误");
        }
        // 返回前清掉密码哈希，不暴露给前端
        Seller safe = new Seller();
        safe.setId(seller.getId());
        safe.setUsername(seller.getUsername());
        safe.setCreatedAt(seller.getCreatedAt());
        safe.setUpdatedAt(seller.getUpdatedAt());
        log.info("卖家登录成功：username={}", seller.getUsername());
        return safe;
    }

    @Override
    public void updatePassword(String rawOld, String rawNew) {
        Seller seller = getOnlySeller();
        if (!passwordEncoder.matches(rawOld, seller.getPasswordHash())) {
            throw new BusinessException(3002, "原密码错误");
        }
        String newHash = passwordEncoder.encode(rawNew);
        // 条件 UPDATE：只在旧密码仍匹配时更新（防止并发改密码）
        int rows = jdbc.update(
                "UPDATE seller SET password_hash=? WHERE id=1 AND password_hash=?",
                newHash, seller.getPasswordHash());
        if (rows != 1) {
            throw new BusinessException(3002, "原密码校验失败");
        }
        log.info("卖家密码修改成功");
    }

    @Override
    public void ensureInitialized(String username, String rawPassword) {
        List<Seller> list = jdbc.query(
                "SELECT * FROM seller WHERE id=1", SELLER_MAPPER);
        if (!list.isEmpty()) {
            log.info("卖家账号已存在，跳过初始化");
            return;
        }
        String hash = passwordEncoder.encode(rawPassword);
        jdbc.update(
                "INSERT INTO seller(id, username, password_hash) VALUES(1, ?, ?)",
                username, hash);
        log.info("卖家账号初始化成功：username={}", username);
    }
}
