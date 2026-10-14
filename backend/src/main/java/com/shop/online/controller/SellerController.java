package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.LoginDTO;
import com.shop.online.dto.UpdatePasswordDTO;
import com.shop.online.entity.Seller;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 卖家后台 - 登录 & 改密码
 * （组员C 建议接口：POST /api/admin/login、PUT /api/admin/password）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    /** 卖家登录 */
    @PostMapping("/login")
    public Result<Seller> login(@RequestBody @Validated LoginDTO dto) {
        return Result.success(sellerService.login(dto.getUsername(), dto.getPassword()));
    }

    /**
     * 修改密码
     * 组员C 数据库方案：校验旧密码 → 新密码 BCrypt 加密 → UPDATE seller.password_hash
     * 条件 UPDATE（带旧密码匹配），防止并发改密码
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Validated UpdatePasswordDTO dto) {
        // DTO 里还保留了两次新密码一致性校验（虽然 Service 层只校验旧密码）
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次新密码不一致");
        }
        sellerService.updatePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码已更新", null);
    }
}
