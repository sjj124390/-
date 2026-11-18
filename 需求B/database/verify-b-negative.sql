-- =============================================================================
-- 需求 B 数据库负向用例（每条都应报 SQLSTATE 45000，验证业务规则拦截）
-- 前置：先执行 verify-b.sql 生成演示数据
-- 用法：mysql -u root -p --default-character-set=utf8mb4 --force < verify-b-negative.sql
-- 说明：负向用例产生 ERROR 输出属预期结果；用例内部用 LAST_INSERT_ID() 取新订单号，
--       不依赖固定 id，可在正向脚本之后任意次数执行。
--       正向脚本结束时：商品 1 与 3 库存为 0（自动下架），商品 2 库存 5 在售。
-- =============================================================================
USE shop_req_b;

-- 用例 1：超库存加入购物车（客户 2 先加 5 件（等于库存），再加 1 件超出）
SELECT 'N1 超库存加购 → 期望 Insufficient stock' AS case_name;
CALL merge_cart_item(2, 2, 5);
CALL merge_cart_item(2, 2, 1);

-- 用例 2：非法状态跳转（客户 1 新订单从 PLACED 直接推进到 COMPLETED）
SELECT 'N2 非法状态跳转 → 期望 Illegal order status transition' AS case_name;
CALL merge_cart_item(1, 2, 1);
CALL place_order(1, '[2]');
SET @o = LAST_INSERT_ID();
CALL advance_order(@o, 'COMPLETED');

-- 用例 3：重复取消已取消订单（订单 1 已在正向用例中由客户取消）
SELECT 'N3 重复取消订单 → 期望 Order already cancelled' AS case_name;
CALL cancel_order(1, 'CUSTOMER', '重复取消');

-- 用例 4：客户在开始发货后取消
SELECT 'N4 发货后客户取消 → 期望 Customer cannot cancel after shipping' AS case_name;
CALL advance_order(@o, 'CONFIRMED');
CALL advance_order(@o, 'PREPARED');
CALL advance_order(@o, 'SHIPPED');
CALL cancel_order(@o, 'CUSTOMER', '发货后想取消');

-- 用例 5：对已完成订单取消
SELECT 'N5 已完成订单取消 → 期望 Completed order cannot be cancelled' AS case_name;
CALL advance_order(@o, 'COMPLETED');
CALL cancel_order(@o, 'SELLER', '已完成订单取消');

-- 用例 6：上架库存为 0 的商品（商品 3 已被触发器自动下架）
SELECT 'N6 零库存商品上架 → 期望 Product stock is empty' AS case_name;
CALL publish_product(3);

-- 用例 7：勾选不在购物车中的商品下单
SELECT 'N7 下单商品不在购物车 → 期望 Selected products not in cart' AS case_name;
CALL place_order(2, '[999]');
