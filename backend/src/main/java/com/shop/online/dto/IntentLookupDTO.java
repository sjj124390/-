package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 口令码查询请求（POST /api/intents/lookup）
 */
@Data
public class IntentLookupDTO {

    @NotBlank(message = "口令码不能为空")
    private String code;
}
