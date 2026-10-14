package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.IntentLookupDTO;
import com.shop.online.dto.IntentSubmitDTO;
import com.shop.online.service.IntentService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 前台公开接口 - 购买意向 & 口令码查询
 * （组员C 建议接口：POST /api/intents、POST /api/intents/lookup）
 */
@RestController
@RequestMapping("/api/intents")
@RequiredArgsConstructor
public class IntentController {

    private final IntentService intentService;

    /**
     * 提交购买意向
     * 成功后返回 intentId + 原始口令码（仅前端展示一次提醒买家保存）
     * SP 内部保证：商品必须在售 + 口令码摘要唯一 + 不过期
     */
    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody @Validated IntentSubmitDTO dto) {
        Map<String, Object> result = intentService.submitIntent(
                dto.getProductId(),
                dto.getBuyerName(),
                dto.getContact(),
                dto.getNote());
        return Result.success("购买意向已提交，请保存您的口令码", result);
    }

    /**
     * 买家用口令码查询交易进度
     * 正确且未过期返回意向+商品状态（不泄露联系方式）
     */
    @PostMapping("/lookup")
    public Result<Map<String, Object>> lookup(@RequestBody @Validated IntentLookupDTO dto) {
        return Result.success(intentService.lookupByRawCode(dto.getCode()));
    }
}
