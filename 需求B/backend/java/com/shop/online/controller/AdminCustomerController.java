package com.shop.online.controller;

import com.shop.online.common.AuthUser;
import com.shop.online.common.Result;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.security.AuthInterceptor;
import com.shop.online.service.AuthService;
import com.shop.online.service.CustomerService;
import com.shop.online.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 商家后台：注册客户查询、购买历史、修改密码。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCustomerController {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final AuthService authService;

    public AdminCustomerController(CustomerService customerService,
                                   OrderService orderService,
                                   AuthService authService) {
        this.customerService = customerService;
        this.orderService = orderService;
        this.authService = authService;
    }

    @GetMapping("/customers")
    public Result<List<Views.CustomerView>> customers() {
        return Result.ok(customerService.listCustomers());
    }

    @GetMapping("/customers/{id}/orders")
    public Result<List<Views.OrderView>> customerOrders(@PathVariable long id) {
        return Result.ok(orderService.listByCustomerForSeller(id));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                       @Valid @RequestBody Requests.PasswordChange request) {
        authService.changeSellerPassword(user.id(), request);
        return Result.ok();
    }
}
