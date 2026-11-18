package com.shop.online.service;

import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CustomerRepository;
import com.shop.online.repository.FavoriteRepository;
import com.shop.online.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public CustomerService(CustomerRepository customerRepository,
                           FavoriteRepository favoriteRepository,
                           ProductRepository productRepository,
                           ProductService productService) {
        this.customerRepository = customerRepository;
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    public Views.CustomerView profile(long customerId) {
        CustomerRepository.CustomerStat stat = customerRepository.findAllWithStats().stream()
                .filter(item -> item.id() == customerId)
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
        return toView(stat);
    }

    public List<Views.CustomerView> listCustomers() {
        return customerRepository.findAllWithStats().stream().map(this::toView).toList();
    }

    public List<Views.ProductView> favorites(long customerId) {
        return productService.toViews(favoriteRepository.findProducts(customerId));
    }

    public void addFavorite(long customerId, long productId) {
        requireProduct(productId);
        favoriteRepository.add(customerId, productId);
    }

    public void removeFavorite(long customerId, long productId) {
        favoriteRepository.remove(customerId, productId);
    }

    public void collectProducts(long customerId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw BusinessException.badRequest("请选择要收藏的商品");
        }
        for (Long productId : productIds) {
            requireProduct(productId);
            favoriteRepository.add(customerId, productId);
        }
    }

    private void requireProduct(long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }

    private Views.CustomerView toView(CustomerRepository.CustomerStat stat) {
        return new Views.CustomerView(
                stat.id(),
                stat.username(),
                stat.phone(),
                stat.defaultTradePlace(),
                stat.createdAt(),
                stat.orderCount(),
                stat.totalSpent());
    }
}
