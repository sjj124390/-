package com.shop.online.service.impl;

import com.shop.online.entity.Product;
import com.shop.online.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 第二次迭代：手动下架规则的服务层测试。 */
class ProductWithdrawTest {
    @Test
    void previewReportsPendingIntents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProductServiceImpl service = new ProductServiceImpl(jdbc);
        Product p = new Product();
        p.setId(9L);
        p.setStatus("ON_SALE");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(9L)))
                .thenReturn(java.util.Collections.singletonList(p));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(3);
        Map<String, Object> preview = service.withdrawPreview(9L);
        assertEquals(true, preview.get("allowed"));
        assertEquals(3, preview.get("pendingIntentCount"));
        assertEquals(false, preview.get("willNotifyBuyers"));
        assertTrue(String.valueOf(preview.get("message")).contains("3 个购买意向"));
    }

    @Test
    void tradeFrozenCannotBeWithdrawn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProductServiceImpl service = new ProductServiceImpl(jdbc);
        when(jdbc.update(anyString(), eq(9L))).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.withdraw(9L));
        assertTrue(ex.getMessage().contains("交易中"));
        verify(jdbc, never()).update(contains("purchase_intent"), any());
    }

    @Test
    void withdrawCancelsPendingIntents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProductServiceImpl service = new ProductServiceImpl(jdbc);
        when(jdbc.update(anyString(), eq(9L))).thenReturn(1);
        service.withdraw(9L);
        verify(jdbc).update(contains("purchase_intent"), eq(9L));
    }
}
