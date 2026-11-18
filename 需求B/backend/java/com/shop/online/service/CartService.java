package com.shop.online.service;

import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CartRepository;
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

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       FavoriteRepository favoriteRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
    }

    public List<Views.CartItemView> list(long customerId) {
        return cartRepository.list(customerId).stream()
                .map(row -> new Views.CartItemView(
                        row.productId(),
                        row.name(),
                        row.price(),
                        row.stock(),
                        row.quantity(),
                        row.imageUrl()))
                .toList();
    }

    /**
     * 加入购物车；同一商品重复加入时累加数量。
     */
    @Transactional
    public void add(long customerId, Requests.CartAdd request) {
        ProductRepository.ProductRow product = requireOnSaleProduct(request.productId());
        CartRepository.CartRow existing = cartRepository.find(customerId, request.productId()).orElse(null);
        int current = existing == null ? 0 : existing.quantity();
        int next = current + request.quantity();
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
    public void updateQuantity(long customerId, long productId, int quantity) {
        ProductRepository.ProductRow product = requireOnSaleProduct(productId);
        if (quantity > product.stock()) {
            throw BusinessException.badRequest("商品库存不足，请调整数量");
        }
        cartRepository.find(customerId, productId)
                .orElseThrow(() -> BusinessException.notFound("购物车中不存在该商品"));
        cartRepository.updateQuantity(customerId, productId, quantity);
    }

    public void remove(long customerId, long productId) {
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
        List<CartRepository.CartRow> rows = cartRepository.listByIds(customerId, ids);
        if (rows.isEmpty()) {
            throw BusinessException.badRequest("所选商品不在购物车中");
        }
        List<Long> validIds = rows.stream().map(CartRepository.CartRow::productId).toList();
        for (Long productId : validIds) {
            favoriteRepository.add(customerId, productId);
        }
        cartRepository.deleteMany(customerId, validIds);
    }

    private ProductRepository.ProductRow requireOnSaleProduct(long productId) {
        ProductRepository.ProductRow product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        if (!ProductService.STATUS_ON_SALE.equals(product.status())) {
            throw BusinessException.badRequest("商品已下架");
        }
        return product;
    }
}
