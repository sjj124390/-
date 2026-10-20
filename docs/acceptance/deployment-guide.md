# 安装与部署手册

第10组在线购物系统 1.0.0；2026-10-20。

## 1. 部署方式

采用本地 JAR + MySQL + Python 静态服务器方式。支持安装了相应工具的 Windows/Linux；原测试报告环境为 Windows 10。本项目尚未提供 Docker 镜像，本手册不填写不存在的镜像地址。

| 服务 | 地址/端口 |
|---|---|
| MySQL | localhost:3306；库名 single_item_shop |
| Spring Boot | http://localhost:8080 |
| 前端 | http://127.0.0.1:5500 |

先按[构建手册](build-guide.md)安装 JDK、Maven、Python 与 MySQL，准备源码及 JAR。MySQL 服务应已启动。

## 2. 初始化数据库

在专用课程测试库中执行。推荐使用带完整存储过程的 `database/schema.sql`，要求 MySQL 8.0.16+。该脚本创建 `single_item_shop`、四张业务表以及四个存储过程；不要只导入表结构。

从项目根目录启动 MySQL 客户端：

```sh
mysql -u root -p --default-character-set=utf8mb4
```

在 MySQL 客户端中执行（路径相对于启动客户端的目录）：

```sql
SOURCE database/schema.sql;
USE single_item_shop;
SHOW TABLES;
SHOW PROCEDURE STATUS WHERE Db = 'single_item_shop';
```

应看到 `seller`、`product`、`purchase_intent`、`offline_trade`，以及 `publish_product`、`submit_intent`、`freeze_product`、`finish_trade`。

`database/schema.sql` 面向空环境，重复导入会因同名对象报错。不要改用 `schema-mysql57.sql` 重置已有数据：该兼容脚本含 DROP TABLE。MySQL 5.7 的 CHECK 行为也不同，不能与 8.0 的验证结果混用。

## 3. 配置与启动后端

应用读取 `backend/src/main/resources/application.yml`。无需修改或公开数据库真实密码，推荐启动时用环境变量覆盖数据库账号。示例为 Windows PowerShell：

```powershell
# 将下方占位值替换为本机课程测试库账号，不要把真实密码提交到仓库。
$env:SPRING_DATASOURCE_USERNAME = "your_local_user"
$env:SPRING_DATASOURCE_PASSWORD = "your_local_password"
# 为本机演示生成密钥；同一数据库复用时必须保留同一密钥，否则旧口令码无法查询。
$env:PASSCODE_HMAC_KEY = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
java -jar backend/target/online-shopping-backend-1.0.0.jar
```

Linux 示例：

```sh
export SPRING_DATASOURCE_USERNAME='your_local_user'
export SPRING_DATASOURCE_PASSWORD='your_local_password'
export PASSCODE_HMAC_KEY='replace_with_a_stable_local_random_secret'
java -jar backend/target/online-shopping-backend-1.0.0.jar
```

首次启动会初始化唯一卖家，课程演示默认账号为 `admin`，默认密码为 `admin123`。若库里已有卖家，重启不会重置密码。待出现 Spring Boot 启动完成日志后进行下一步。数据库位于其他主机时同时覆盖 `SPRING_DATASOURCE_URL`。

也可直接使用原始联调 ZIP 内的 JAR：先在解压后的 `--main` 目录执行同样命令。务必通过环境变量覆盖该历史包中保存的个人数据库配置。

## 4. 启动前端

另开一个终端，从项目根目录执行：

```sh
cd frontend
python -m http.server 5500 --bind 127.0.0.1
```

访问：

- 买家商品页：`http://127.0.0.1:5500/index.html`
- 卖家登录页：`http://127.0.0.1:5500/login.html`

不要双击 HTML 文件。`frontend/js/config.js` 的接口地址应为 `http://localhost:8080/api`；端口变更时同步调整前端配置和后端 CORS 配置。

## 5. 部署验证与停止

1. 打开登录页，登录卖家账号，发布测试商品。
2. 买家页刷新后显示该商品；提交购买意向后保存口令码。
3. 后台能看到意向记录并标记结果；成交成功后旧商品进入历史。
4. 按[运行与测试手册](run-test-guide.md)检查其他用例。
5. 停止前后端终端中的进程使用 Ctrl+C；数据库数据会保留。

## 6. 已知边界

当前仅为课程本地演示版本。后台 token 只是前端登录态，服务端未实现真正的请求鉴权；不能将此写成“所有后台接口已保护”。购买自动冻结、成交自动补货等与原需求的差异见[需求对照](requirements-status.md)。本次未在当前环境重跑部署，不将上述步骤当作新的成功运行证据。
