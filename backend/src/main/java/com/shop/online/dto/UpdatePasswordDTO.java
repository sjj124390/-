package com.shop.online.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 修改密码请求
 */
@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
