# 在线购物系统 · 前端（成员 A / 朱江负责）

原生 HTML + CSS + JavaScript 实现，无构建依赖。买家可在前台查看商品并提交购买意向，卖家在后台管理商品、订单与登录密码。

## 一、目录结构

```
frontend/
├── index.html            # 商品详情页（买家）
├── purchase.html         # 购买信息填写页（买家）
├── login.html            # 卖家登录页（后台入口）
├── admin-products.html   # 商品管理页（后台）
├── admin-orders.html     # 意向订单页（后台）
├── admin-password.html   # 密码修改页（后台）
├── acceptance.md         # 16 项验收清单（已全部通过）
├── css/
│   └── style.css         # 全部页面统一视觉
└── js/
    ├── config.js         # 接口基址配置
    ├── api.js            # fetch 封装 + 登录态（含 401 跳转）
    ├── common.js         # Toast、确认弹窗、登录守卫、侧边栏、状态常量
    ├── product-detail.js
    ├── purchase.js
    ├── login.js
    ├── admin-products.js
    ├── admin-orders.js
    └── admin-password.js
```

## 二、快速开始

### 1. 启动后端（组员 B 负责）

后端监听 `http://localhost:8080`，需要 CORS 放行 `http://127.0.0.1:5500` 与 `http://localhost:5500`。

### 2. 启动前端静态服务器

任选一种：

```powershell
# 方式 A：Python 3 自带（推荐）
cd "C:\Users\Administrator\Desktop\软件工程实践\frontend"
python -m http.server 5500 --bind 127.0.0.1
```

```powershell
# 方式 B：VS Code Live Server 扩展
# 在 frontend 目录右键 → Open with Live Server
```

### 3. 打开浏览器

| 角色 | 地址 |
|---|---|
| 买家 | http://127.0.0.1:5500/index.html |
| 卖家后台登录 | http://127.0.0.1:5500/login.html |

> **不要直接双击 HTML 打开**：`file://` 协议下 `fetch` 无法跨域请求后端，会一律报「网络异常」。必须通过 `http://` 访问。

## 三、接口约定

- **基址**：`http://localhost:8080/api`
- **统一返回**：`{ code, message, data }`，`code === 200` 表示成功；其余视为失败，**前端不自行编造错误文案**，直接把后端 `message` 弹给用户。
- **鉴权**：除登录与前台两个接口外，卖家后台接口需要 `Authorization: Bearer <token>` 头（`token` 登录后存入 `localStorage.seller_token`）。
- **超时/失败**：`fetch` 失败或非 200 时，前端弹出后端 `message`；若 `code === 401` 且原本处于已登录态，会自动清除登录态并跳转登录页。

### 接口清单

| 方法 | 路径 | 入参 | 用途 |
|---|---|---|---|
| GET  | `/product/detail` | `id`（可选；不传取当前商品） | 商品详情 |
| POST | `/order/submit` | `{ productId, buyerName, buyerPhone, remark }` | 提交购买意向 |
| POST | `/seller/login` | `{ username, password }` | 卖家登录，返回 `token` |
| GET  | `/product/list` | — | 商品列表（前端按 `status` 0/1 为当前、2 为历史分组） |
| POST | `/product/add` | `{ name, price, imageUrl, description }` | 发布新商品 |
| POST | `/product/freeze` | `?id=` | 手动冻结 |
| POST | `/product/restore` | `?id=` | 解除冻结 |
| GET  | `/order/list` | — | 订单列表（前端按 `status` 0 渲染操作按钮、1/2 渲染只读徽标） |
| POST | `/order/updateStatus` | `{ orderId, status }` (`status`: 1 成功 / 2 失败) | 标记交易结果 |
| POST | `/seller/updatePassword` | `{ username, oldPassword, newPassword }` | 修改密码 |

商品 / 订单状态码：

- 商品 `status`：`0` 在售 / `1` 冻结 / `2` 已售
- 订单 `status`：`0` 待交易 / `1` 成功 / `2` 失败

## 四、字段兼容

后端字段命名不同时，前端会做归一化映射，降低前后端不一致的踩坑成本：

- `id` ↔ `productId` ↔ `orderId`
- `imageUrl` ↔ `image_url` ↔ `image`
- `buyerPhone` ↔ `phone` ↔ `contact`
- `description` ↔ `desc`
- `createTime` ↔ `createdAt` ↔ `created_at` ↔ `submitTime`
- 列表响应兼容直接是数组，也兼容 `{ list | records | data | items: [...] }` 包装

## 五、浏览器/网络环境提示

如果本机 `github.com` 经常 443 超时，可在 `C:\Windows\System32\drivers\etc\hosts` 末尾追加：

```
140.82.112.3 github.com
```

刷新 DNS：`ipconfig /flushdns`。该 IP 失效时可改用 `140.82.113.3 / 140.82.114.3 / 20.27.177.113`（同网段备用 IP）。

## 六、开发与验收

- 16 项验收步骤见 [`acceptance.md`](./acceptance.md)。
- 修改任一 `.html` / `.js` 后，浏览器需 **Ctrl+F5 强制刷新** 才能看到最新版本（`api.js` / `common.js` 改动尤其需要）。
- 调试时建议在浏览器 DevTools 的 **Application → Local Storage** 检查 `seller_token` 与 `seller_name`。
- 若前端要本地独立联调，可临时把 `js/config.js` 的 `BASE_URL` 改成本地 mock 后端的地址；后端就绪后改回 `http://localhost:8080/api`。

## 七、待完善（不阻塞当前验收）

- 标记订单成功时业务上需要「自动下架原商品并上架同款新品」（前端文案已就位），需后端在 `order/updateStatus` 接口内联动实现。
- 后端若返回「查询口令码」字段（`data.code` / `data.queryCode` / `data.intentCode`），购买成功页会直接展示给买家；当前接口未返回时降级为普通 toast 提示并返回详情页。
- 移动端布局已做基础适配，深色模式 / 国际化未做。
