package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 冻结请求（POST /api/admin/products/{id}/freeze）
 */
@Data
public class FreezeDTO {

    @NotNull(message = "意向ID不能为空")
    private Long intentId;
}
