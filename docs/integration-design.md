# 系统集成设计与验收报告

> 负责：朱江（系统集成） ｜ 阶段：第 2 次阶段性评审
> 成果物：可运行项目（一键启动脚本 + 自动化验收脚本）、集成交付说明

## 1. 集成目标

把组员各自交付的三块内容拼成一个**能真正跑起来**的系统：

| 来源 | 内容 | 负责人 |
| --- | --- | --- |
| 前端静态页（`frontend/`） | 买家前台 3 页 + 卖家后台 4 页 | 组员 A |
| 后端服务（`backend/`） | Spring Boot + JdbcTemplate + 存储过程 | 组员 C |
| 数据库（`database/`、`backend/src/main/resources/sql/`） | 4 张表 + 4 个存储过程 | 组员 C |

集成的痛点不在"能不能启动"，而在**前后端接口契约完全不一致**——前端按一套约定开发、后端按另一套资源模型实现，两边从未对接过。

## 2. 契约差异清单

| 能力 | 前端调用（原设计） | 后端实际接口 | 差异 |
| --- | --- | --- | --- |
| 商品详情 | `GET /product/detail?id=` | `GET /products/current` | 路径不同，且后端只有"当前商品"没有按 id 查询 |
| 商品列表 | `GET /product/list` | `GET /admin/products`（全量历史） | 后端无"在售 + 历史"合并视图 |
| 发布商品 | `POST /product/add`（一步上架） | `POST /admin/products`（建草稿）+ `POST /admin/products/{id}/publish` | 后端是两步状态机 |
| 冻结 / 解冻 | `POST /product/freeze?id=` / `restore?id=` | `POST /admin/products/{id}/withdraw`、`publish`、`freeze`(需 intentId) | 后端冻结是"选中意向生成交易"，不是手动开关 |
| 提交购买 | `POST /order/submit` | `POST /intents` | 字段名不同：`buyerPhone→contact`、`remark→note`；返回 `rawCode` |
| 订单列表 | `GET /order/list` | `GET /admin/trades/pending` + `GET /admin/products/{id}/intents` | 后端按商品查意向，无全局订单视图 |
| 标记交易结果 | `POST /order/updateStatus` | `POST /admin/trades/{id}/finish` | 需先有交易 id，且要传 productId |
| 登录 / 改密 | `POST /seller/login`、`POST /seller/updatePassword` | `POST /admin/login`、`PUT /admin/password` | 路径、方法都不同，改密需 confirmPassword |
| 状态表示 | 数字 `0/1/2` | 枚举 `ON_SALE/FROZEN/SOLD/OFF_SHELF/DRAFT` | 需要双向映射 |

## 3. 集成方案

**只加一层适配，不动两端。** 后端代码（组员 C）与页面代码（组员 A）保持原样，全部差异收敛到 `frontend/js/api.js` 一个文件：

```
页面 (7 个 html + 对应 js)
        │  只认 window.Api.xxx()
        ▼
frontend/js/api.js  ← 唯一适配点（路径重写 / 字段改名 / 状态映射 / 业务编排）
        │  fetch
        ▼
backend (Spring Boot 8080) → MySQL 存储过程
```

这样做的原因：页面已按 16 项验收标准逐项验证过，后端已验证过存储过程的事务与锁顺序；改两端任何一侧都会破坏对方的既有验证，而适配层是纯新增、可单点回滚的。

### 3.1 状态映射

| 后端枚举 | 页面状态 | 说明 |
| --- | --- | --- |
| `ON_SALE` | 0（在售） | 可提交购买意向 |
| `FROZEN` | 1（冻结） | 已有买家，等待线下交易 |
| `OFF_SHELF` | 1（冻结） | 卖家手动下架，后台可恢复上架 |
| `SOLD` | 2（已售） | 进入历史商品 |
| `DRAFT` | — | 未发布，不进入后台列表 |

| 意向枚举 | 订单状态 | 说明 |
| --- | --- | --- |
| `PENDING` / `SELECTED` | 0（待交易） | 可标记成交结果 |
| `COMPLETED` | 1（交易成功） | — |
| `CANCELLED` / `EXPIRED` | 2（交易失败） | — |

### 3.2 业务规则补齐

后端状态机比前端假设更严格，集成时在适配层补齐了三处编排：

1. **下单即冻结**（基线需求 5「交易前商品要冻结」）
   后端 `POST /intents` 只写意向、不锁商品；适配层在下单成功后立即调用 `freeze(productId, intentId)`，使商品进入 `FROZEN` 并生成待处理交易。冻结失败不影响下单结果，由卖家在后台处理。
2. **成交即补货**（基线需求 1「卖掉一个再制作一个」）
   后端 `finish_trade` 成交后只把商品置 `SOLD`，不再生成新商品。适配层在标记成功后自动用原商品信息创建草稿并上架，实现"卖掉一个再制作一个"。
3. **手动冻结 / 解除冻结**
   `手动冻结 → withdraw（下架）`；`解除冻结 → 有未完成交易则标记失败恢复在售，已下架则重新 publish`。

### 3.3 体验与健壮性修正

| 问题 | 处理 |
| --- | --- |
| 存储过程 `SIGNAL` 的英文原文直接弹给用户（如 `Another product is active`） | 适配层做错误文案映射表，统一输出中文提示 |
| 发布商品"先建草稿再上架"，冲突时残留 DRAFT 脏数据 | 发布前先查在售状态，冲突直接给出中文提示，不再落草稿 |
| `GET /products/current` 无商品时返回业务码 1001 | 视为空态而非错误，前台展示"暂无可展示商品" |
| 后端无会话令牌 | 登录成功后本地生成 token 供页面登录守卫使用，401 时清除并跳转登录页 |
| 空图片 | 后端 `image_url` 非空约束，前端未填时落本地占位图 `img/product-default.svg` |

## 4. 集成过程中发现并修复的缺陷

| 编号 | 文件 | 缺陷 | 修复 |
| --- | --- | --- | --- |
| I-1 | `frontend/js/api.js` | `getProductDetail` 未把 id 传给后端，详情页永远取不到指定商品 | 传入 id；无 id 时取当前商品，兼容导航"商品详情"无参入口 |
| I-2 | `frontend/js/purchase.js` | 下单成功跳回 `index.html` 丢失 id | 回跳携带 `?id=` |
| I-3 | `frontend/js/api.js` | 后端 401 只清登录态不跳转 | 401 时清除并跳转 `login.html` |
| I-4 | `frontend/js/api.js` | 约束冲突时英文 SP 报错直达用户 | 错误文案映射表 |
| I-5 | `frontend/js/api.js` | 发布失败残留 DRAFT 草稿 | 发布前前置校验 |
| I-6 | `scripts/*.ps1` | Windows PowerShell 5.1 以 ANSI 读取 UTF-8 脚本，中文乱码并导致语法错误 | 脚本统一存为 UTF-8 with BOM |

## 5. 验收结果

### 5.1 自动化验收

```
node tests\integration-e2e.mjs
→ 通过 36 项，失败 0 项
```

覆盖用例：在售/冻结/已售/手动下架状态映射、按 id 查询、列表合并排序、错误密码、登录成功、唯一在售约束（含中文文案）、意向排队、订单列表字段映射、标记成交、成交自动补货、手动下架与意向作废、口令码查询与失效提示、原密码校验、登录态存取。

### 5.2 页面验收

买家前台（首页/详情/购买）、卖家后台（登录/商品管理/意向订单/修改密码）7 个页面在真实后端下逐页走通，覆盖 `frontend/acceptance.md` 的 16 项检查。

### 5.3 集成后完整业务闭环

```
发布商品 → 上架（ON_SALE） → 买家下单 → 自动冻结（FROZEN）+ 生成待处理交易
        → 卖家标记成功 → SOLD + 自动补货新同款上架
        → 卖家标记失败 → 恢复 ON_SALE，买家可重新下单
```

## 6. 迭代 2 适配更新（挑战 A 评审前）

集成基线之后，上游组员推进了迭代 2：后端新增 `FacadeController`（后端适配前端原契约，页面走 `Api.*` 时不经过它）、`withdraw` 升级为「下架 + 排队意向作废」的同一事务、新增口令码查询 `POST /intents/lookup` 与下架预览接口；前端新增「手动下架」按钮与「口令码查询页」（`query.html`），状态表增加「手动下架(3)」「意向失效(3)」。适配层同步做了四项更新：

| 变化 | 适配层处理 |
| --- | --- |
| 业务模型由「下单即冻结」演进为「意向排队制」 | 提交意向不再自动冻结商品；冻结移到卖家选定意向成交时（先冻结再结束交易），「交易前商品冻结」的基线需求仍被满足 |
| 新增「手动下架」（下架并作废排队意向） | 新增 `Api.offlineProduct(id)`，映射 `POST /admin/products/{id}/withdraw` |
| 新增口令码查询页 | 新增 `Api.queryIntent({queryCode})`，映射 `POST /intents/lookup`；进度映射 待交易 0 / 成功 1；失效由后端 410/404 抛错，页面统一提示「口令码已失效」 |
| 状态语义细化 | 商品 `OFF_SHELF` 由 1（冻结）改为 3（手动下架）；订单 `CANCELLED` 结合商品状态细分为 2（交易失败）或 3（意向失效） |

**语义收敛说明**：后端状态机中「手动冻结」与「手动下架」同为 `withdraw` 一个动作，前端两个按钮的效果一致，展示统一为「手动下架(3)」；如需严格区分，需要后端为「无交易的冻结」引入独立状态，列入后续迭代。

**缺陷修复**：上游 `admin-products.js` 存在编辑残留的语法错误（重复的 `load()` 代码块），导致商品管理页整页无法解析，已修复。

**验收口径**：组员 C 的 `backend/e2e_test.py` 面向其本机环境（MySQL 5.7 路径与口令硬编码），本机集成验收以 `tests/integration-e2e.mjs`（36 项，含迭代 2 全链路）为准。

## 7. 已知限制与后续迭代

1. **无会话鉴权**：后端未提供登录令牌，后台接口无鉴权，当前 token 仅用于前端页面守卫；接入挑战需求 A（客户注册登录）时需补 JWT/会话。
2. **口令码查询未接入页面**：后端已实现 `POST /intents/lookup`（买家凭口令码查进度），前端暂无入口。
3. **挑战需求 A 尚未实现**：多商品、二级分类、库存、浏览/搜索、多图与富媒体、客户注册与历史订单、商家查看客户信息，均属于下一迭代范围；当前前端列表页与后端"单商品唯一在售"约束需要同步改造。
4. **演示数据依赖脚本**：`scripts/seed-demo.ps1` 用于重置演示商品；真实使用由卖家在后台发布。
