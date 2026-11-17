# 第 10 组 · 需求 B 前端

前端与联调负责人：孙俊杰

## 运行

在仓库根目录执行：

```sh
python3 -m http.server 5500 --bind 127.0.0.1
```

打开 [需求 B 前端](http://127.0.0.1:5500/%E9%9C%80%E6%B1%82B/frontend/index.html)。页面使用原生 HTML、CSS 和 JavaScript，无需编译或安装依赖。

页面默认使用「本地演示」。演示商品、购物车、收藏、订单和库存变化保存在当前浏览器中。刷新会保留数据，课堂演示可使用新的无痕窗口从初始数据开始。

## 演示步骤

1. 以买家身份搜索商品，收藏一个商品，将两种商品加入购物车。
2. 在收藏页查看或取消收藏。在购物车中修改数量、勾选商品并批量提交，核对订单明细与合计。
3. 切换到商家，依次确认订单、标记备货完成、开始发货、标记交易完成。切回买家核对订单状态。
4. 再下一笔订单，体验取消。演示暂定买家只能在商家确认前取消，商家可以在发货前取消。演示下单扣减库存，取消后回补。

## 后端联调

页面可切换到「后端联调」，默认 API 地址为 `http://localhost:8080/api`，在 `api.js` 中修改。后端需允许前端所在来源，以及表中的 HTTP 方法和 `Authorization`、`Content-Type` 请求头。

下表是前端适配层目前使用的**待确认契约**。仓库现有单商品后端未实现需求 B 的这些接口，且现有商家登录不返回真实联调令牌。后端完成后，在 `api.js` 集中核对并调整路径、认证和字段映射。联调模式直接使用接口结果，服务错误会显示在页面上。

| 功能 | 方法与路径（相对于 `/api`） | 请求或响应数据 |
| --- | --- | --- |
| 登录 | `POST /customers/login`、`POST /admin/login` | 请求 `username`、`password`，响应 `{ token }` |
| 搜索商品 | `GET /products?keyword=&page=1&size=50` | 响应 `{ records: Product[] }`，当前界面展示首批结果 |
| 查看收藏 | `GET /customers/me/favorites` | 响应 `Product[]` |
| 添加/移除收藏 | `PUT /customers/me/favorites/{productId}`、`DELETE /customers/me/favorites/{productId}` | 路径使用商品 ID |
| 查看购物车 | `GET /customers/me/cart/items` | 响应购物车条目数组 |
| 添加购物车商品 | `POST /customers/me/cart/items` | 请求 `{ productId, quantity }`，数量为本次增加量 |
| 更新/移除购物车商品 | `PUT /customers/me/cart/items/{productId}`、`DELETE /customers/me/cart/items/{productId}` | 更新请求 `{ quantity }`，数量为更新后的值 |
| 勾选商品下单 | `POST /customers/me/orders` | 请求 `{ productIds }`，由后端核对购物车、价格与库存 |
| 查看订单 | `GET /customers/me/orders`、`GET /admin/orders` | 响应订单数组 |
| 推进订单状态 | `PUT /admin/orders/{id}/status` | 请求 `{ status }` |
| 取消订单 | `POST /customers/me/orders/{id}/cancel`、`POST /admin/orders/{id}/cancel` | 由后端校验身份和状态 |

接口沿用 `{ code, message, data }`，成功码为 `200`。联调期间凭证保存在页面内存，通过 `Authorization: Bearer <token>` 发送，刷新后需重新登录。

- 商品字段为 `id`、`name`、`description`、`price`、`stock`、`imageUrl`（可选）。
- 购物车条目字段为 `productId`、`name`、`price`、`stock`、`quantity`。
- 订单字段为 `id`、`buyerName`（商家端使用）、`status`、`totalAmount`、`createdAt`、`items`、`allowedActions`。订单明细显示 `name`、`price` 和 `quantity`。
- 订单状态使用 `PLACED`、`CONFIRMED`、`PREPARED`、`SHIPPED`、`COMPLETED`，取消为 `CANCELLED`。后端根据当前用户和状态返回可执行的目标状态数组 `allowedActions`，页面据此显示操作按钮。
