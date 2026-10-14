package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 结束交易请求（POST /api/admin/trades/{id}/finish）
 *
 * finish_trade SP 要求同时传入 product_id（固定锁顺序），
 * 所以客户端必须在请求体里带上 productId（虽然 trade.id 可单独定位交易）
 */
@Data
public class TradeFinishDTO {

    /** 固定锁顺序参数：必须与交易所属商品一致 */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** true 成交成功 / false 成交失败 */
    @NotNull(message = "结果不能为空")
    private Boolean success;

    /** 失败原因（成功时可为 null） */
    private String reason;
}
