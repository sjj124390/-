package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.TradeFinishDTO;
import com.shop.online.entity.OfflineTrade;
import com.shop.online.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卖家后台 - 线下交易管理
 * （组员C 建议接口：POST /api/admin/trades/{id}/finish）
 */
@RestController
@RequestMapping("/api/admin/trades")
@RequiredArgsConstructor
public class AdminTradeController {

    private final TradeService tradeService;

    /**
     * 结束交易（调 SP finish_trade）
     *
     * 请求体必须同时带 productId —— 组员C 设计的 SP 要求固定锁顺序：
     * 先锁商品行，再锁交易行和意向行。如果只传 tradeId，SP 内部会用
     * 另一条查询拿到 product_id，但显式传入可以减少一次查询并保证锁顺序一致
     *
     * SP 保证：交易 PENDING + 商品 FROZEN + 结果合法（0 或 1）
     * 成功时：商品 SOLD + 意向 COMPLETED + 其他 PENDING 意向批量 CANCELLED
     * 失败时：商品恢复 ON_SALE + 选中意向 CANCELLED
     */
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id,
                               @RequestBody @Validated TradeFinishDTO dto) {
        tradeService.finish(dto.getProductId(), id, dto.getSuccess(), dto.getReason());
        String msg = dto.getSuccess() ? "交易成交成功" : "交易成交失败，商品已恢复在售";
        return Result.success(msg, null);
    }

    /** 待处理交易列表（辅助调试/演示用） */
    @GetMapping("/pending")
    public Result<List<OfflineTrade>> pending() {
        return Result.success(tradeService.listPending());
    }
}
