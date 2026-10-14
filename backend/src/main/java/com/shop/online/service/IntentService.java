package com.shop.online.service;

import com.shop.online.entity.PurchaseIntent;

import java.util.List;
import java.util.Map;

/**
 * 购买意向服务（配合组员C 数据库 + submit_intent 存储过程）
 */
public interface IntentService {

    /**
     * 提交购买意向（调用 SP submit_intent）
     * 后端自动生成口令码 + 计算 HMAC-SHA256 + 7 天过期
     *
     * @return Map 包含 intentId + 原始口令码（仅展示给买家一次）
     */
    Map<String, Object> submitIntent(Long productId, String name, String contact, String note);

    /**
     * 口令码查询（买家自助查询）
     * 返回商品状态和交易进度，不泄露联系方式
     */
    Map<String, Object> lookupByRawCode(String rawCode);

    /** 后台按商品查看意向名单（含买家信息，仅后台可见） */
    List<PurchaseIntent> listByProduct(Long productId);

    /** 按 ID 查意向（冻结时校验用） */
    PurchaseIntent findById(Long intentId);
}
