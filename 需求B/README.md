# 需求 B · 在线购物系统

软件工程实践第 10 组。本目录包含需求 B 的前端、Spring Boot 后端、MySQL 建库和升级脚本、联调验收脚本及课堂汇报。需求 A 的多商品、二级分类和买家账户是本阶段的基础。需求 B 在此基础上加入收藏、购物车批量下单、购物车转收藏及订单五状态流转。

## 在 Windows 上启动

需要 JDK 17 或更高版本、Maven 3.9 和已启动的 MySQL 8.0。下列命令从仓库里的 `需求B` 目录执行。

1. 安装数据库。已有 `shop_req_b` 库且已执行过原建库脚本时，只执行第二条一次。已经执行过 `integration-b.sql` 的库可以跳过这一步。

   ```powershell
   mysql -u root -p --default-character-set=utf8mb4 -e "source database/schema-b.sql"
   mysql -u root -p --default-character-set=utf8mb4 -e "source database/integration-b.sql"
   ```

2. 构建并运行。将 `DB_PASSWORD` 换成实际密码。默认连接本机 MySQL 的 `3306` 端口，可用 `DB_HOST`、`DB_PORT` 和 `DB_USER` 覆盖。

   ```powershell
   mvn -f backend/pom.xml -B package
   $env:DB_PASSWORD = "你的 MySQL 密码"
   java -jar backend/target/online-shopping-backend-b-1.0.0.jar
   ```

3. 首次启动会创建演示账号。要使用课堂演示商品，在第二个终端进入 `需求B` 并执行下列命令。也可以用商家账号在页面发布自己的商品。

   ```powershell
   mysql -u root -p --default-character-set=utf8mb4 -e "source database/seed-demo-b.sql"
   ```

4. 打开 [购物系统](http://127.0.0.1:8080/)，数据来源默认为「后端联调」。需要避开端口占用时，在 `java -jar` 命令末尾加 `--server.port=8081`，然后访问对应地址。

| 身份 | 演示账号 | 演示密码 |
| --- | --- | --- |
| 买家 | `buyer` | `123456` |
| 商家 | `admin` | `admin123` |

## 演示与验收

- 买家搜索商品，将三种商品加入购物车，修改数量后只勾选其中两项并提交；也可把剩余商品转入收藏。
- 商家在订单管理中依次确认、备货、发货、完成，买家回到「我的订单」核对状态。
- 再下单一件商品。买家在发货前取消，页面应显示「已取消」，商品库存回补。发货后可由商家在交易完成前取消。

集成验收需要 Node.js 18 或更高版本。它通过真实前端 API 适配层访问运行中的后端，并会新增专用的测试账号、商品和订单，请在联调用的数据库执行。端口有调整时，用 `SHOP_B_ORIGIN` 指定前端页面地址。

```powershell
node tests/integration-e2e.mjs
```

- [前端功能与接口](frontend/README.md)
- [后端接口说明](backend/接口说明文档.md)
- [数据库设计与 SQL 验收](database/README.md)
- [孙俊杰的前端及联调汇报](汇报材料/孙俊杰-前端汇报.md)
