-- 后端至少启动一次后执行。重复导入只补齐缺少的演示商品。
USE shop_req_b;
START TRANSACTION;

INSERT INTO category (name, parent_id, sort_order)
VALUES ('骑行用品', NULL, 1)
ON DUPLICATE KEY UPDATE name = category.name;

SET @cycling = (SELECT id FROM category WHERE name = '骑行用品' AND parent_id IS NULL);
INSERT INTO category (name, parent_id, sort_order)
VALUES ('骑行配件', @cycling, 1)
ON DUPLICATE KEY UPDATE name = category.name;
SET @accessories = (SELECT id FROM category WHERE name = '骑行配件' AND parent_id = @cycling);

INSERT INTO product (seller_id, category_id, name, description, image_url, price, stock, status)
SELECT 1, @cycling, '城市通勤折叠车', '车身轻便，适合校园与短途通勤。', 'img/product-bike.svg', 699.00, 4, 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE seller_id = 1 AND name = '城市通勤折叠车');

INSERT INTO product (seller_id, category_id, name, description, image_url, price, stock, status)
SELECT 1, @accessories, '便携车灯', '夜间骑行照明，可充电。', '', 59.00, 12, 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE seller_id = 1 AND name = '便携车灯');

INSERT INTO product (seller_id, category_id, name, description, image_url, price, stock, status)
SELECT 1, @accessories, '骑行水壶', '弹盖设计，容量 600 mL。', '', 29.90, 15, 'ON_SALE'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE seller_id = 1 AND name = '骑行水壶');

COMMIT;
