package com.shop.online.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购买意向实体（对应组员C schema 的 purchase_intent 表）
 * status: PENDING / SELECTED / COMPLETED / CANCELLED / EXPIRED
 *
 * code_hash 是 HMAC-SHA256 摘要（32 字节 BINARY），数据库不存原始口令码
 * code_expires_at 默认 7 天后过期
 */
@Data
public class PurchaseIntent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 意向ID */
    private Long id;

    /** 关联商品ID */
    private Long productId;

    /** 购买人姓名 */
    private String buyerName;

    /** 联系方式（电话/其他，仅后台可见） */
    private String contact;

    /** 备注 */
    private String note;

    /** HMAC-SHA256 摘要（32 字节），Java 用 byte[] 映射 BINARY(32) */
    private byte[] codeHash;

    /** 口令码过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime codeExpiresAt;

    /**
     * 意向状态（ENUM）：
     *   PENDING   - 待卖家选中
     *   SELECTED  - 已被选中冻结
     *   COMPLETED - 交易成功
     *   CANCELLED - 交易失败或其他意向因成功交易被批量取消
     *   EXPIRED   - 过期（可选清理状态）
     */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
