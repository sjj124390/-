package com.shop.online.controller;

import com.shop.online.common.PageResult;
import com.shop.online.common.Result;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.service.CategoryService;
import com.shop.online.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 商家后台：商品发布与管理、两级类别管理。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public AdminProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public Result<PageResult<Views.ProductView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(productService.searchAdmin(keyword, categoryId, status, page, size));
    }

    @PostMapping("/products")
    public Result<Views.ProductView> create(@Valid @RequestBody Requests.ProductSave request) {
        return Result.ok(productService.create(request));
    }

    @PutMapping("/products/{id}")
    public Result<Views.ProductView> update(@PathVariable long id,
                                            @Valid @RequestBody Requests.ProductSave request) {
        return Result.ok(productService.update(id, request));
    }

    @PutMapping("/products/{id}/status")
    public Result<Views.ProductView> changeStatus(@PathVariable long id,
                                                  @Valid @RequestBody Requests.StatusChange request) {
        productService.changeStatus(id, request.status());
        return Result.ok(productService.get(id));
    }

    @GetMapping("/categories")
    public Result<List<Views.CategoryView>> categories() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping("/categories")
    public Result<Long> createCategory(@Valid @RequestBody Requests.CategorySave request) {
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable long id,
                                       @Valid @RequestBody Requests.CategorySave request) {
        categoryService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
