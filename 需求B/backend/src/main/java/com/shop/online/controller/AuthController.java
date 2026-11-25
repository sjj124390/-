package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 登录与注册。
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/admin/login")
    public Result<Views.LoginResult> adminLogin(@Valid @RequestBody Requests.Login request) {
        return Result.ok(authService.loginSeller(request));
    }

    @PostMapping("/customers/login")
    public Result<Views.LoginResult> customerLogin(@Valid @RequestBody Requests.Login request) {
        return Result.ok(authService.loginCustomer(request));
    }

    @PostMapping("/customers/register")
    public Result<Views.LoginResult> register(@Valid @RequestBody Requests.Register request) {
        return Result.ok(authService.register(request));
    }
}
