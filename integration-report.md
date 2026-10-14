# 前后端联调报告（组员 D）

## 一、联调背景

组员 A（前端）与组员 B（后端）并行开发时使用了**两套不同的接口契约**，直接对接时全部 10 个前端接口无一可用：

| # | 前端契约（A） | 后端实现（B） | 差异 |
|---|---|---|---|
| 1 | `GET /api/product/detail?id=` | `GET /api/products/current` | 路径不同 |
| 2 | `POST /api/order/submit` `{productId,buyerName,buyerPhone,remark}` | `POST /api/intents` `{productId,buyerName,contact,note}` | 路径 + 字段名 |
| 3 | `POST /api/seller/login` 返回 `token` | `POST /api/admin/login` 仅返回 Seller（无 token） | 路径 + 返回体 |
| 4 | `GET /api/product/list` 状态 `0/1/2` | `GET /api/admin/products` 状态 `DRAFT/ON_SALE/FROZEN/SOLD/OFF_SHELF` | 路径 + 状态枚举 |
| 5 | `POST /api/product/add` 一步发布 | `POST /api/admin/products` 建草稿 + `POST /{id}/publish` 发布 | 流程拆分 |
| 6 | `POST /api/product/freeze?id=` 手动冻结 | `POST /api/admin/products/{id}/freeze` 需带 `intentId`（选中买家才冻结） | 语义不同 |
| 7 | `POST /api/product/restore?id=` | 无独立接口（`publish_product` SP 允许 OFF_SHELF→ON_SALE） | 缺失 |
| 8 | `GET /api/order/list` 平铺订单流 | `GET /api/admin/products/{id}/intents` 按商品查意向 | 聚合方式 |
| 9 | `POST /api/order/updateStatus` `{orderId,status}` 一步标记 | `freeze_product` + `finish_trade` 两段式存储过程 | 流程拆分 |
| 10 | `POST /api/seller/updatePassword` | `PUT /api/admin/password` | 方法 + 路径 |

## 二、联调方案：后端新增适配层

为**同时保留 A 的前端代码与 B 的后端代码零改动**，在后端新增单一文件
`backend/src/main/java/com/shop/online/controller/FacadeController.java`（约 300 行），
以前端契约为准做协议适配：

- **字段映射**：`buyerPhone→contact`、`remark→note`、`imageUrl↔image_url`、`createdAt→createTime`
- **商品状态映射**：`ON_SALE→0(在售)`、`FROZEN→1(冻结)`、`OFF_SHELF→1(冻结)`、`SOLD→2(已售)`、`DRAFT` 不暴露给前端
- **订单状态映射**：`PENDING/SELECTED→0(待交易)`、`COMPLETED→1(成功)`、`CANCELLED/EXPIRED→2(失败)`
- **流程编排**：
  - `product/add` = `createDraft` + `publish_product`（发布失败自动清理残留草稿）
  - `order/updateStatus(成功)` = 意向 PENDING 时先 `freeze_product` 再 `finish_trade`；已 SELECTED 则直接 `finish_trade`
  - `order/updateStatus(失败)` = PENDING 意向条件取消（`UPDATE ... WHERE status='PENDING'`）；SELECTED 走 `finish_trade(失败)`，商品自动恢复在售
  - `product/freeze` = `withdraw`（在售→下架，前端显示「冻结」）；`product/restore` = `publish_product`（下架→在售）
- **登录 token**：B 的 MVP 后端无鉴权，适配层登录成功补发 UUID token 供前端登录态使用
- **中文错误文案**：适配层对常见冲突做预检（重复发布、重复标记、冻结锁定等），避免把存储过程英文报错直接弹给买家

> 注意：组员 C 的 4 个存储过程内部自行管理事务，适配层所有方法**不得加 `@Transactional`**。

## 三、联调中发现并修复的问题

| # | 问题 | 定位 | 修复 |
|---|---|---|---|
| 1 | 后端在本机 JDK 8 下编译失败 | `PasscodeUtil` 使用了 Java 11+ 的 `String.isBlank()` | 改为等价的 `trim().isEmpty()`（一行） |
| 2 | `GET /api/order/list` 返回 500 | MySQL Connector/J 8 默认把 `DATETIME` 映射为 `java.time.LocalDateTime`，适配层强转 `java.sql.Timestamp` 抛 `ClassCastException` | 兼容两种类型后再格式化 |
| 3 | 卖家密码配置与环境不符 | `application.yml` 默认 `root/root`，本机 MySQL 为其他密码 | 更新配置 |

## 四、联调结论

- 前端 10 个接口全部打通，买家链路（浏览→下单→口令码）与卖家链路（登录→商品→订单→改密）端到端可用
- A 的前端代码、B 的后端业务代码、C 的数据库脚本**均未改动业务逻辑**，集成工作集中在 D 新增的适配层
- 已知限制（MVP 可接受）：
  - 后台接口无真正鉴权（token 仅前端登录态），B 预留了拦截器升级位
  - 「手动冻结」语义映射为「下架」，会同时取消该商品的未处理意向
  - 交易成功后「自动上架同款新品」未实现（A 的 README 已列为待完善项）
