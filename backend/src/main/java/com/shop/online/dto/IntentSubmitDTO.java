package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 提交购买意向请求（POST /api/intents）
 */
@Data
public class IntentSubmitDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotBlank(message = "姓名不能为空")
    private String buyerName;

    @NotBlank(message = "联系方式不能为空")
    private String contact;

    private String note;
}
