package com.shop.online.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线下交易实体（对应组员C schema 的 offline_trade 表）
 * status: PENDING / SUCCESS / FAILED
 *
 * pending_product 是数据库生成列：CASE WHEN status='PENDING' THEN product_id ELSE NULL END
 * 配合 UNIQUE KEY uq_pending_trade 保证同一商品最多一笔待处理交易
 *
 * CHECK 约束：PENDING 时 finished_at 必须为 NULL；SUCCESS/FAILED 时 finished_at 必须非空
 */
@Data
public class OfflineTrade implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交易ID */
    private Long id;

    /** 关联商品ID */
    private Long productId;

    /** 关联意向ID */
    private Long intentId;

    /** 冻结时的成交价快照（防止改价） */
    private BigDecimal agreedPrice;

    /**
     * 交易状态（ENUM）：
     *   PENDING - 待处理（冻结中）
     *   SUCCESS - 成交成功
     *   FAILED  - 成交失败
     */
    private String status;

    /** 数据库生成列，仅供查询 */
    private Long pendingProduct;

    /** 失败原因（仅 FAILED 状态有值） */
    private String failureReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
