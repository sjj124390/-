package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.FreezeDTO;
import com.shop.online.dto.ProductDraftDTO;
import com.shop.online.entity.Product;
import com.shop.online.entity.PurchaseIntent;
import com.shop.online.service.IntentService;
import com.shop.online.service.ProductService;
import com.shop.online.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卖家后台 - 商品管理
 * （组员C 建议接口：/api/admin/products/*）
 *
 * MVP 阶段暂未实现真正的鉴权，所有 /api/admin/* 接口可直接访问；
 * 后续升级可加 SellerAuthInterceptor 拦截器
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final IntentService intentService;
    private final TradeService tradeService;

    /** 创建草稿商品（不自动发布，卖家需显式调 publish） */
    @PostMapping
    public Result<Product> createDraft(@RequestBody @Validated ProductDraftDTO dto) {
        Product p = productService.createDraft(
                dto.getName(), dto.getDescription(), dto.getImageUrl(), dto.getPrice());
        return Result.success("草稿商品已创建", p);
    }

    /**
     * 发布商品（调 SP publish_product）
     * SP 保证：唯一卖家存在 + 商品状态允许 + 无其他在售/冻结
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        productService.publish(id);
        return Result.success();
    }

    /** 手动下架（当前在售商品 OFF_SHELF + 取消所有 PENDING 意向） */
    @PostMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        productService.withdraw(id);
        return Result.success();
    }

    /** 商品历史（含全部状态） */
    @GetMapping
    public Result<List<Product>> history() {
        return Result.success(productService.history());
    }

    /** 查看某商品的意向名单（含买家完整信息，仅后台可见） */
    @GetMapping("/{id}/intents")
    public Result<List<PurchaseIntent>> intents(@PathVariable Long id) {
        return Result.success(intentService.listByProduct(id));
    }

    /**
     * 选中一个 PENDING 意向冻结商品（调 SP freeze_product）
     * 请求体：{"intentId": 42}
     * SP 保证：商品在售 + 意向属于该商品 + 意向 PENDING + 口令码未过期
     * 返回新生成的 offline_trade.id
     */
    @PostMapping("/{id}/freeze")
    public Result<Long> freeze(@PathVariable Long id, @RequestBody @Validated FreezeDTO dto) {
        Long tradeId = tradeService.freeze(id, dto.getIntentId());
        return Result.success("已冻结，待处理交易创建成功", tradeId);
    }
}
