# 在线购物系统（单商品 · 线下交易 MVP）

《软件工程实践》第 10 组项目。极简电商闭环：**只卖一个商品，卖掉一个再制作一个**；买家无需注册，看中后填写联系信息提交购买意向，商品随即冻结，卖家线下见面一手交钱一手交货，回后台标记成交成功或失败。

- 组长：孙俊杰 ｜ 组员：朱江、杨接乔、陆贻辉
- 本阶段交付：可运行系统（前端 + 后端 + 数据库）与系统集成说明

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | 原生 HTML + CSS + JavaScript（无框架，静态托管） |
| 后端 | Java 17 · Spring Boot 2.7 · JdbcTemplate · BCrypt |
| 数据库 | MySQL 8.0 · 4 张表 + 4 个存储过程（事务与锁顺序在库内保证） |

## 目录结构

```
├── backend/                 Spring Boot 服务（8080）
│   ├── src/main/java/com/shop/online/   controller / service / dto / entity / config
│   ├── src/main/resources/sql/          建库脚本与存储过程（schema-memberC.sql）
│   └── pom.xml
├── frontend/                买家前台 + 卖家后台静态页（5500）
│   ├── js/api.js            前后端接口适配层（系统集成唯一适配点）
│   └── img/                 本地商品配图与占位图
├── database/                数据库设计交付物（schema.sql、acceptance.md）
├── docs/                    设计文档（数据库、前端、系统集成）
├── scripts/                 一键启动 / 停止 / 演示数据脚本
├── tests/                   集成自动化验收脚本
└── logs/                    运行日志（不入库）
```

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | `java -version` 可验证 |
| Maven | 3.9.x | 仅构建时需要，可放 `%USERPROFILE%\tools\apache-maven-3.9.9` 或加入 PATH |
| MySQL | 8.0 | 默认账号 `root`，默认密码 `123456`（可用参数覆盖） |
| Python | 3.x | 仅用于起静态服务器，也可用 IDEA / VS Code 内置服务器替代 |

## 快速开始（三条命令）

```powershell
# 1. 启动数据库结构 + 后端 8080 + 前端 5500
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1

# 2. 写入一件演示商品（已有在售商品时自动跳过）
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1

# 3. 运行集成自动化验收
node tests\integration-e2e.mjs
```

访问地址：

- 买家前台：http://127.0.0.1:5500/index.html
- 卖家后台：http://127.0.0.1:5500/login.html （默认账号 `admin` / `admin123`）
- 后端接口：http://localhost:8080/api

停止服务：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
```

常用参数：

```powershell
# 重建数据库（清空数据，会重建表与存储过程）
scripts\start-all.ps1 -ResetDb

# 指定数据库账号密码 / 端口
scripts\start-all.ps1 -DbUser root -DbPassword 123456 -BackendPort 8080 -FrontendPort 5500

# 强制重新构建后端 jar
scripts\start-all.ps1 -Build
```

## 手动构建与部署

### 1. 构建

```powershell
cd backend
mvn -B -DskipTests package
# 产物：backend\target\online-shopping-backend-1.0.0.jar
```

### 2. 初始化数据库

```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source backend/src/main/resources/sql/schema-memberC.sql"
```

脚本会创建库 `single_item_shop`、4 张表（`seller` / `product` / `purchase_intent` / `offline_trade`）与 4 个存储过程（`publish_product` / `submit_intent` / `freeze_product` / `finish_trade`）。

### 3. 启动后端

```powershell
java -jar backend\target\online-shopping-backend-1.0.0.jar --spring.datasource.password=123456
```

首次启动会自动初始化唯一卖家账号 `admin / admin123`（BCrypt 存储），日志出现"在线购物系统后端启动成功"即就绪。

### 4. 启动前端

```powershell
cd frontend
python -m http.server 5500 --bind 127.0.0.1
```

前端通过 `frontend/js/config.js` 中的 `API_BASE_URL` 访问后端，跨域已由后端 `CorsConfig` 放行。

## 自动化验收

`tests/integration-e2e.mjs` 在 Node 中加载真实的前端适配层 `frontend/js/api.js`，对接真实后端逐项断言，覆盖：

商品详情与状态映射、按 id 查询、列表合并排序、错误密码提示、登录成功、唯一在售约束（中文文案）、下单自动冻结、订单列表字段映射、标记成交、成交自动补货、原密码校验、登录态存取。

```powershell
node tests\integration-e2e.mjs
# 通过 27 项，失败 0 项
```

## 成果物索引（对照《系统验收标准》五类）

| 类别 | 成果物 | 位置 |
| --- | --- | --- |
| 需求 | 需求规格说明书、MVP 需求清单与业务规则 | 组 Wiki（朱江，第 1 次评审成果物） |
| 代码 | 全量源代码（前端 + 后端 + 数据库） | 本仓库 `frontend/` `backend/` `database/` |
| 构建 | 构建配置与手册 | `backend/pom.xml`、本文"构建"小节 |
| 部署 | 部署脚本与手册 | `scripts/start-all.ps1`、`scripts/stop-all.ps1`、本文"部署"小节 |
| 运行 | 测试用例清单、自动化脚本、运行说明 | `frontend/acceptance.md`、`tests/integration-e2e.mjs` |

## 系统集成说明

前后端接口契约完全不一致（路径、方法、字段命名、状态表示、业务编排都不同）。集成采用**单点适配**方案：后端与页面代码均不改动，全部差异收敛到 `frontend/js/api.js`。

详见 [docs/integration-design.md](docs/integration-design.md)（契约差异清单、状态映射、业务规则补齐、缺陷修复记录、验收结果）。

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 页面提示"网络异常，请确认后端服务（8080 端口）已启动" | 后端未启动或端口被占用，执行 `scripts\start-all.ps1` |
| 登录提示"卖家账号尚未初始化" | 数据库被清空但后端未重启，先 `stop-all.ps1` 再 `start-all.ps1 -ResetDb` |
| 提示"已有在售或冻结中的商品" | 单商品规则：同一时间只允许一件在售，先成交或下架当前商品 |
| 构建下载依赖很慢 | 在 `~/.m2/settings.xml` 配置阿里云镜像后重试 |
| 启动脚本中文乱码或语法报错 | 脚本需为 UTF-8 with BOM 编码（PowerShell 5.1 按 ANSI 解析无 BOM 文件） |
