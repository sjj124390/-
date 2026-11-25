-- 在原需求 B 库上补齐联调字段。新库先运行 schema-b.sql，已有库直接执行本脚本一次。
USE shop_req_b;

ALTER TABLE product
  ADD COLUMN sales INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN off_shelf_reason ENUM('SOLD_OUT', 'MANUAL') NULL;

ALTER TABLE customer_order_item
  ADD COLUMN image_url VARCHAR(2048) NULL COMMENT '下单时的封面快照';

-- 原库没有记录下架原因。已有下架商品继续保持下架，后续可由商家手动上架。
UPDATE product SET off_shelf_reason = 'MANUAL' WHERE status = 'OFF_SHELF';

-- 根据仍有效的历史订单恢复销量。取消订单不计入销量。
UPDATE product p
JOIN (
  SELECT i.product_id, SUM(i.quantity) AS quantity
  FROM customer_order_item i JOIN customer_order o ON o.id = i.order_id
  WHERE o.status <> 'CANCELLED'
  GROUP BY i.product_id
) history ON history.product_id = p.id
SET p.sales = history.quantity;
