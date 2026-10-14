package com.shop.online.service;

import com.shop.online.entity.Seller;

/**
 * 卖家服务
 *
 * 组员C 约束：seller.id 只能为 1（唯一卖家），通过 CHECK 约束保证
 * 后端登录固定查 id=1 的那条记录
 */
public interface SellerService {

    /**
     * 卖家登录（固定查 id=1，忽略传的 username 或做简单校验）
     * @param rawPassword 明文密码
     * @return 登录成功的 Seller（不带 passwordHash）
     */
    Seller login(String username, String rawPassword);

    /**
     * 修改密码
     * 校验旧密码 → 新密码 BCrypt 加密 → UPDATE
     */
    void updatePassword(String rawOld, String rawNew);

    /** 初始化唯一卖家（应用首次启动时调用一次） */
    void ensureInitialized(String username, String rawPassword);
}
