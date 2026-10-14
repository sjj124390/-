-- =====================================================
-- 组员C（孙俊杰）的数据库 schema.sql
-- 来自 https://github.com/sjj124390/-/blob/main/database/schema.sql
-- 仅在独立测试库运行，不要在真实业务库使用虚构数据
-- =====================================================

CREATE DATABASE IF NOT EXISTS single_item_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE single_item_shop;

CREATE TABLE seller (
 id TINYINT UNSIGNED PRIMARY KEY,
 username VARCHAR(50) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL COMMENT '应用生成 BCrypt/Argon2 哈希',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_single_seller CHECK (id = 1)
) ENGINE=InnoDB;

CREATE TABLE product (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 seller_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
 name VARCHAR(100) NOT NULL,
 description TEXT NOT NULL,
 image_url VARCHAR(2048) NOT NULL,
 price DECIMAL(10,2) NOT NULL,
 status ENUM('DRAFT','ON_SALE','FROZEN','SOLD','OFF_SHELF') NOT NULL DEFAULT 'DRAFT',
 active_slot TINYINT GENERATED ALWAYS AS
  (CASE WHEN status IN ('ON_SALE','FROZEN') THEN 1 ELSE NULL END) STORED,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_one_active_product (active_slot),
 KEY idx_product_history (created_at),
 CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES seller(id),
 CONSTRAINT ck_product_price CHECK (price > 0)
) ENGINE=InnoDB;

CREATE TABLE purchase_intent (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT UNSIGNED NOT NULL,
 buyer_name VARCHAR(50) NOT NULL,
 contact VARCHAR(100) NOT NULL COMMENT '电话或其他联系方式，仅后台可见',
 note VARCHAR(500),
 code_hash BINARY(32) NOT NULL COMMENT '应用对随机口令码做 HMAC-SHA256，密钥不入库',
 code_expires_at DATETIME(6) NOT NULL,
 status ENUM('PENDING','SELECTED','COMPLETED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_code_hash (code_hash),
 UNIQUE KEY uq_intent_product (id, product_id),
 KEY idx_intent_product_status (product_id,status,created_at),
 CONSTRAINT fk_intent_product FOREIGN KEY (product_id) REFERENCES product(id),
 CONSTRAINT ck_intent_code_expiry CHECK (code_expires_at > created_at),
 CONSTRAINT ck_buyer_name CHECK (CHAR_LENGTH(TRIM(buyer_name)) > 0),
 CONSTRAINT ck_contact CHECK (CHAR_LENGTH(TRIM(contact)) > 0)
) ENGINE=InnoDB;

CREATE TABLE offline_trade (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT UNSIGNED NOT NULL,
 intent_id BIGINT UNSIGNED NOT NULL,
 agreed_price DECIMAL(10,2) NOT NULL COMMENT '冻结时的成交价快照',
 status ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
 pending_product BIGINT UNSIGNED GENERATED ALWAYS AS
  (CASE WHEN status = 'PENDING' THEN product_id ELSE NULL END) STORED,
 failure_reason VARCHAR(500),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at DATETIME(6),
 UNIQUE KEY uq_pending_trade (pending_product),
 KEY idx_trade_intent (intent_id,product_id),
 CONSTRAINT fk_trade_intent_product FOREIGN KEY (intent_id,product_id)
  REFERENCES purchase_intent(id,product_id),
 CONSTRAINT ck_trade_price CHECK (agreed_price > 0),
 CONSTRAINT ck_trade_finished CHECK (
  (status = 'PENDING' AND finished_at IS NULL) OR
  (status IN ('SUCCESS','FAILED') AND finished_at IS NOT NULL))
) ENGINE=InnoDB;

DELIMITER $$

CREATE PROCEDURE publish_product(IN p_product BIGINT UNSIGNED)
BEGIN
 DECLARE v_seller TINYINT UNSIGNED DEFAULT NULL;
 DECLARE v_status VARCHAR(20) DEFAULT NULL;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SELECT id INTO v_seller FROM seller WHERE id=1 FOR UPDATE;
 IF v_seller IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Initialize seller first'; END IF;
 SELECT status INTO v_status FROM product WHERE id=p_product FOR UPDATE;
 IF v_status IS NULL OR v_status NOT IN ('DRAFT','OFF_SHELF') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Product cannot be published';
 END IF;
 IF EXISTS(SELECT 1 FROM product WHERE status IN ('ON_SALE','FROZEN')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Another product is active';
 END IF;
 UPDATE product SET status='ON_SALE' WHERE id=p_product;
 COMMIT;
END$$

CREATE PROCEDURE submit_intent(
 IN p_product BIGINT UNSIGNED, IN p_name VARCHAR(50),
 IN p_contact VARCHAR(100), IN p_note VARCHAR(500),
 IN p_code_hash BINARY(32), IN p_expires DATETIME(6))
BEGIN
 DECLARE v_status VARCHAR(20) DEFAULT NULL;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SELECT status INTO v_status FROM product WHERE id=p_product FOR UPDATE;
 IF v_status IS NULL OR v_status <> 'ON_SALE' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Product is not available';
 END IF;
 IF p_code_hash IS NULL OR p_expires IS NULL OR p_expires <= CURRENT_TIMESTAMP(6) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid code or expiration';
 END IF;
 INSERT INTO purchase_intent(product_id,buyer_name,contact,note,code_hash,code_expires_at)
 VALUES(p_product,p_name,p_contact,p_note,p_code_hash,p_expires);
 SELECT LAST_INSERT_ID() AS intent_id;
 COMMIT;
END$$

CREATE PROCEDURE freeze_product(IN p_product BIGINT UNSIGNED, IN p_intent BIGINT UNSIGNED)
BEGIN
 DECLARE v_status VARCHAR(20) DEFAULT NULL;
 DECLARE v_intent_status VARCHAR(20) DEFAULT NULL;
 DECLARE v_expires DATETIME(6);
 DECLARE v_price DECIMAL(10,2);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SELECT status,price INTO v_status,v_price FROM product WHERE id=p_product FOR UPDATE;
 IF v_status IS NULL OR v_status <> 'ON_SALE' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Product cannot be frozen';
 END IF;
 SELECT status,code_expires_at INTO v_intent_status,v_expires
 FROM purchase_intent WHERE id=p_intent AND product_id=p_product FOR UPDATE;
 IF v_intent_status IS NULL OR v_intent_status <> 'PENDING' OR v_expires <= CURRENT_TIMESTAMP(6) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Intent is unavailable or expired';
 END IF;
 UPDATE product SET status='FROZEN' WHERE id=p_product;
 UPDATE purchase_intent SET status='SELECTED' WHERE id=p_intent;
 INSERT INTO offline_trade(product_id,intent_id,agreed_price) VALUES(p_product,p_intent,v_price);
 SELECT LAST_INSERT_ID() AS trade_id;
 COMMIT;
END$$

CREATE PROCEDURE finish_trade(
 IN p_product BIGINT UNSIGNED, IN p_trade BIGINT UNSIGNED,
 IN p_success BOOLEAN, IN p_reason VARCHAR(500))
BEGIN
 DECLARE v_product_status VARCHAR(20) DEFAULT NULL;
 DECLARE v_trade_status VARCHAR(20) DEFAULT NULL;
 DECLARE v_intent BIGINT UNSIGNED;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF p_success IS NULL OR p_success NOT IN (0,1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid result';
 END IF;
 SELECT status INTO v_product_status FROM product WHERE id=p_product FOR UPDATE;
 SELECT status,intent_id INTO v_trade_status,v_intent
 FROM offline_trade WHERE id=p_trade AND product_id=p_product FOR UPDATE;
 IF v_product_status IS NULL OR v_product_status <> 'FROZEN'
  OR v_trade_status IS NULL OR v_trade_status <> 'PENDING' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Trade is not pending';
 END IF;
 UPDATE offline_trade
 SET status=IF(p_success,'SUCCESS','FAILED'), finished_at=CURRENT_TIMESTAMP(6),
 failure_reason=IF(p_success,NULL,p_reason) WHERE id=p_trade;
 UPDATE purchase_intent SET status=IF(p_success,'COMPLETED','CANCELLED') WHERE id=v_intent;
 UPDATE product SET status=IF(p_success,'SOLD','ON_SALE') WHERE id=p_product;
 IF p_success THEN
  UPDATE purchase_intent SET status='CANCELLED' WHERE product_id=p_product AND status='PENDING';
 END IF;
 COMMIT;
END$$

DELIMITER ;
