package com.shop.online.controller;

import com.shop.online.common.PageResult;
import com.shop.online.common.Result;
import com.shop.online.dto.Views;
import com.shop.online.service.CategoryService;
import com.shop.online.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 买家端商品浏览、搜索、类别树（无需登录）。
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public Result<PageResult<Views.ProductView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(productService.searchPublic(keyword, categoryId, page, size));
    }

    @GetMapping("/products/{id}")
    public Result<Views.ProductView> detail(@PathVariable long id) {
        return Result.ok(productService.get(id));
    }

    @GetMapping("/categories")
    public Result<List<Views.CategoryView>> categories() {
        return Result.ok(categoryService.tree());
    }
}
