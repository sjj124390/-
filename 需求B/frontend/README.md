# 第 10 组 · 需求 B 前端

前端与联调负责人为孙俊杰。页面使用原生 HTML、CSS 和 JavaScript。Spring Boot 会把页面和接口打包到同一个 JAR，启动后从 [购物系统](http://127.0.0.1:8080/) 进入，详见 [需求 B 启动说明](../README.md)。

买家可浏览、按二级分类和关键词搜索商品，查看商品多图，注册登录后使用收藏、购物车、订单和个人资料。商家登录后可处理订单、发布和维护商品、调整分类、查看客户购买历史及修改密码。

## 数据来源

- 「后端联调」是默认模式。接口与页面使用同源 `/api`，买家和商家使用各自的 JWT，切换身份时保留本页的登录状态。刷新页面后要重新登录，订单和购物车由数据库保存。
- 「本地演示」使用浏览器的本地存储，可演示收藏、加购、转收藏、下单和订单状态变化。新开无痕窗口可从演示初始数据开始。
- 单独启动静态服务时，在前端目录执行 `python -m http.server 5500 --bind 127.0.0.1`，再打开 [拆分运行的前端](http://127.0.0.1:5500/?api=http%3A%2F%2F127.0.0.1%3A8080%2Fapi)。查询参数 `api` 用于指定后端的 API 前缀。

## 需求 B 页面对接

| 页面操作 | 方法及路径（相对于 `/api`） |
| --- | --- |
| 收藏列表、收藏、取消收藏 | `GET /customers/me/favorites`，`PUT/DELETE /customers/me/favorites/{productId}` |
| 查看购物车、加购 | `GET/POST /customers/me/cart/items` |
| 保存数量、移除购物车商品 | `PUT/DELETE /customers/me/cart/items/{productId}` |
| 所选商品转收藏 | `POST /customers/me/cart/to-favorites`，提交 `{ productIds }` |
| 购物车批量下单 | `POST /customers/me/orders`，提交 `{ productIds }` |
| 买家订单、商家订单 | `GET /customers/me/orders`，`GET /admin/orders` |
| 商家推进订单 | `PUT /admin/orders/{id}/status`，提交 `{ status }` |
| 买家或商家取消订单 | `POST /customers/me/orders/{id}/cancel`，`POST /admin/orders/{id}/cancel` |

`api.js` 统一处理 `{ code, message, data }`、JWT 请求头和错误提示。购物车根据商品状态和库存判断能否结算，订单按钮根据服务端的 `allowedActions` 渲染。金额、库存、状态和权限由后端校验。完整参数见 [后端接口说明](../backend/接口说明文档.md)。
