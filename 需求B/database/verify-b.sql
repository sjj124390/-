-- =============================================================================
-- 需求 B 数据库验收脚本（可重复执行）
-- 用法：mysql -u root -p --default-character-set=utf8mb4 < verify-b.sql
-- 说明：脚本先清理业务数据再重建演示数据；末尾的负向用例会产生预期内的
--       ERROR 输出（库存不足、非法状态跳转、客户发货后取消），属正常现象。
-- =============================================================================
USE shop_req_b;

-- 0. 清理（临时关闭外键检查，便于脚本重复执行）
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM order_status_log;
DELETE FROM customer_order_item;
DELETE FROM customer_order;
DELETE FROM cart_item;
DELETE FROM favorite;
DELETE FROM product_image;
DELETE FROM product;
DELETE FROM category;
DELETE FROM customer;
DELETE FROM seller;
SET FOREIGN_KEY_CHECKS = 1;
ALTER TABLE category AUTO_INCREMENT = 1;
ALTER TABLE product AUTO_INCREMENT = 1;
ALTER TABLE customer AUTO_INCREMENT = 1;
ALTER TABLE customer_order AUTO_INCREMENT = 1;
ALTER TABLE order_status_log AUTO_INCREMENT = 1;

SELECT '========== 1. 基础数据 ==========' AS step;

INSERT INTO seller(id, username, password_hash) VALUES(1, 'admin', '$2a$10$demohash');

INSERT INTO category(id, parent_id, name, sort_order) VALUES
 (1, NULL, '数码', 1),
 (2, NULL, '图书', 2);
INSERT INTO category(parent_id, name, sort_order) VALUES
 (1, '手机', 1),
 (1, '耳机', 2),
 (2, '小说', 1);

INSERT INTO product(id, category_id, name, description, image_url, price, stock) VALUES
 (1, 3, '智能手机 Pro', '<p>6.7 英寸 <b>OLED</b> 屏幕</p>', 'img/phone.svg', 4999.00, 2),
 (2, 5, '长篇小说·示例', '<p>精装版</p>', 'img/book.svg', 39.90, 5),
 (3, 4, '蓝牙耳机', '<p>降噪</p>', 'img/earphone.svg', 299.00, 1);
CALL publish_product(1);
CALL publish_product(2);
CALL publish_product(3);

INSERT INTO product_image(product_id, url, sort_order) VALUES
 (1, 'img/phone-1.svg', 1),
 (1, 'img/phone-2.svg', 2);

INSERT INTO customer(username, password_hash, phone, default_location) VALUES
 ('buyer01', '$2a$10$demohash', '13800000001', '图书馆门口'),
 ('buyer02', '$2a$10$demohash', '13800000002', '一号教学楼');

SELECT '基础数据就绪：3 件在售商品、2 个客户' AS info;

SELECT '========== 2. 购物车（同商品累加） ==========' AS step;
CALL merge_cart_item(1, 1, 2);
CALL merge_cart_item(1, 2, 1);
CALL merge_cart_item(1, 3, 1);

SELECT product_id, name, price, stock, quantity FROM v_cart_detail WHERE customer_id = 1;

SELECT '========== 3. 多件下单（扣库存 + 明细快照） ==========' AS step;
CALL place_order(1, '[1,2]');

SELECT '订单主表' AS t, id, status, total_amount, buyer_name, trade_location FROM customer_order;
SELECT '订单明细' AS t, order_id, product_name, unit_price, quantity FROM customer_order_item;
SELECT '商品库存变化' AS t, id, name, stock, status FROM product ORDER BY id;
SELECT '购物车剩余（仅未勾选的耳机）' AS t, product_id, quantity FROM cart_item WHERE customer_id = 1;

SELECT '========== 4. 状态流转与取消规则 ==========' AS step;
CALL advance_order(1, 'CONFIRMED');
CALL advance_order(1, 'PREPARED');

SELECT '客户在发货前取消（应成功并回补库存）' AS info;
CALL cancel_order(1, 'CUSTOMER', '临时不需要了');

SELECT '商品库存与自动上架恢复' AS t, id, name, stock, status FROM product ORDER BY id;

SELECT '再下一单并推进到开始发货' AS info;
CALL merge_cart_item(1, 2, 2);
CALL place_order(1, '[2]');
CALL advance_order(2, 'CONFIRMED');
CALL advance_order(2, 'PREPARED');
CALL advance_order(2, 'SHIPPED');

SELECT '商家在交易完成前取消（应成功）' AS info;
CALL cancel_order(2, 'SELLER', '库存核对异常，商家取消');

SELECT '订单最终状态与流水' AS t, id, status, cancel_actor, cancel_reason FROM customer_order ORDER BY id;
SELECT order_id, from_status, to_status, actor FROM order_status_log ORDER BY id;

SELECT '========== 5. 购物车转收藏 ==========' AS step;
CALL merge_cart_item(1, 1, 1);
CALL cart_to_favorite(1, '[1]');
SELECT '收藏记录' AS t, customer_id, product_id FROM favorite;
SELECT '购物车剩余（仅剩未转换的耳机）' AS t, COUNT(*) AS cnt FROM cart_item WHERE customer_id = 1;

SELECT '========== 6. 库存自动下架（正向验证） ==========' AS step;
SELECT '把商品 3 的库存清零，触发器应自动下架' AS info;
UPDATE product SET stock = 0 WHERE id = 3;
SELECT id, name, stock, status FROM product WHERE id = 3;

SELECT '========== 7. 汇总校验 ==========' AS step;
SELECT '检查项' AS item, '结果' AS result
UNION ALL SELECT '在售商品数（应为 2 或 3，库存 0 已下架）', CAST((SELECT COUNT(*) FROM product WHERE status='ON_SALE') AS CHAR)
UNION ALL SELECT '订单总数', CAST((SELECT COUNT(*) FROM customer_order) AS CHAR)
UNION ALL SELECT '已取消订单数', CAST((SELECT COUNT(*) FROM customer_order WHERE status='CANCELLED') AS CHAR)
UNION ALL SELECT '状态流水条数', CAST((SELECT COUNT(*) FROM order_status_log) AS CHAR)
UNION ALL SELECT '收藏记录数', CAST((SELECT COUNT(*) FROM favorite) AS CHAR)
UNION ALL SELECT '未回补异常的负库存（应为 0）', CAST((SELECT COUNT(*) FROM product WHERE stock < 0) AS CHAR);
