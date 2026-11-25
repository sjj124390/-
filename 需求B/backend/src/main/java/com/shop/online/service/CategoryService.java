package com.shop.online.service;

import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 两级类别树，一级类别包含 children。
     */
    public List<Views.CategoryView> tree() {
        List<CategoryRepository.CategoryRow> all = categoryRepository.findAll();
        Map<Long, List<Views.CategoryView>> children = new HashMap<>();
        for (CategoryRepository.CategoryRow row : all) {
            if (row.parentId() != null) {
                children.computeIfAbsent(row.parentId(), key -> new java.util.ArrayList<>())
                        .add(new Views.CategoryView(row.id(), row.name(), row.parentId(), List.of()));
            }
        }
        return all.stream()
                .filter(row -> row.parentId() == null)
                .map(row -> new Views.CategoryView(row.id(), row.name(), null,
                        children.getOrDefault(row.id(), List.of())))
                .toList();
    }

    public long create(Requests.CategorySave request) {
        String name = request.name().trim();
        validateParent(request.parentId(), null);
        return categoryRepository.insert(name, request.parentId(), resolveSortOrder(request.sortOrder()));
    }

    public void update(long id, Requests.CategorySave request) {
        CategoryRepository.CategoryRow current = categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("类别不存在"));
        if (request.parentId() != null && request.parentId() == current.id()) {
            throw BusinessException.badRequest("类别不能将自己设为父类别");
        }
        if (request.parentId() != null && categoryRepository.countChildren(id) > 0) {
            throw BusinessException.badRequest("该类别下还有子类别，不能改为二级类别");
        }
        validateParent(request.parentId(), id);
        categoryRepository.update(id, request.name().trim(), request.parentId(),
                resolveSortOrder(request.sortOrder()));
    }

    public void delete(long id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("类别不存在"));
        if (categoryRepository.countChildren(id) > 0) {
            throw BusinessException.conflict("请先删除子类别");
        }
        if (categoryRepository.countProducts(id) > 0) {
            throw BusinessException.conflict("该类别下仍有商品，无法删除");
        }
        categoryRepository.delete(id);
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        CategoryRepository.CategoryRow parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> BusinessException.badRequest("父类别不存在"));
        if (parent.parentId() != null) {
            throw BusinessException.badRequest("类别最多支持两级");
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw BusinessException.badRequest("类别不能将自己设为父类别");
        }
    }

    private int resolveSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }
}
