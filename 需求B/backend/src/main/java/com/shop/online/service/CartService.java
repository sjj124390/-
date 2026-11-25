package com.shop.online.service;

import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CartRepository;
import com.shop.online.repository.CustomerRepository;
import com.shop.online.repository.FavoriteRepository;
import com.shop.online.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;
    private final CustomerRepository customerRepository;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       FavoriteRepository favoriteRepository,
                       CustomerRepository customerRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
        this.customerRepository = customerRepository;
    }

    public List<Views.CartItemView> list(long customerId) {
        return cartRepository.list(customerId).stream()
                .map(row -> new Views.CartItemView(
                        row.productId(),
                        row.name(),
                        row.price(),
                        row.stock(),
                        row.quantity(),
                        row.imageUrl(),
                        row.status()))
                .toList();
    }

    /**
     * 加入购物车；同一商品重复加入时累加数量。
     */
    @Transactional
    public void add(long customerId, Requests.CartAdd request) {
        lockCart(customerId);
        ProductRepository.ProductLockRow product = requireOnSaleProduct(request.productId());
        CartRepository.CartRow existing = cartRepository.find(customerId, request.productId()).orElse(null);
        int current = existing == null ? 0 : existing.quantity();
        int next = current + request.quantity();
        if (next > 999) {
            throw BusinessException.badRequest("同一商品最多可加入 999 件");
        }
        if (next > product.stock()) {
            throw BusinessException.badRequest("商品库存不足，请调整数量");
        }
        if (existing == null) {
            cartRepository.insert(customerId, request.productId(), next);
        } else {
            cartRepository.updateQuantity(customerId, request.productId(), next);
        }
    }

    /**
     * 更新为指定数量（覆盖原数量）。
     */
    @Transactional
    public void updateQuantity(long customerId, long productId, int quantity) {
        lockCart(customerId);
        ProductRepository.ProductLockRow product = requireOnSaleProduct(productId);
        if (quantity > product.stock()) {
            throw BusinessException.badRequest("商品库存不足，请调整数量");
        }
        cartRepository.find(customerId, productId)
                .orElseThrow(() -> BusinessException.notFound("购物车中不存在该商品"));
        cartRepository.updateQuantity(customerId, productId, quantity);
    }

    @Transactional
    public void remove(long customerId, long productId) {
        lockCart(customerId);
        cartRepository.delete(customerId, productId);
    }

    /**
     * 将购物车中选中的商品转为收藏，并从购物车移除。
     */
    @Transactional
    public void moveToFavorites(long customerId, List<Long> productIds) {
        List<Long> ids = productIds == null ? List.of()
                : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("请选择要收藏的商品");
        }
        lockCart(customerId);
        List<CartRepository.CartRow> rows = cartRepository.listByIds(customerId, ids);
        if (rows.size() != ids.size()) {
            throw BusinessException.badRequest("购物车已变动，请刷新后重新选择商品");
        }
        for (Long productId : ids) {
            favoriteRepository.add(customerId, productId);
        }
        cartRepository.deleteMany(customerId, ids);
    }

    private void lockCart(long customerId) {
        customerRepository.lockById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
    }

    private ProductRepository.ProductLockRow requireOnSaleProduct(long productId) {
        ProductRepository.ProductLockRow product = productRepository.lockByIds(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        if (!ProductService.STATUS_ON_SALE.equals(product.status())) {
            throw BusinessException.badRequest("商品已下架");
        }
        return product;
    }
}
