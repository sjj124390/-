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
 IN p_code_hash BINARY(32), IN p_expires DATETIME)
BEGIN
 DECLARE v_status VARCHAR(20) DEFAULT NULL;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SELECT status INTO v_status FROM product WHERE id=p_product FOR UPDATE;
 IF v_status IS NULL OR v_status <> 'ON_SALE' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Product is not available';
 END IF;
 IF p_code_hash IS NULL OR p_expires IS NULL OR p_expires <= CURRENT_TIMESTAMP THEN
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
 DECLARE v_expires DATETIME;
 DECLARE v_price DECIMAL(10,2);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 SELECT status,price INTO v_status,v_price FROM product WHERE id=p_product FOR UPDATE;
 IF v_status IS NULL OR v_status <> 'ON_SALE' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Product cannot be frozen';
 END IF;
 SELECT status,code_expires_at INTO v_intent_status,v_expires
 FROM purchase_intent WHERE id=p_intent AND product_id=p_product FOR UPDATE;
 IF v_intent_status IS NULL OR v_intent_status <> 'PENDING' OR v_expires <= CURRENT_TIMESTAMP THEN
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
 SET status=IF(p_success,'SUCCESS','FAILED'), finished_at=CURRENT_TIMESTAMP,
 failure_reason=IF(p_success,NULL,p_reason) WHERE id=p_trade;
 UPDATE purchase_intent SET status=IF(p_success,'COMPLETED','CANCELLED') WHERE id=v_intent;
 UPDATE product SET status=IF(p_success,'SOLD','ON_SALE') WHERE id=p_product;
 IF p_success THEN
  UPDATE purchase_intent SET status='CANCELLED' WHERE product_id=p_product AND status='PENDING';
 END IF;
 COMMIT;
END$$
