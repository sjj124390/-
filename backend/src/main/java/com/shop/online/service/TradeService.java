package com.shop.online.service;

import com.shop.online.entity.OfflineTrade;

import java.util.List;

/**
 * 线下交易服务（配合组员C freeze_product / finish_trade 存储过程）
 */
public interface TradeService {

    /**
     * 冻结商品（调用 SP freeze_product）
     * 选中一个 PENDING 意向，商品 FROZEN、意向 SELECTED、生成 PENDING 交易
     *
     * @return 生成的 offline_trade.id
     */
    Long freeze(Long productId, Long intentId);

    /**
     * 结束交易（调用 SP finish_trade）
     * @param success true 成交成功 / false 成交失败
     * @param reason  失败原因（成功时传 null）
     */
    void finish(Long productId, Long tradeId, boolean success, String reason);

    /** 待处理交易列表（status=PENDING） */
    List<OfflineTrade> listPending();

    /** 按 ID 查交易 */
    OfflineTrade findById(Long tradeId);
}
