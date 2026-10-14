-- =====================================================
-- 组员C 的 schema.sql（MySQL 5.7 兼容版）
-- 改动：utf8mb4_0900_ai_ci -> utf8mb4_unicode_ci；CHECK 约束在 5.7 被忽略（非致命）
-- 存储过程需用 mysql.exe --delimiter='$$' 执行
-- =====================================================

CREATE DATABASE IF NOT EXISTS single_item_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE single_item_shop;

DROP TABLE IF EXISTS offline_trade;
DROP TABLE IF EXISTS purchase_intent;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS seller;
DROP PROCEDURE IF EXISTS publish_product;
DROP PROCEDURE IF EXISTS submit_intent;
DROP PROCEDURE IF EXISTS freeze_product;
DROP PROCEDURE IF EXISTS finish_trade;

CREATE TABLE seller (
 id TINYINT UNSIGNED PRIMARY KEY,
 username VARCHAR(50) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT ck_single_seller CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uq_one_active_product (active_slot),
 KEY idx_product_history (created_at),
 CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES seller(id),
 CONSTRAINT ck_product_price CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_intent (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT UNSIGNED NOT NULL,
 buyer_name VARCHAR(50) NOT NULL,
 contact VARCHAR(100) NOT NULL COMMENT '电话/联系方式',
 note VARCHAR(500),
 code_hash BINARY(32) NOT NULL COMMENT 'HMAC-SHA256 摘要',
 code_expires_at DATETIME NOT NULL,
 status ENUM('PENDING','SELECTED','COMPLETED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uq_code_hash (code_hash),
 UNIQUE KEY uq_intent_product (id, product_id),
 KEY idx_intent_product_status (product_id,status,created_at),
 CONSTRAINT fk_intent_product FOREIGN KEY (product_id) REFERENCES product(id),
 CONSTRAINT ck_intent_code_expiry CHECK (code_expires_at > created_at),
 CONSTRAINT ck_buyer_name CHECK (CHAR_LENGTH(TRIM(buyer_name)) > 0),
 CONSTRAINT ck_contact CHECK (CHAR_LENGTH(TRIM(contact)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offline_trade (
 id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
 product_id BIGINT UNSIGNED NOT NULL,
 intent_id BIGINT UNSIGNED NOT NULL,
 agreed_price DECIMAL(10,2) NOT NULL,
 status ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
 pending_product BIGINT UNSIGNED GENERATED ALWAYS AS
  (CASE WHEN status = 'PENDING' THEN product_id ELSE NULL END) STORED,
 failure_reason VARCHAR(500),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at DATETIME NULL,
 UNIQUE KEY uq_pending_trade (pending_product),
 KEY idx_trade_intent (intent_id,product_id),
 CONSTRAINT fk_trade_intent_product FOREIGN KEY (intent_id,product_id)
  REFERENCES purchase_intent(id,product_id),
 CONSTRAINT ck_trade_price CHECK (agreed_price > 0),
 CONSTRAINT ck_trade_finished CHECK (
  (status = 'PENDING' AND finished_at IS NULL) OR
  (status IN ('SUCCESS','FAILED') AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
