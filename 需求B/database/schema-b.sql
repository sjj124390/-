-- =============================================================================
-- 在线购物系统 · 需求 B 数据库（挑战需求 A + B）
-- 负责人：朱江（第 10 组 · 数据库）
-- 适用：MySQL 8.0.16+（本脚本在 8.0.46 上验证通过）
--
-- 相对基线库（database/schema.sql）的变化：
--   1. 商品支持多件同时在售，移除「同一时间只有一件在售」的唯一约束；
--   2. 商品新增二级分类、库存、多图（product_image）；
--   3. 新增客户表（注册 / 登录 / 默认交易地点），支持商家查看客户；
--   4. 新增收藏、购物车，支持多件商品一次性下单；
--   5. 新增订单主表 + 明细表，实现 PLACED→CONFIRMED→PREPARED→SHIPPED→COMPLETED
--      五状态流转与取消规则；
--   6. 新增订单状态流水表，保留状态变更痕迹；
--   7. 库存为 0 自动下架（触发器），取消订单回补库存。
--
-- 安装：mysql -u root -p < schema-b.sql
-- 说明：脚本使用独立库 shop_req_b，不覆盖已通过验收的基线库 single_item_shop。
--       重复执行会在同名表处报错，不会删除已有数据。
-- =============================================================================
CREATE DATABASE IF NOT EXISTS shop_req_b CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE shop_req_b;

-- -----------------------------------------------------------------------------
-- 1. 卖家（沿用基线：唯一卖家）
-- -----------------------------------------------------------------------------
CREATE TABLE seller (
  id TINYINT UNSIGNED PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL COMMENT '应用生成 BCrypt/Argon2 哈希，不存明文',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT ck_single_seller CHECK (id = 1)
) ENGINE=InnoDB COMMENT='唯一卖家账号';

-- -----------------------------------------------------------------------------
-- 2. 商品分类（二级）
--    parent_id 为 NULL 表示一级分类；二级分类指向一级。
--    parent_key 生成列把 NULL 转为 0，使同名分类在「同层级」内唯一。
-- -----------------------------------------------------------------------------
CREATE TABLE category (
  id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  parent_id INT UNSIGNED NULL COMMENT 'NULL=一级分类，否则为一级分类 id',
  name VARCHAR(50) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  parent_key INT UNSIGNED GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_category_sibling (parent_key, name),
  KEY idx_category_parent (parent_id, sort_order),
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
) ENGINE=InnoDB COMMENT='商品分类，最多两级（自引用由 trg_category_not_self 触发器拦截）';

-- -----------------------------------------------------------------------------
-- 3. 商品
--    多商品在售；库存为 0 时由触发器自动转为 OFF_SHELF。
--    description 使用 MEDIUMTEXT 以容纳富媒体（HTML）描述。
-- -----------------------------------------------------------------------------
CREATE TABLE product (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  seller_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
  category_id INT UNSIGNED NULL,
  name VARCHAR(100) NOT NULL,
  description MEDIUMTEXT NOT NULL COMMENT '支持富媒体描述（HTML 片段）',
  image_url VARCHAR(2048) NOT NULL COMMENT '主图地址，多图见 product_image',
  price DECIMAL(10,2) NOT NULL,
  stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '库存数量，0 自动下架',
  status ENUM('DRAFT','ON_SALE','OFF_SHELF','SOLD') NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_product_status (status, created_at),
  KEY idx_product_category (category_id, status),
  KEY idx_product_name (name),
  FULLTEXT KEY ft_product_search (name, description) WITH PARSER ngram COMMENT '中文全文检索',
  CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES seller(id),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
  CONSTRAINT ck_product_price CHECK (price > 0)
) ENGINE=InnoDB COMMENT='商品（多件在售、带库存与分类）';

-- -----------------------------------------------------------------------------
-- 4. 商品图片（多图，按顺序展示）
-- -----------------------------------------------------------------------------
CREATE TABLE product_image (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT UNSIGNED NOT NULL,
  url VARCHAR(2048) NOT NULL,
  sort_order TINYINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_image_order (product_id, sort_order),
  KEY idx_image_product (product_id),
  CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB COMMENT='商品多图';

-- -----------------------------------------------------------------------------
-- 5. 客户（挑战需求 A.2：自由注册）
-- -----------------------------------------------------------------------------
CREATE TABLE customer (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt/Argon2 哈希',
  phone VARCHAR(20) NOT NULL,
  default_location VARCHAR(200) NOT NULL COMMENT '默认交易地点',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_customer_username (username),
  KEY idx_customer_phone (phone)
) ENGINE=InnoDB COMMENT='注册客户';

-- -----------------------------------------------------------------------------
-- 6. 商品收藏（需求 B.2.1）
-- -----------------------------------------------------------------------------
CREATE TABLE favorite (
  customer_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (customer_id, product_id),
  KEY idx_favorite_product (product_id),
  CONSTRAINT fk_favorite_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_favorite_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB COMMENT='客户收藏商品';

-- -----------------------------------------------------------------------------
-- 7. 购物车（需求 B.2.2）
--    同一客户同一商品只保留一行，数量为累计值。
-- -----------------------------------------------------------------------------
CREATE TABLE cart_item (
  customer_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity INT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (customer_id, product_id),
  KEY idx_cart_product (product_id),
  CONSTRAINT fk_cart_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT ck_cart_quantity CHECK (quantity > 0 AND quantity <= 999)
) ENGINE=InnoDB COMMENT='购物车条目';

-- -----------------------------------------------------------------------------
-- 8. 订单主表（需求 B.1）
--    状态：PLACED 客户下单 → CONFIRMED 商家确认 → PREPARED 备货完成
--          → SHIPPED 开始发货 → COMPLETED 交易完成；CANCELLED 已取消。
--    取消规则（数据库层强制）：
--      客户：SHIPPED 之前可取消（PLACED/CONFIRMED/PREPARED）
--      商家：COMPLETED 之前可取消（PLACED/CONFIRMED/PREPARED/SHIPPED）
--    下单时保存客户与交易地点快照，避免后续资料变更影响历史订单。
-- -----------------------------------------------------------------------------
CREATE TABLE customer_order (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT UNSIGNED NOT NULL,
  status ENUM('PLACED','CONFIRMED','PREPARED','SHIPPED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PLACED',
  total_amount DECIMAL(12,2) NOT NULL COMMENT '下单时明细金额合计',
  buyer_name VARCHAR(50) NOT NULL COMMENT '下单人快照',
  buyer_phone VARCHAR(20) NOT NULL COMMENT '联系电话快照',
  trade_location VARCHAR(200) NOT NULL COMMENT '交易地点快照',
  cancel_actor ENUM('CUSTOMER','SELLER') NULL COMMENT '取消方',
  cancel_reason VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  cancelled_at DATETIME(6) NULL,
  KEY idx_order_customer (customer_id, status, created_at),
  KEY idx_order_status (status, created_at),
  CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT ck_order_total CHECK (total_amount > 0),
  CONSTRAINT ck_order_cancel CHECK (
    (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancel_actor IS NOT NULL)
    OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancel_actor IS NULL))
) ENGINE=InnoDB COMMENT='订单（五状态 + 取消）';

-- -----------------------------------------------------------------------------
-- 9. 订单明细（下单时的名称与单价快照）
-- -----------------------------------------------------------------------------
CREATE TABLE customer_order_item (
  order_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  product_name VARCHAR(100) NOT NULL COMMENT '下单时商品名称快照',
  unit_price DECIMAL(10,2) NOT NULL COMMENT '下单时单价快照',
  quantity INT UNSIGNED NOT NULL,
  PRIMARY KEY (order_id, product_id),
  KEY idx_item_product (product_id),
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT ck_item_price CHECK (unit_price > 0),
  CONSTRAINT ck_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB COMMENT='订单明细';

-- -----------------------------------------------------------------------------
-- 10. 订单状态流水（审计与追溯）
-- -----------------------------------------------------------------------------
CREATE TABLE order_status_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  from_status ENUM('PLACED','CONFIRMED','PREPARED','SHIPPED','COMPLETED','CANCELLED') NULL COMMENT '首条为 NULL',
  to_status ENUM('PLACED','CONFIRMED','PREPARED','SHIPPED','COMPLETED','CANCELLED') NOT NULL,
  actor ENUM('CUSTOMER','SELLER','SYSTEM') NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_log_order (order_id, created_at),
  CONSTRAINT fk_log_order FOREIGN KEY (order_id) REFERENCES customer_order(id)
) ENGINE=InnoDB COMMENT='订单状态变更流水';

-- -----------------------------------------------------------------------------
-- 11. 触发器
--     11.1 库存为 0 自动下架（挑战需求 A.1.3）
--     11.2 分类不允许把自己设为上级（CHECK 约束不能引用自增列，改用触发器）
-- -----------------------------------------------------------------------------
DELIMITER $$
CREATE TRIGGER trg_product_zero_stock_offline
BEFORE UPDATE ON product
FOR EACH ROW
BEGIN
  IF NEW.stock = 0 AND NEW.status = 'ON_SALE' THEN
    SET NEW.status = 'OFF_SHELF';
  END IF;
END$$

CREATE TRIGGER trg_category_not_self
BEFORE UPDATE ON category
FOR EACH ROW
BEGIN
  IF NEW.parent_id IS NOT NULL AND NEW.parent_id = NEW.id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Category cannot be its own parent';
  END IF;
END$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- 12. 视图：购物车与订单的联表查询（供接口直接读取，减少应用层拼装）
-- -----------------------------------------------------------------------------
CREATE VIEW v_cart_detail AS
SELECT c.customer_id, c.product_id, p.name, p.price, p.stock, c.quantity,
       p.image_url, p.status AS product_status
FROM cart_item c JOIN product p ON p.id = c.product_id;

CREATE VIEW v_order_detail AS
SELECT o.id AS order_id, o.customer_id, o.status, o.total_amount, o.created_at,
       o.buyer_name, o.buyer_phone, o.trade_location,
       i.product_id, i.product_name, i.unit_price, i.quantity
FROM customer_order o JOIN customer_order_item i ON i.order_id = o.id;

-- =============================================================================
-- 13. 存储过程：业务规则集中实现
--     约定：过程自行管理事务，不得在调用方已开启的事务中调用；
--           失败统一 SIGNAL SQLSTATE '45000'，应用层按消息映射为中文提示。
-- =============================================================================
DELIMITER $$

-- 13.1 发布商品：只允许草稿或已下架商品上架，且库存必须大于 0
CREATE PROCEDURE publish_product(IN p_product BIGINT UNSIGNED)
BEGIN
  DECLARE v_seller TINYINT UNSIGNED DEFAULT NULL;
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_stock INT UNSIGNED DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT id INTO v_seller FROM seller WHERE id = 1 FOR SHARE;
  IF v_seller IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Initialize seller first';
  END IF;
  SELECT status, stock INTO v_status, v_stock FROM product WHERE id = p_product FOR UPDATE;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product not exists';
  END IF;
  IF v_status NOT IN ('DRAFT','OFF_SHELF') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product cannot be published';
  END IF;
  IF v_stock = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product stock is empty';
  END IF;
  UPDATE product SET status = 'ON_SALE' WHERE id = p_product;
  COMMIT;
END$$

-- 13.2 手动下架商品（商家操作）
CREATE PROCEDURE withdraw_product(IN p_product BIGINT UNSIGNED)
BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT status INTO v_status FROM product WHERE id = p_product FOR UPDATE;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product not exists';
  END IF;
  IF v_status <> 'ON_SALE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only on-sale product can be withdrawn';
  END IF;
  UPDATE product SET status = 'OFF_SHELF' WHERE id = p_product;
  COMMIT;
END$$

-- 13.3 加入购物车（同商品累加数量，校验在售与库存）
CREATE PROCEDURE merge_cart_item(
  IN p_customer BIGINT UNSIGNED, IN p_product BIGINT UNSIGNED, IN p_quantity INT UNSIGNED)
BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_stock INT UNSIGNED DEFAULT 0;
  DECLARE v_have INT UNSIGNED DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF p_quantity IS NULL OR p_quantity = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid quantity';
  END IF;
  START TRANSACTION;
  SELECT status, stock INTO v_status, v_stock FROM product WHERE id = p_product FOR SHARE;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product not exists';
  END IF;
  IF v_status <> 'ON_SALE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product is not available';
  END IF;
  SELECT quantity INTO v_have FROM cart_item
  WHERE customer_id = p_customer AND product_id = p_product FOR UPDATE;
  IF v_have IS NULL THEN
    SET v_have = 0;
  END IF;
  IF v_have + p_quantity > v_stock THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient stock';
  END IF;
  IF v_have = 0 THEN
    INSERT INTO cart_item(customer_id, product_id, quantity) VALUES(p_customer, p_product, p_quantity);
  ELSE
    UPDATE cart_item SET quantity = quantity + p_quantity
    WHERE customer_id = p_customer AND product_id = p_product;
  END IF;
  COMMIT;
END$$

-- 13.4 多件商品下单（从购物车勾选）
--   p_product_ids：JSON 数组，如 '[1,3,5]'
--   逻辑：校验勾选项都在购物车 → 原子扣减库存（条件更新，防超卖）
--         → 生成订单与明细快照 → 清空勾选项 → 写状态流水
CREATE PROCEDURE place_order(IN p_customer BIGINT UNSIGNED, IN p_product_ids JSON)
BEGIN
  DECLARE v_name VARCHAR(50) DEFAULT NULL;
  DECLARE v_phone VARCHAR(20) DEFAULT NULL;
  DECLARE v_loc VARCHAR(200) DEFAULT NULL;
  DECLARE v_selected INT DEFAULT 0;
  DECLARE v_in_cart INT DEFAULT 0;
  DECLARE v_updated INT DEFAULT 0;
  DECLARE v_total DECIMAL(12,2) DEFAULT NULL;
  DECLARE v_order BIGINT UNSIGNED;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

  IF p_product_ids IS NULL OR JSON_LENGTH(p_product_ids) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No product selected';
  END IF;

  START TRANSACTION;

  -- 客户信息快照与存在性校验
  SELECT username, phone, default_location INTO v_name, v_phone, v_loc
  FROM customer WHERE id = p_customer FOR SHARE;
  IF v_name IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer not exists';
  END IF;

  -- 勾选商品必须都在该客户购物车中
  SET v_selected = JSON_LENGTH(p_product_ids);
  SELECT COUNT(*) INTO v_in_cart
  FROM JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt
  JOIN cart_item c ON c.product_id = jt.pid AND c.customer_id = p_customer;
  IF v_in_cart <> v_selected THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selected products not in cart';
  END IF;

  -- 原子扣减库存：条件更新，库存不足或已下架的商品不会被扣减
  UPDATE product p
  JOIN cart_item c ON c.product_id = p.id AND c.customer_id = p_customer
  JOIN JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt ON jt.pid = p.id
  SET p.stock = p.stock - c.quantity
  WHERE p.status = 'ON_SALE' AND p.stock >= c.quantity;
  SET v_updated = ROW_COUNT();
  IF v_updated <> v_selected THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient stock or product off-sale';
  END IF;

  -- 订单总额（价格取自商品当前价，扣库存不影响价格）
  SELECT SUM(p.price * c.quantity) INTO v_total
  FROM cart_item c
  JOIN product p ON p.id = c.product_id
  JOIN JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt ON jt.pid = c.product_id
  WHERE c.customer_id = p_customer;

  INSERT INTO customer_order(customer_id, status, total_amount, buyer_name, buyer_phone, trade_location)
  VALUES(p_customer, 'PLACED', v_total, v_name, v_phone, v_loc);
  SET v_order = LAST_INSERT_ID();

  INSERT INTO customer_order_item(order_id, product_id, product_name, unit_price, quantity)
  SELECT v_order, p.id, p.name, p.price, c.quantity
  FROM cart_item c
  JOIN product p ON p.id = c.product_id
  JOIN JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt ON jt.pid = c.product_id
  WHERE c.customer_id = p_customer;

  DELETE c FROM cart_item c
  JOIN JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt ON jt.pid = c.product_id
  WHERE c.customer_id = p_customer;

  INSERT INTO order_status_log(order_id, from_status, to_status, actor)
  VALUES(v_order, NULL, 'PLACED', 'CUSTOMER');

  COMMIT;
  SELECT v_order AS order_id;
END$$

-- 13.5 推进订单状态（商家操作）
--   只允许 PLACED→CONFIRMED→PREPARED→SHIPPED→COMPLETED 逐级推进
CREATE PROCEDURE advance_order(
  IN p_order BIGINT UNSIGNED, IN p_target ENUM('CONFIRMED','PREPARED','SHIPPED','COMPLETED'))
BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT status INTO v_status FROM customer_order WHERE id = p_order FOR UPDATE;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order not exists';
  END IF;
  IF NOT ((v_status = 'PLACED' AND p_target = 'CONFIRMED')
       OR (v_status = 'CONFIRMED' AND p_target = 'PREPARED')
       OR (v_status = 'PREPARED' AND p_target = 'SHIPPED')
       OR (v_status = 'SHIPPED' AND p_target = 'COMPLETED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Illegal order status transition';
  END IF;
  UPDATE customer_order SET status = p_target WHERE id = p_order;
  INSERT INTO order_status_log(order_id, from_status, to_status, actor)
  VALUES(p_order, v_status, p_target, 'SELLER');
  COMMIT;
END$$

-- 13.6 取消订单（客户 / 商家，规则在数据库层强制）
--   客户：SHIPPED 之前可取消；商家：COMPLETED 之前可取消。
--   取消后回补库存，因库存耗尽而自动下架的商品恢复在售。
CREATE PROCEDURE cancel_order(
  IN p_order BIGINT UNSIGNED, IN p_actor ENUM('CUSTOMER','SELLER'), IN p_reason VARCHAR(500))
BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT status INTO v_status FROM customer_order WHERE id = p_order FOR UPDATE;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order not exists';
  END IF;
  IF v_status = 'CANCELLED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order already cancelled';
  END IF;
  IF v_status = 'COMPLETED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed order cannot be cancelled';
  END IF;
  IF p_actor = 'CUSTOMER' AND v_status = 'SHIPPED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer cannot cancel after shipping';
  END IF;

  -- 回补库存
  UPDATE product p
  JOIN customer_order_item i ON i.product_id = p.id AND i.order_id = p_order
  SET p.stock = p.stock + i.quantity;

  -- 库存恢复后，把因售罄自动下架的商品重新上架
  UPDATE product p
  JOIN customer_order_item i ON i.product_id = p.id AND i.order_id = p_order
  SET p.status = 'ON_SALE'
  WHERE p.status = 'OFF_SHELF' AND p.stock > 0;

  UPDATE customer_order
  SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(6),
      cancel_actor = p_actor, cancel_reason = p_reason
  WHERE id = p_order;

  INSERT INTO order_status_log(order_id, from_status, to_status, actor)
  VALUES(p_order, v_status, 'CANCELLED', p_actor);

  COMMIT;
END$$

-- 13.7 购物车多件商品转为收藏（需求 B.2.4）
--   p_product_ids：JSON 数组；已收藏的商品忽略，转换后从购物车移除
CREATE PROCEDURE cart_to_favorite(IN p_customer BIGINT UNSIGNED, IN p_product_ids JSON)
BEGIN
  DECLARE v_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF p_product_ids IS NULL OR JSON_LENGTH(p_product_ids) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No product selected';
  END IF;
  START TRANSACTION;
  INSERT IGNORE INTO favorite(customer_id, product_id)
  SELECT p_customer, jt.pid
  FROM JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt
  JOIN cart_item c ON c.product_id = jt.pid AND c.customer_id = p_customer;
  SET v_count = ROW_COUNT();
  DELETE c FROM cart_item c
  JOIN JSON_TABLE(p_product_ids, '$[*]' COLUMNS(pid BIGINT PATH '$')) jt ON jt.pid = c.product_id
  WHERE c.customer_id = p_customer;
  COMMIT;
  SELECT v_count AS favorite_added;
END$$

DELIMITER ;
