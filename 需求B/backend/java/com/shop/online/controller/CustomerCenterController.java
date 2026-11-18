package com.shop.online.controller;

import com.shop.online.common.AuthUser;
import com.shop.online.common.Result;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.security.AuthInterceptor;
import com.shop.online.service.CartService;
import com.shop.online.service.CustomerService;
import com.shop.online.service.OrderService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 买家个人中心：资料、收藏、购物车、订单。
 */
@RestController
@RequestMapping("/api/customers/me")
public class CustomerCenterController {

    private final CustomerService customerService;
    private final CartService cartService;
    private final OrderService orderService;

    public CustomerCenterController(CustomerService customerService,
                                    CartService cartService,
                                    OrderService orderService) {
        this.customerService = customerService;
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @GetMapping
    public Result<Views.CustomerView> profile(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user) {
        return Result.ok(customerService.profile(user.id()));
    }

    @GetMapping("/favorites")
    public Result<List<Views.ProductView>> favorites(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user) {
        return Result.ok(customerService.favorites(user.id()));
    }

    @PutMapping("/favorites/{productId}")
    public Result<Void> addFavorite(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                    @PathVariable long productId) {
        customerService.addFavorite(user.id(), productId);
        return Result.ok();
    }

    @DeleteMapping("/favorites/{productId}")
    public Result<Void> removeFavorite(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                       @PathVariable long productId) {
        customerService.removeFavorite(user.id(), productId);
        return Result.ok();
    }

    @GetMapping("/cart/items")
    public Result<List<Views.CartItemView>> cartItems(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user) {
        return Result.ok(cartService.list(user.id()));
    }

    @PostMapping("/cart/items")
    public Result<Void> addCartItem(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                    @Valid @RequestBody Requests.CartAdd request) {
        cartService.add(user.id(), request);
        return Result.ok();
    }

    @PutMapping("/cart/items/{productId}")
    public Result<Void> updateCartItem(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                       @PathVariable long productId,
                                       @Valid @RequestBody Requests.CartUpdate request) {
        cartService.updateQuantity(user.id(), productId, request.quantity());
        return Result.ok();
    }

    @DeleteMapping("/cart/items/{productId}")
    public Result<Void> removeCartItem(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                       @PathVariable long productId) {
        cartService.remove(user.id(), productId);
        return Result.ok();
    }

    @PostMapping("/cart/to-favorites")
    public Result<Void> cartToFavorites(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                        @Valid @RequestBody Requests.CartToFavorite request) {
        cartService.moveToFavorites(user.id(), request.productIds());
        return Result.ok();
    }

    @PostMapping("/orders")
    public Result<Views.OrderView> createOrder(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                               @Valid @RequestBody Requests.Checkout request) {
        return Result.ok(orderService.create(user.id(), request));
    }

    @GetMapping("/orders")
    public Result<List<Views.OrderView>> orders(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user) {
        return Result.ok(orderService.listByCustomer(user.id()));
    }

    @GetMapping("/orders/{id}")
    public Result<Views.OrderView> orderDetail(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                               @PathVariable long id) {
        return Result.ok(orderService.get(id, AuthUser.ROLE_CUSTOMER, user.id()));
    }

    @PostMapping("/orders/{id}/cancel")
    public Result<Views.OrderView> cancelOrder(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                               @PathVariable long id) {
        return Result.ok(orderService.cancel(id, AuthUser.ROLE_CUSTOMER, user.id()));
    }
}
