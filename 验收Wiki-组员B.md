# 在线购物系统 · 后端

> 组员B · 后端开发负责人 · 软件工程实践 · 第 1 次阶段性评审

**13 个 REST 接口** · **4 个存储过程** · **13 项自测全部通过** · Spring Boot 2.7 + JdbcTemplate · MySQL 5.7 存储过程

| 项 | 值 |
|---|---|
| 项目仓库 | 小组 GitHub 仓库 `backend/` 目录 |
| 协作关系 | 组员A 前端 ↔ **我（B）后端** ↔ 组员C 数据库（schema + SP） |
| 运行入口 | `java -jar target\online-shopping-backend-1.0.0.jar`（8080 端口） |
| 卖家账号 | `admin / admin123`（启动自动初始化，seller.id 固定为 1） |

---

## 我的工作

### 1. 接口层：13 个 REST 接口
覆盖商品发布、购买意向提交、口令码查询、商品冻结、成交/失败结算、卖家登录、修改密码。另外为对齐组员 A 的前端契约，提供适配层 Controller（Facade），把前端的 10 个接口映射到后端服务上，**两边代码对接**。

### 2. 一致性下沉到数据库
商品、意向、交易三张表上的原子操作全部由 MySQL 存储过程完成，SP 内部自管事务（START TRANSACTION / COMMIT），Java 层只负责 CallableStatement 传参与结果映射。**"只允许一个在售商品""冻结期间禁止新意向"由唯一索引 + SP 共同兜底，不依赖应用代码写对。**

### 3. 异常透传
SP 用 `SIGNAL SQLSTATE '45000'` 抛业务错误，Spring 会将其包装成 DataAccessException。我写了解包工具沿 cause 链提取 MESSAGE_TEXT，转成统一 `Result{code,message}`，前端直接拿到可读文案

### 4. 安全设计
- 卖家密码：BCrypt 加密存储，修改密码用条件 UPDATE 防并发
- 口令码：SecureRandom 16 字节 → 32 位十六进制明文（仅展示一次），数据库只存 HMAC-SHA256 的 32 字节摘要（BINARY(32)），密钥取环境变量，验证用恒定时间比较防时序攻击，7 天过期

---

## 技术架构

```
浏览器 / 前端(组员A)
      │  HTTP JSON
      ▼
Controller（5 个）── FacadeController（适配组员A 契约）
      │
      ▼
Service（4 个）─── CallableStatement 调 SP
      │
      ▼
MySQL 5.7 · single_item_shop
  seller / product / purchase_intent / offline_trade
  publish_product · submit_intent · freeze_product · finish_trade
  唯一索引 uq_one_active_product（生成列 active_slot 兜底）
```

> 设计原则：规则的唯一事实来源在数据库层。SP 抛错 → 解包 → 前端可读，一条链路贯穿"数据库兜底 → 异常透传 → 前端可读"。

---

## 接口清单（13 个）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/products/current | 当前在售/冻结商品 |
| POST | /api/intents | 提交购买意向，返回一次性口令码 |
| POST | /api/intents/lookup | 买家凭口令码查进度 |
| POST | /api/admin/products | 创建草稿商品 |
| POST | /api/admin/products/{id}/publish | 发布（SP 兜底唯一在售） |
| POST | /api/admin/products/{id}/withdraw | 下架 + 取消待处理意向 |
| GET | /api/admin/products | 商品历史（全部状态） |
| GET | /api/admin/products/{id}/intents | 意向名单（仅后台可见） |
| POST | /api/admin/products/{id}/freeze | 选中意向冻结商品（生成待处理交易） |
| POST | /api/admin/trades/{id}/finish | 成交成功/失败结算 |
| GET | /api/admin/trades/pending | 待处理交易列表 |
| POST | /api/admin/login | 卖家登录（BCrypt 校验） |
| PUT | /api/admin/password | 修改密码（条件 UPDATE 防并发） |

> 另有 FacadeController 10 个适配接口（`/api/product/*`、`/api/order/*`、`/api/seller/*`）供组员A 前端直接调用。

---

## 状态机

**商品**

```
DRAFT ──发布──▶ ON_SALE ──冻结──▶ FROZEN ──成交──▶ SOLD
ON_SALE ──下架──▶ OFF_SHELF ──重新上架──▶ ON_SALE
FROZEN ──交易失败──▶ ON_SALE（自动恢复）
```

**意向 / 交易**

```
PENDING ──被选中──▶ SELECTED ──成交──▶ COMPLETED
PENDING/SELECTED ──取消/失败/过期──▶ CANCELLED / EXPIRED
offline_trade: PENDING ──finish──▶ SUCCESS / FAILED
```

> 成交时 finish_trade 在一个事务里完成三件事：商品置 SOLD、选中意向置 COMPLETED、其余 PENDING 意向批量 CANCELLED——要么全成功，要么全回滚。

---

## 自测验收（13 项全部通过）

```
✅ 0.  登录 admin/admin123
✅ 1.  创建草稿商品
✅ 2.  发布成功
✅ 3.  前台可见 ON_SALE
✅ 4.  禁止第二件在售 → 45000 Another product is active
✅ 5.  两个买家意向提交成功
✅ 6.  口令码查询返回商品与意向状态
✅ 7.  冻结选中意向 → 商品 FROZEN + 生成交易
✅ 8.  冻结期间禁止新意向 → Product is not available
✅ 9.  标记失败 → 商品自动恢复 ON_SALE
✅ 10. 再次冻结并成交 → 意向 COMPLETED / 其余 CANCELLED
✅ 11. 修改密码成功
✅ 12. 旧密码被拒绝
✅ 13. 新密码可登录并可改回
```

![e2e 自测结果](demo-evidence/api_e2e_result.png)

图 4：e2e_final2.py 自测输出 —— 13 项验收全部通过（终端实拍）