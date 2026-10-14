package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.entity.Product;
import com.shop.online.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开接口 - 商品
 * （组员C 建议接口 GET /api/products/current）
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 查询当前商品（ON_SALE 或 FROZEN）
     * 有在售商品返回完整对象；冻结时也返回但前端按 status 标记不可购买
     * 无在售/冻结商品返回 code=200 data=null
     */
    @GetMapping("/current")
    public Result<Product> current() {
        Product p = productService.getCurrent();
        if (p == null) {
            return Result.error(1001, "暂无在售商品");
        }
        return Result.success(p);
    }
}
