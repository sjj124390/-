#!/usr/bin/env python3
"""在独立 MySQL 验收库中执行数据库回归测试，不访问业务库数据。

用法：
  set MYSQL_PWD=你的密码
  python database/acceptance_test.py --user root

仅允许创建/删除名称以 single_item_shop_acceptance_ 开头的数据库。
依赖 Python 3 和 mysql 命令行客户端，无需第三方 Python 包。
"""

import argparse
import concurrent.futures
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time


ROOT = Path(__file__).resolve().parents[1]
DB_PREFIX = "single_item_shop_acceptance_"
PROCEDURES = {
    "publish_product",
    "submit_intent",
    "freeze_product",
    "finish_trade",
    "withdraw_product",
}
TABLES = {"seller", "product", "purchase_intent", "offline_trade"}


class MySQL:
    def __init__(self, args):
        executable = args.mysql or shutil.which("mysql")
        if not executable:
            raise RuntimeError("找不到 mysql 客户端，请使用 --mysql 指定 mysql.exe")
        password = args.password if args.password is not None else os.environ.get("MYSQL_PWD", "")
        self.env = os.environ.copy()
        self.env["MYSQL_PWD"] = password
        self.base = [
            executable,
            f"--host={args.host}",
            f"--port={args.port}",
            f"--user={args.user}",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--skip-column-names",
            "--connect-timeout=5",
        ]

    def execute(self, sql, database=None, check=True, delimiter=None, timeout=40):
        command = self.base.copy()
        if database:
            command.append(f"--database={database}")
        if delimiter:
            command.append(f"--delimiter={delimiter}")
        result = subprocess.run(
            command,
            input=sql,
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
            env=self.env,
            timeout=timeout,
        )
        if check and result.returncode != 0:
            detail = (result.stderr or result.stdout).strip()
            raise AssertionError(f"SQL 执行失败：{detail}\nSQL: {sql[:500]}")
        return result

    def query(self, sql, database):
        result = self.execute(sql, database=database)
        return [line.split("\t") for line in result.stdout.splitlines() if line]

    def scalar(self, sql, database):
        rows = self.query(sql, database)
        if len(rows) != 1 or len(rows[0]) != 1:
            raise AssertionError(f"期望单个查询值，实际为 {rows!r}")
        return rows[0][0]

    def expect_error(self, sql, database, fragment=None):
        result = self.execute(sql, database=database, check=False)
        if result.returncode == 0:
            raise AssertionError(f"预期 SQL 失败但执行成功：{sql}")
        detail = (result.stderr or result.stdout).strip()
        if fragment and fragment not in detail:
            raise AssertionError(f"错误信息不符合预期：期望包含 {fragment!r}，实际 {detail!r}")
        return detail


class Acceptance:
    def __init__(self, mysql, database):
        self.mysql = mysql
        self.database = database
        self.results = []
        self.extra_databases = []
        self.sequence = 0
        self.a = None
        self.b = None

    def reset(self):
        db = self.database
        self.mysql.execute("DROP TRIGGER IF EXISTS trg_acceptance_force_intent_update_failure", db)
        self.mysql.execute("DELETE FROM offline_trade", db)
        self.mysql.execute("DELETE FROM purchase_intent", db)
        self.mysql.execute("DELETE FROM product", db)
        self.mysql.execute("DELETE FROM seller", db)
        self.mysql.execute(
            "INSERT INTO seller(id,username,password_hash) "
            "VALUES(1,'acceptance_seller','$2a$10$acceptance_only_not_for_login')",
            db,
        )
        self.mysql.execute(
            "INSERT INTO product(name,description,image_url,price) VALUES "
            "('验收商品A','独立库测试数据','test-a.jpg',100.00),"
            "('验收商品B','独立库测试数据','test-b.jpg',200.00)",
            db,
        )
        self.a = int(self.mysql.scalar("SELECT id FROM product WHERE name='验收商品A'", db))
        self.b = int(self.mysql.scalar("SELECT id FROM product WHERE name='验收商品B'", db))
        self.sequence = 0

    def run_case(self, case_id, title, function, reset=True):
        started = time.monotonic()
        try:
            if reset:
                self.reset()
            detail = function() or "断言全部通过"
            self.results.append((case_id, title, "PASS", detail))
            print(f"PASS|{case_id}|{title}|{detail}|{time.monotonic() - started:.2f}s", flush=True)
        except Exception as exc:
            self.results.append((case_id, title, "FAIL", str(exc)))
            print(f"FAIL|{case_id}|{title}|{exc}", flush=True)

    def call(self, procedure, arguments):
        self.mysql.execute(f"CALL {procedure}({arguments})", self.database)

    def expect_call_error(self, procedure, arguments, fragment=None):
        return self.mysql.expect_error(
            f"CALL {procedure}({arguments})", self.database, fragment=fragment
        )

    def hash_for(self, label):
        self.sequence += 1
        return hashlib.sha256(f"{self.database}:{label}:{self.sequence}".encode()).hexdigest()

    def submit(self, product_id, label):
        digest = self.hash_for(label)
        self.call(
            "submit_intent",
            f"{product_id},'买家{label}','13800000000','验收意向',"
            f"UNHEX('{digest}'),DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",
        )
        return int(
            self.mysql.scalar(
                f"SELECT id FROM purchase_intent WHERE code_hash=UNHEX('{digest}')",
                self.database,
            )
        )

    def product_status(self, product_id):
        return self.mysql.scalar(
            f"SELECT status FROM product WHERE id={product_id}", self.database
        )

    def intent_status(self, intent_id):
        return self.mysql.scalar(
            f"SELECT status FROM purchase_intent WHERE id={intent_id}", self.database
        )

    def prepare_frozen(self):
        self.call("publish_product", str(self.a))
        first = self.submit(self.a, "I1")
        second = self.submit(self.a, "I2")
        self.call("freeze_product", f"{self.a},{first}")
        trade = int(
            self.mysql.scalar(
                f"SELECT id FROM offline_trade WHERE product_id={self.a} AND status='PENDING'",
                self.database,
            )
        )
        return first, second, trade

    def test_structure(self):
        tables = {
            row[0]
            for row in self.mysql.query(
                "SELECT table_name FROM information_schema.tables "
                f"WHERE table_schema='{self.database}' AND table_type='BASE TABLE'",
                self.database,
            )
        }
        procedures = {
            row[0]
            for row in self.mysql.query(
                "SELECT routine_name FROM information_schema.routines "
                f"WHERE routine_schema='{self.database}' AND routine_type='PROCEDURE'",
                self.database,
            )
        }
        assert tables == TABLES, tables
        assert procedures == PROCEDURES, procedures
        collations = int(
            self.mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.tables "
                f"WHERE table_schema='{self.database}' "
                "AND table_collation='utf8mb4_unicode_ci'",
                self.database,
            )
        )
        assert collations == 4, collations
        foreign_keys = int(
            self.mysql.scalar(
                "SELECT COUNT(DISTINCT constraint_name) FROM information_schema.key_column_usage "
                f"WHERE table_schema='{self.database}' AND referenced_table_name IS NOT NULL",
                self.database,
            )
        )
        assert foreign_keys == 3, foreign_keys
        unique_pending = self.mysql.scalar(
            "SELECT non_unique FROM information_schema.statistics "
            f"WHERE table_schema='{self.database}' AND table_name='offline_trade' "
            "AND index_name='uq_pending_trade' LIMIT 1",
            self.database,
        )
        assert unique_pending == "0", unique_pending
        return "4表、5过程、3外键、字符集和唯一索引符合预期"

    def test_publish_uniqueness(self):
        self.call("publish_product", str(self.a))
        self.expect_call_error("publish_product", str(self.b), "Another product is active")
        assert self.product_status(self.a) == "ON_SALE"
        assert self.product_status(self.b) == "DRAFT"
        active = self.mysql.scalar(
            "SELECT COUNT(*) FROM product WHERE status IN ('ON_SALE','FROZEN')", self.database
        )
        assert active == "1"
        return "第二件商品发布被拒，首件商品仍在售"

    def test_valid_intents(self):
        self.call("publish_product", str(self.a))
        first = self.submit(self.a, "I1")
        second = self.submit(self.a, "I2")
        assert first != second
        assert self.intent_status(first) == "PENDING"
        assert self.intent_status(second) == "PENDING"
        return "两条意向均生成独立主键和唯一摘要"

    def test_invalid_intents(self):
        self.call("publish_product", str(self.a))
        future = "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)"
        valid_hash = f"UNHEX('{self.hash_for('valid')}')"
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'   ','13800000000',NULL,{valid_hash},{future}",
            "Invalid buyer name or contact",
        )
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'买家','   ',NULL,{valid_hash},{future}",
            "Invalid buyer name or contact",
        )
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'买家','13800000000',NULL,{valid_hash},"
            "DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND)",
            "Invalid code or expiration",
        )
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'买家','13800000000',NULL,NULL,{future}",
            "Invalid code or expiration",
        )
        count = self.mysql.scalar("SELECT COUNT(*) FROM purchase_intent", self.database)
        assert count == "0"
        return "纯空白姓名、联系方式、过期时间和空摘要均被过程拦截"

    def test_duplicate_hash(self):
        self.call("publish_product", str(self.a))
        digest = self.hash_for("duplicate")
        args = (
            f"{self.a},'买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)"
        )
        self.call("submit_intent", args)
        self.expect_call_error("submit_intent", args, "Duplicate entry")
        count = self.mysql.scalar("SELECT COUNT(*) FROM purchase_intent", self.database)
        assert count == "1"
        return "相同口令摘要第二次写入被唯一索引拒绝"

    def test_freeze_success(self):
        first, second, trade = self.prepare_frozen()
        assert self.product_status(self.a) == "FROZEN"
        assert self.intent_status(first) == "SELECTED"
        assert self.intent_status(second) == "PENDING"
        status, price, finished = self.mysql.query(
            f"SELECT status,agreed_price,finished_at FROM offline_trade WHERE id={trade}",
            self.database,
        )[0]
        assert (status, price, finished) == ("PENDING", "100.00", "NULL")
        return "商品、选中意向及成交价快照在同一事务中更新"

    def test_freeze_guards(self):
        first, _, trade = self.prepare_frozen()
        self.expect_call_error("publish_product", str(self.b), "Another product is active")
        digest = self.hash_for("frozen")
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",
            "Product is not available",
        )
        self.expect_call_error(
            "freeze_product", f"{self.a},{first}", "Product cannot be frozen"
        )
        pending = self.mysql.scalar(
            f"SELECT COUNT(*) FROM offline_trade WHERE id={trade} AND status='PENDING'",
            self.database,
        )
        assert pending == "1"
        return "冻结期间发布、新意向和重复冻结均被拒，原交易不变"

    def test_cross_product_freeze(self):
        self.call("publish_product", str(self.b))
        other = self.submit(self.b, "B-I1")
        self.mysql.execute(f"UPDATE product SET status='DRAFT' WHERE id={self.b}", self.database)
        self.call("publish_product", str(self.a))
        self.submit(self.a, "A-I1")
        self.expect_call_error(
            "freeze_product", f"{self.a},{other}", "Intent is unavailable or expired"
        )
        assert self.product_status(self.a) == "ON_SALE"
        trades = self.mysql.scalar("SELECT COUNT(*) FROM offline_trade", self.database)
        assert trades == "0"
        return "属于其他商品的意向不能冻结当前商品"

    def test_finish_wrong_product(self):
        _, _, trade = self.prepare_frozen()
        self.expect_call_error(
            "finish_trade", f"{self.b},{trade},0,'错误商品'", "Trade is not pending"
        )
        assert self.product_status(self.a) == "FROZEN"
        status = self.mysql.scalar(
            f"SELECT status FROM offline_trade WHERE id={trade}", self.database
        )
        assert status == "PENDING"
        return "product_id 不匹配时结束交易被拒"

    def test_finish_failure(self):
        first, second, trade = self.prepare_frozen()
        self.call("finish_trade", f"{self.a},{trade},0,'线下未成交'")
        assert self.product_status(self.a) == "ON_SALE"
        assert self.intent_status(first) == "CANCELLED"
        assert self.intent_status(second) == "PENDING"
        status, reason, finished = self.mysql.query(
            f"SELECT status,failure_reason,finished_at FROM offline_trade WHERE id={trade}",
            self.database,
        )[0]
        assert status == "FAILED" and reason == "线下未成交" and finished != "NULL"
        self.call("freeze_product", f"{self.a},{second}")
        assert self.product_status(self.a) == "FROZEN"
        return "失败交易恢复在售，另一待处理意向仍可被选中"

    def test_finish_success(self):
        first, second, trade = self.prepare_frozen()
        self.call("finish_trade", f"{self.a},{trade},1,NULL")
        assert self.product_status(self.a) == "SOLD"
        assert self.intent_status(first) == "COMPLETED"
        assert self.intent_status(second) == "CANCELLED"
        status, reason, finished = self.mysql.query(
            f"SELECT status,failure_reason,finished_at FROM offline_trade WHERE id={trade}",
            self.database,
        )[0]
        assert status == "SUCCESS" and reason == "NULL" and finished != "NULL"
        return "成交后商品已售、选中意向完成、其余待处理意向取消"

    def test_terminal_guards(self):
        first, _, trade = self.prepare_frozen()
        self.call("finish_trade", f"{self.a},{trade},1,NULL")
        self.expect_call_error(
            "finish_trade", f"{self.a},{trade},1,NULL", "Trade is not pending"
        )
        self.expect_call_error("publish_product", str(self.a), "Product cannot be published")
        self.call("publish_product", str(self.b))
        assert self.product_status(self.b) == "ON_SALE"
        return "重复结算和已售商品重发被拒，新草稿可发布"

    @staticmethod
    def concurrent(functions):
        barrier = threading.Barrier(len(functions))

        def invoke(function):
            barrier.wait()
            return function()

        with concurrent.futures.ThreadPoolExecutor(max_workers=len(functions)) as pool:
            futures = [pool.submit(invoke, function) for function in functions]
            return [future.result() for future in futures]

    def test_concurrent_freeze(self):
        self.call("publish_product", str(self.a))
        first = self.submit(self.a, "I1")
        second = self.submit(self.a, "I2")
        outcomes = self.concurrent(
            [
                lambda: self.mysql.execute(
                    f"CALL freeze_product({self.a},{first})", self.database, check=False
                ),
                lambda: self.mysql.execute(
                    f"CALL freeze_product({self.a},{second})", self.database, check=False
                ),
            ]
        )
        codes = sorted(result.returncode for result in outcomes)
        assert codes == [0, 1], [result.stderr for result in outcomes]
        assert self.product_status(self.a) == "FROZEN"
        trades = self.mysql.scalar(
            "SELECT COUNT(*) FROM offline_trade WHERE status='PENDING'", self.database
        )
        selected = self.mysql.scalar(
            "SELECT COUNT(*) FROM purchase_intent WHERE status='SELECTED'", self.database
        )
        assert trades == "1" and selected == "1"
        return "两个连接仅一个冻结成功，无重复待处理交易"

    def test_concurrent_publish(self):
        outcomes = self.concurrent(
            [
                lambda: self.mysql.execute(
                    f"CALL publish_product({self.a})", self.database, check=False
                ),
                lambda: self.mysql.execute(
                    f"CALL publish_product({self.b})", self.database, check=False
                ),
            ]
        )
        codes = sorted(result.returncode for result in outcomes)
        assert codes == [0, 1], [result.stderr for result in outcomes]
        active = self.mysql.scalar(
            "SELECT COUNT(*) FROM product WHERE status IN ('ON_SALE','FROZEN')", self.database
        )
        assert active == "1"
        return "两个连接仅一个发布成功，活动商品槽位保持唯一"

    def test_concurrent_freeze_submit(self):
        self.call("publish_product", str(self.a))
        first = self.submit(self.a, "I1")
        digest = self.hash_for("racing-intent")
        submit_sql = (
            f"CALL submit_intent({self.a},'并发买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR))"
        )
        freeze_result, submit_result = self.concurrent(
            [
                lambda: self.mysql.execute(
                    f"CALL freeze_product({self.a},{first})", self.database, check=False
                ),
                lambda: self.mysql.execute(submit_sql, self.database, check=False),
            ]
        )
        assert freeze_result.returncode == 0, freeze_result.stderr
        assert self.product_status(self.a) == "FROZEN"
        assert self.intent_status(first) == "SELECTED"
        trades = self.mysql.scalar(
            "SELECT COUNT(*) FROM offline_trade WHERE status='PENDING'", self.database
        )
        assert trades == "1"
        digest = self.hash_for("after-freeze")
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'之后买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",
            "Product is not available",
        )
        order = "意向先提交后冻结" if submit_result.returncode == 0 else "先冻结，意向被拒"
        return f"商品行锁串行化（{order}），冻结后新意向必被拒"

    def test_withdraw_success(self):
        self.call("publish_product", str(self.a))
        first = self.submit(self.a, "I1")
        second = self.submit(self.a, "I2")
        self.call("withdraw_product", str(self.a))
        assert self.product_status(self.a) == "OFF_SHELF"
        assert self.intent_status(first) == "CANCELLED"
        assert self.intent_status(second) == "CANCELLED"
        return "在售商品下架成功，两条待处理意向同步取消"

    def test_frozen_withdraw(self):
        first, _, trade = self.prepare_frozen()
        self.expect_call_error(
            "withdraw_product", str(self.a), "Trading frozen product cannot be withdrawn"
        )
        assert self.product_status(self.a) == "FROZEN"
        assert self.intent_status(first) == "SELECTED"
        status = self.mysql.scalar(
            f"SELECT status FROM offline_trade WHERE id={trade}", self.database
        )
        assert status == "PENDING"
        return "交易中商品禁止下架，商品、意向和交易均未改变"

    def test_off_shelf_restore(self):
        self.call("publish_product", str(self.a))
        intent = self.submit(self.a, "I1")
        self.call("withdraw_product", str(self.a))
        digest = self.hash_for("off-shelf")
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",
            "Product is not available",
        )
        self.expect_call_error(
            "freeze_product", f"{self.a},{intent}", "Product cannot be frozen"
        )
        self.call("publish_product", str(self.a))
        assert self.product_status(self.a) == "ON_SALE"
        return "下架状态拒绝新意向和冻结，允许重新发布恢复在售"

    def test_concurrent_withdraw_submit(self):
        self.call("publish_product", str(self.a))
        self.submit(self.a, "I1")
        digest = self.hash_for("withdraw-race")
        submit_sql = (
            f"CALL submit_intent({self.a},'并发买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR))"
        )
        withdraw_result, submit_result = self.concurrent(
            [
                lambda: self.mysql.execute(
                    f"CALL withdraw_product({self.a})", self.database, check=False
                ),
                lambda: self.mysql.execute(submit_sql, self.database, check=False),
            ]
        )
        assert withdraw_result.returncode == 0, withdraw_result.stderr
        assert self.product_status(self.a) == "OFF_SHELF"
        pending = self.mysql.scalar(
            "SELECT COUNT(*) FROM purchase_intent WHERE status='PENDING'", self.database
        )
        assert pending == "0"
        digest = self.hash_for("after-withdraw")
        self.expect_call_error(
            "submit_intent",
            f"{self.a},'之后买家','13800000000',NULL,UNHEX('{digest}'),"
            "DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR)",
            "Product is not available",
        )
        order = "意向先提交后被下架取消" if submit_result.returncode == 0 else "先下架，意向被拒"
        return f"商品行锁串行化（{order}），最终无有效待处理意向"

    def test_withdraw_rollback(self):
        self.call("publish_product", str(self.a))
        intent = self.submit(self.a, "I1")
        self.mysql.execute(
            "DELIMITER $$\n"
            "CREATE TRIGGER trg_acceptance_force_intent_update_failure "
            "BEFORE UPDATE ON purchase_intent FOR EACH ROW BEGIN "
            "IF OLD.status='PENDING' AND NEW.status='CANCELLED' THEN "
            "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced intent update failure'; "
            "END IF; END$$\nDELIMITER ;",
            self.database,
        )
        self.expect_call_error("withdraw_product", str(self.a), "Forced intent update failure")
        assert self.product_status(self.a) == "ON_SALE"
        assert self.intent_status(intent) == "PENDING"
        self.mysql.execute(
            "DROP TRIGGER IF EXISTS trg_acceptance_force_intent_update_failure", self.database
        )
        return "测试触发器强制第二步失败，商品状态与意向均回滚"

    def variant_smoke(self, suffix, scripts):
        database = f"{self.database}_{suffix}"
        self.extra_databases.append(database)
        self.mysql.execute(f"DROP DATABASE IF EXISTS {database}")
        try:
            for index, (relative, delimiter) in enumerate(scripts):
                source = (ROOT / relative).read_text(encoding="utf-8")
                source = source.replace("single_item_shop", database)
                self.mysql.execute(
                    source, database=database if index else None, delimiter=delimiter
                )
            routines = {
                row[0]
                for row in self.mysql.query(
                    "SELECT routine_name FROM information_schema.routines "
                    f"WHERE routine_schema='{database}' AND routine_type='PROCEDURE'",
                    database,
                )
            }
            assert routines == PROCEDURES, routines
            self.mysql.execute(
                "INSERT INTO seller(id,username,password_hash) VALUES(1,'variant','test-only');"
                "INSERT INTO product(name,description,image_url,price) "
                "VALUES('脚本验收','独立库测试','variant.jpg',99.00)",
                database,
            )
            product = self.mysql.scalar("SELECT id FROM product LIMIT 1", database)
            self.mysql.execute(f"CALL publish_product({product})", database)
            digest = self.hash_for(suffix)
            self.mysql.expect_error(
                f"CALL submit_intent({product},'   ','contact',NULL,UNHEX('{digest}'),"
                "DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 HOUR))",
                database,
                fragment="Invalid buyer name or contact",
            )
            self.mysql.execute(
                f"CALL submit_intent({product},'买家','contact',NULL,UNHEX('{digest}'),"
                "DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 1 HOUR))",
                database,
            )
            self.mysql.execute(f"CALL withdraw_product({product})", database)
            pending = self.mysql.scalar(
                "SELECT COUNT(*) FROM purchase_intent WHERE status='PENDING'", database
            )
            assert pending == "0"
            self.mysql.execute(f"CALL publish_product({product})", database)
            status = self.mysql.scalar(
                f"SELECT status FROM product WHERE id={product}", database
            )
            assert status == "ON_SALE"
            return "脚本可安装，非空校验、下架取消意向和恢复上架均通过"
        finally:
            if not self.keep:
                self.mysql.execute(f"DROP DATABASE IF EXISTS {database}")

    def run(self, keep=False):
        self.keep = keep
        self.run_case("DB-01", "表、过程、外键、字符集与唯一索引", self.test_structure)
        self.run_case("DB-02", "单件在售约束", self.test_publish_uniqueness)
        self.run_case("DB-03", "有效购买意向", self.test_valid_intents)
        self.run_case("DB-04", "非法购买意向输入", self.test_invalid_intents)
        self.run_case("DB-05", "口令摘要唯一性", self.test_duplicate_hash)
        self.run_case("DB-06", "冻结与成交价快照", self.test_freeze_success)
        self.run_case("DB-07", "冻结期间业务保护", self.test_freeze_guards)
        self.run_case("DB-08", "跨商品冻结拒绝", self.test_cross_product_freeze)
        self.run_case("DB-09", "跨商品结算拒绝", self.test_finish_wrong_product)
        self.run_case("DB-10", "交易失败恢复", self.test_finish_failure)
        self.run_case("DB-11", "交易成功收尾", self.test_finish_success)
        self.run_case("DB-12", "重复结算与已售保护", self.test_terminal_guards)
        self.run_case("DB-13", "并发冻结同一商品", self.test_concurrent_freeze)
        self.run_case("DB-14", "并发发布不同商品", self.test_concurrent_publish)
        self.run_case("DB-15", "并发冻结与提交意向", self.test_concurrent_freeze_submit)
        self.run_case("DB-16", "在售下架与意向取消", self.test_withdraw_success)
        self.run_case("DB-17", "冻结商品禁止下架", self.test_frozen_withdraw)
        self.run_case("DB-18", "下架保护与恢复上架", self.test_off_shelf_restore)
        self.run_case("DB-19", "并发下架与提交意向", self.test_concurrent_withdraw_submit)
        self.run_case("DB-20", "下架第二步异常回滚", self.test_withdraw_rollback)
        self.run_case(
            "DB-21",
            "后端 schema-memberC.sql 安装与冒烟验收",
            lambda: self.variant_smoke(
                "memberc", [("backend/src/main/resources/sql/schema-memberC.sql", None)]
            ),
            reset=False,
        )
        self.run_case(
            "DB-22",
            "后端 MySQL 5.7 分体脚本安装与冒烟验收",
            lambda: self.variant_smoke(
                "mysql57",
                [
                    ("backend/src/main/resources/sql/schema-mysql57.sql", None),
                    ("backend/src/main/resources/sql/procedures-mysql57.sql", "$$"),
                ],
            ),
            reset=False,
        )
        return self.results


def validate_database_name(name):
    if not re.fullmatch(r"single_item_shop_acceptance_[A-Za-z0-9_]+", name):
        raise ValueError(f"验收库名必须以 {DB_PREFIX} 开头且只含字母、数字、下划线：{name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="3306")
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", help="也可通过 MYSQL_PWD 环境变量提供")
    parser.add_argument("--mysql", help="mysql.exe 完整路径")
    parser.add_argument("--database", help=f"可选；必须以 {DB_PREFIX} 开头")
    parser.add_argument("--keep", action="store_true", help="保留验收库，默认结束后清理")
    args = parser.parse_args()

    database = args.database or f"{DB_PREFIX}{os.getpid()}_{int(time.time())}"
    validate_database_name(database)
    mysql = MySQL(args)
    version = mysql.scalar("SELECT VERSION()", None)
    acceptance = Acceptance(mysql, database)
    print(f"MYSQL_VERSION={version}")
    print(f"TEST_DATABASE={database}")
    print("BUSINESS_DATABASE_ACCESSED=NO")
    try:
        mysql.execute(f"DROP DATABASE IF EXISTS {database}")
        schema = (ROOT / "database/schema.sql").read_text(encoding="utf-8")
        mysql.execute(schema.replace("single_item_shop", database))
        results = acceptance.run(keep=args.keep)
    finally:
        if not args.keep:
            mysql.execute(f"DROP DATABASE IF EXISTS {database}")
    passed = sum(result[2] == "PASS" for result in results)
    failed = len(results) - passed
    print(f"RESULT|total={len(results)}|passed={passed}|failed={failed}")
    print(f"CLEANUP={'KEPT' if args.keep else 'DROPPED'}")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"FATAL|{exc}", file=sys.stderr)
        sys.exit(2)
