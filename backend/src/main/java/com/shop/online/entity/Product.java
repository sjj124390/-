package com.shop.online.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（对应组员C schema 的 product 表）
 * status: DRAFT / ON_SALE / FROZEN / SOLD / OFF_SHELF
 *
 * active_slot 是数据库生成列：CASE WHEN status IN ('ON_SALE','FROZEN') THEN 1 ELSE NULL END
 * 配合 UNIQUE KEY uq_one_active_product 保证同一时间最多一件在售/冻结商品
 */
@Data
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long id;

    /** 卖家ID（固定 1） */
    private Integer sellerId;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 商品图片地址 */
    private String imageUrl;

    /** 商品价格 */
    private BigDecimal price;

    /**
     * 商品状态（ENUM）：
     *   DRAFT   - 草稿
     *   ON_SALE - 在售
     *   FROZEN  - 冻结（有买家选中）
     *   SOLD    - 已售出
     *   OFF_SHELF - 手动下架
     */
    private String status;

    /** 数据库生成列，仅供查询，不作为插入字段 */
    private Integer activeSlot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
