package com.shop.online.service;

import com.shop.online.common.AuthUser;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CartRepository;
import com.shop.online.repository.CustomerRepository;
import com.shop.online.repository.OrderRepository;
import com.shop.online.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public static final String STATUS_PLACED = "PLACED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PREPARED = "PREPARED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 状态机：只能按顺序推进。
     */
    private static final Map<String, String> NEXT_STATUS = Map.of(
            STATUS_PLACED, STATUS_CONFIRMED,
            STATUS_CONFIRMED, STATUS_PREPARED,
            STATUS_PREPARED, STATUS_SHIPPED,
            STATUS_SHIPPED, STATUS_COMPLETED);

    /**
     * 客户可在「开始发货」前取消。
     */
    private static final Set<String> CUSTOMER_CANCELLABLE =
            Set.of(STATUS_PLACED, STATUS_CONFIRMED, STATUS_PREPARED);

    /**
     * 商家可在「交易完成」前取消。
     */
    private static final Set<String> SELLER_CANCELLABLE =
            Set.of(STATUS_PLACED, STATUS_CONFIRMED, STATUS_PREPARED, STATUS_SHIPPED);

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CartRepository cartRepository,
                        CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * 从购物车勾选商品下单：校验库存与售价、扣减库存、生成订单、清理购物车。
     */
    @Transactional
    public Views.OrderView create(long customerId, Requests.Checkout request) {
        List<Long> productIds = request.productIds() == null ? List.of()
                : request.productIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (productIds.isEmpty()) {
            throw BusinessException.badRequest("请选择要下单的商品");
        }
        if (productIds.size() > 50) {
            throw BusinessException.badRequest("单次最多下单 50 种商品");
        }

        CustomerRepository.CustomerRow customer = customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));

        List<CartRepository.CartRow> cartRows = cartRepository.listByIds(customerId, productIds);
        if (cartRows.size() != productIds.size()) {
            throw BusinessException.badRequest("购物车已变动，请刷新后重新选择商品");
        }
        Map<Long, Integer> quantities = new HashMap<>();
        for (CartRepository.CartRow row : cartRows) {
            quantities.put(row.productId(), row.quantity());
        }

        List<ProductRepository.ProductLockRow> products = productRepository.lockByIds(productIds);
        if (products.size() != productIds.size()) {
            throw BusinessException.badRequest("购物车中的商品已不存在");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (ProductRepository.ProductLockRow product : products) {
            int quantity = quantities.get(product.id());
            if (!ProductService.STATUS_ON_SALE.equals(product.status())) {
                throw BusinessException.badRequest(product.name() + " 已下架");
            }
            if (product.stock() < quantity) {
                throw BusinessException.badRequest(product.name() + " 库存不足");
            }
            total = total.add(product.price().multiply(BigDecimal.valueOf(quantity)));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        for (ProductRepository.ProductLockRow product : products) {
            int quantity = quantities.get(product.id());
            int stock = product.stock() - quantity;
            String status = stock == 0 ? ProductService.STATUS_OFF_SHELF : product.status();
            String reason = stock == 0 ? ProductService.REASON_SOLD_OUT : product.offShelfReason();
            productRepository.updateStockAndSales(
                    product.id(), stock, product.sales() + quantity, status, reason);
        }

        String tradePlace = request.tradePlace() == null || request.tradePlace().isBlank()
                ? customer.defaultTradePlace()
                : request.tradePlace().trim();

        long orderId = orderRepository.insertOrder(
                generateOrderNo(), customerId, STATUS_PLACED, total, tradePlace);
        for (ProductRepository.ProductLockRow product : products) {
            int quantity = quantities.get(product.id());
            orderRepository.insertItem(orderId, product.id(), product.name(),
                    product.price(), quantity, product.imageUrl());
        }
        cartRepository.deleteMany(customerId, productIds);

        return get(orderId, AuthUser.ROLE_CUSTOMER, customerId);
    }

    public List<Views.OrderView> listByCustomer(long customerId) {
        return assemble(orderRepository.findByCustomer(customerId), AuthUser.ROLE_CUSTOMER);
    }

    public List<Views.OrderView> listAll() {
        return assemble(orderRepository.findAll(), AuthUser.ROLE_SELLER);
    }

    public List<Views.OrderView> listByCustomerForSeller(long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
        return assemble(orderRepository.findByCustomer(customerId), AuthUser.ROLE_SELLER);
    }

    public Views.OrderView get(long orderId, String role, Long customerId) {
        OrderRepository.OrderRow row = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if (AuthUser.ROLE_CUSTOMER.equals(role) && customerId != null
                && row.customerId() != customerId) {
            throw BusinessException.notFound("订单不存在");
        }
        List<OrderRepository.OrderItemRow> items = orderRepository.findItems(List.of(orderId));
        return toView(row, items, role);
    }

    /**
     * 商家推进订单状态，只允许 PLACED -> CONFIRMED -> PREPARED -> SHIPPED -> COMPLETED。
     */
    @Transactional
    public Views.OrderView changeStatus(long orderId, String targetStatus) {
        OrderRepository.OrderLockRow lock = orderRepository.lockById(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        String expected = NEXT_STATUS.get(lock.status());
        if (expected == null) {
            throw BusinessException.conflict("当前订单状态不可继续推进");
        }
        if (!expected.equals(targetStatus)) {
            throw BusinessException.conflict("订单状态已变动，请刷新后重试");
        }
        orderRepository.updateStatus(orderId, targetStatus);
        return get(orderId, AuthUser.ROLE_SELLER, null);
    }

    /**
     * 取消订单并回补库存。
     * 客户：开始发货前可取消；商家：交易完成前可取消。
     */
    @Transactional
    public Views.OrderView cancel(long orderId, String role, Long customerId) {
        OrderRepository.OrderLockRow lock = orderRepository.lockById(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));

        boolean seller = AuthUser.ROLE_SELLER.equals(role);
        if (seller) {
            if (!SELLER_CANCELLABLE.contains(lock.status())) {
                throw BusinessException.conflict("订单已交易完成或已取消，无法取消");
            }
        } else {
            if (customerId == null || lock.customerId() != customerId) {
                throw BusinessException.notFound("订单不存在");
            }
            if (!CUSTOMER_CANCELLABLE.contains(lock.status())) {
                throw BusinessException.conflict("订单已开始发货，无法取消");
            }
        }

        List<OrderRepository.OrderItemRow> items = orderRepository.findItems(List.of(orderId));
        List<Long> productIds = items.stream()
                .map(OrderRepository.OrderItemRow::productId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, ProductRepository.ProductLockRow> locked = productRepository.lockByIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductRepository.ProductLockRow::id, product -> product));

        for (OrderRepository.OrderItemRow item : items) {
            ProductRepository.ProductLockRow product = locked.get(item.productId());
            if (product == null) {
                continue;
            }
            int stock = product.stock() + item.quantity();
            int sales = Math.max(0, product.sales() - item.quantity());
            String status = product.status();
            String reason = product.offShelfReason();
            if (ProductService.STATUS_OFF_SHELF.equals(status)
                    && ProductService.REASON_SOLD_OUT.equals(reason)) {
                // 因库存为 0 自动下架的商品，取消订单后恢复上架
                status = ProductService.STATUS_ON_SALE;
                reason = null;
            }
            productRepository.updateStockAndSales(product.id(), stock, sales, status, reason);
        }

        orderRepository.cancel(orderId, seller ? AuthUser.ROLE_SELLER : AuthUser.ROLE_CUSTOMER,
                seller ? "商家取消订单" : "客户取消订单");
        return get(orderId, role, customerId);
    }

    private List<Views.OrderView> assemble(List<OrderRepository.OrderRow> rows, String role) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = rows.stream().map(OrderRepository.OrderRow::id).toList();
        List<OrderRepository.OrderItemRow> items = orderRepository.findItems(orderIds);
        Map<Long, List<OrderRepository.OrderItemRow>> grouped = new LinkedHashMap<>();
        for (OrderRepository.OrderItemRow item : items) {
            grouped.computeIfAbsent(item.orderId(), key -> new ArrayList<>()).add(item);
        }
        List<Views.OrderView> views = new ArrayList<>(rows.size());
        for (OrderRepository.OrderRow row : rows) {
            views.add(toView(row, grouped.getOrDefault(row.id(), List.of()), role));
        }
        return views;
    }

    private Views.OrderView toView(OrderRepository.OrderRow row,
                                   List<OrderRepository.OrderItemRow> items,
                                   String role) {
        List<Views.OrderItemView> itemViews = items.stream()
                .map(item -> new Views.OrderItemView(
                        item.productId(),
                        item.productName(),
                        item.price(),
                        item.quantity(),
                        item.imageUrl()))
                .toList();
        return new Views.OrderView(
                row.id(),
                row.orderNo(),
                row.customerId(),
                row.buyerName(),
                row.buyerPhone(),
                row.tradePlace(),
                row.status(),
                row.totalAmount(),
                row.createdAt(),
                itemViews,
                allowedActions(row.status(), role),
                row.cancelledBy());
    }

    /**
     * 根据当前身份与订单状态，返回前端可直接渲染的可执行动作。
     */
    private List<String> allowedActions(String status, String role) {
        List<String> actions = new ArrayList<>();
        if (STATUS_CANCELLED.equals(status) || STATUS_COMPLETED.equals(status)) {
            return actions;
        }
        if (AuthUser.ROLE_SELLER.equals(role)) {
            String next = NEXT_STATUS.get(status);
            if (next != null) {
                actions.add(next);
            }
            if (SELLER_CANCELLABLE.contains(status)) {
                actions.add(STATUS_CANCELLED);
            }
        } else if (CUSTOMER_CANCELLABLE.contains(status)) {
            actions.add(STATUS_CANCELLED);
        }
        return actions;
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMAT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
