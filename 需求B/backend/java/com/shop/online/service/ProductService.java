package com.shop.online.service;

import com.shop.online.common.PageResult;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CategoryRepository;
import com.shop.online.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_OFF_SHELF = "OFF_SHELF";
    public static final String REASON_MANUAL = "MANUAL";
    public static final String REASON_SOLD_OUT = "SOLD_OUT";

    private static final int MAX_IMAGES = 9;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public PageResult<Views.ProductView> searchPublic(String keyword, Long categoryId, int page, int size) {
        return search(keyword, categoryId, STATUS_ON_SALE, page, size);
    }

    public PageResult<Views.ProductView> searchAdmin(String keyword, Long categoryId,
                                                     String status, int page, int size) {
        if (status != null && !status.isBlank()
                && !STATUS_ON_SALE.equals(status) && !STATUS_OFF_SHELF.equals(status)) {
            throw BusinessException.badRequest("商品状态不合法");
        }
        return search(keyword, categoryId, status, page, size);
    }

    private PageResult<Views.ProductView> search(String keyword, Long categoryId,
                                                 String status, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        List<Long> categoryIds = resolveCategoryIds(categoryId);

        List<ProductRepository.ProductRow> rows =
                productRepository.search(keyword, categoryIds, status, pageNo, pageSize);
        long total = productRepository.count(keyword, categoryIds, status);
        List<Views.ProductView> records = toViews(rows);
        return new PageResult<>(records, total, pageNo, pageSize);
    }

    public Views.ProductView get(long id) {
        ProductRepository.ProductRow row = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        List<Views.ProductView> views = toViews(List.of(row));
        return views.get(0);
    }

    public List<Views.ProductView> toViews(List<ProductRepository.ProductRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ProductRepository.ProductRow::id).toList();
        Map<Long, List<String>> imageMap = productRepository.findImages(ids);
        List<Views.ProductView> views = new ArrayList<>(rows.size());
        for (ProductRepository.ProductRow row : rows) {
            List<String> images = new ArrayList<>(imageMap.getOrDefault(row.id(), List.of()));
            if (images.isEmpty() && row.imageUrl() != null && !row.imageUrl().isBlank()) {
                images.add(row.imageUrl());
            }
            views.add(new Views.ProductView(
                    row.id(),
                    row.name(),
                    row.description(),
                    row.price(),
                    row.stock(),
                    row.imageUrl(),
                    List.copyOf(images),
                    row.categoryId(),
                    row.categoryName(),
                    row.status(),
                    row.sales(),
                    row.createdAt()));
        }
        return views;
    }

    @Transactional
    public Views.ProductView create(Requests.ProductSave request) {
        String name = request.name().trim();
        BigDecimal price = normalizePrice(request.price());
        int stock = request.stock();
        Long categoryId = validateCategory(request.categoryId());
        List<String> images = normalizeImages(request.imageUrl(), request.images());
        String imageUrl = normalizeCover(request.imageUrl(), images);

        String status = stock > 0 ? STATUS_ON_SALE : STATUS_OFF_SHELF;
        String reason = stock > 0 ? null : REASON_SOLD_OUT;

        long id = productRepository.insert(name, request.description(), price, stock,
                imageUrl, categoryId, status, reason);
        productRepository.replaceImages(id, images);
        return get(id);
    }

    @Transactional
    public Views.ProductView update(long id, Requests.ProductSave request) {
        ProductRepository.ProductRow current = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));

        String name = request.name().trim();
        BigDecimal price = normalizePrice(request.price());
        int stock = request.stock();
        Long categoryId = validateCategory(request.categoryId());
        List<String> images = normalizeImages(request.imageUrl(), request.images());
        String imageUrl = normalizeCover(request.imageUrl(), images);

        String status;
        String reason;
        if (stock <= 0) {
            status = STATUS_OFF_SHELF;
            reason = REASON_SOLD_OUT;
        } else if (STATUS_OFF_SHELF.equals(current.status())
                && REASON_SOLD_OUT.equals(current.offShelfReason())) {
            // 补货后自动重新上架
            status = STATUS_ON_SALE;
            reason = null;
        } else {
            status = current.status();
            reason = current.offShelfReason();
        }

        productRepository.update(id, name, request.description(), price, stock,
                imageUrl, categoryId, status, reason);
        productRepository.replaceImages(id, images);
        return get(id);
    }

    public void changeStatus(long id, String status) {
        ProductRepository.ProductRow current = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        if (STATUS_ON_SALE.equals(status)) {
            if (current.stock() <= 0) {
                throw BusinessException.badRequest("库存为 0，无法上架，请先补充库存");
            }
            productRepository.updateStatus(id, STATUS_ON_SALE, null);
        } else if (STATUS_OFF_SHELF.equals(status)) {
            productRepository.updateStatus(id, STATUS_OFF_SHELF, REASON_MANUAL);
        } else {
            throw BusinessException.badRequest("商品状态不合法");
        }
    }

    private List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryRepository.findByParentId(categoryId).forEach(child -> ids.add(child.id()));
        return ids;
    }

    private Long validateCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.badRequest("商品类别不存在"));
        return categoryId;
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> normalizeImages(String imageUrl, List<String> images) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            set.add(imageUrl.trim());
        }
        if (images != null) {
            for (String image : images) {
                if (image != null && !image.isBlank()) {
                    set.add(image.trim());
                }
            }
        }
        if (set.size() > MAX_IMAGES) {
            throw BusinessException.badRequest("商品图片最多 " + MAX_IMAGES + " 张");
        }
        for (String image : set) {
            if (image.length() > 500) {
                throw BusinessException.badRequest("图片地址长度不能超过 500 个字符");
            }
        }
        return new ArrayList<>(set);
    }

    private String normalizeCover(String imageUrl, List<String> images) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl.trim();
        }
        return images.isEmpty() ? null : images.get(0);
    }
}
