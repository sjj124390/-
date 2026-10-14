package com.shop.online.service;

import com.shop.online.entity.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务（配合组员C 数据库 + 存储过程）
 */
public interface ProductService {

    /**
     * 前台查询：返回当前可购买商品
     * ON_SALE 正常返回；FROZEN 返回但标记不可购买（不泄露买家）
     * 无在售/冻结商品时返回 null
     */
    Product getCurrent();

    /** 创建草稿商品（DRAFT 状态） */
    Product createDraft(String name, String description, String imageUrl, BigDecimal price);

    /**
     * 发布商品：调用存储过程 publish_product
     * SP 内会检查：唯一卖家存在 + 商品状态允许发布 + 无其他在售/冻结商品
     */
    void publish(Long productId);

    /**
     * 手动下架：当前在售商品 OFF_SHELF，同时取消该商品所有 PENDING 意向
     * 组员C 要求放在一个 JDBC 事务里（条件 UPDATE 检查影响行数）
     */
    void withdraw(Long productId);

    /** 商品历史（含已售、已下架、冻结、在售、草稿全部） */
    List<Product> history();

    /** 按 ID 查商品（后台用） */
    Product findById(Long productId);
}
