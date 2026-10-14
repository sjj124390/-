package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 创建草稿商品请求（POST /api/admin/products）
 */
@Data
public class ProductDraftDTO {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotBlank(message = "商品描述不能为空")
    private String description;

    @NotBlank(message = "商品图片地址不能为空")
    private String imageUrl;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;
}
