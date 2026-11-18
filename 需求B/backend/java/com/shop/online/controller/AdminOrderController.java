package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 商家订单管理：查看全部订单、推进状态、取消订单。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Result<List<Views.OrderView>> list() {
        return Result.ok(orderService.listAll());
    }

    @PutMapping("/{id}/status")
    public Result<Views.OrderView> changeStatus(@PathVariable long id,
                                                @Valid @RequestBody Requests.StatusChange request) {
        return Result.ok(orderService.changeStatus(id, request.status()));
    }

    @PostMapping("/{id}/cancel")
    public Result<Views.OrderView> cancel(@PathVariable long id) {
        return Result.ok(orderService.cancel(id, com.shop.online.common.AuthUser.ROLE_SELLER, null));
    }
}
