package com.shop.online.common;

import java.util.List;

/**
 * 分页结果，供商品列表接口使用。
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
