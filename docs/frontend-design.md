# 在线购物系统前端技术方案（朱江负责）

## 1. 范围与角色约定
实现买家前台与卖家后台共 6 个页面，以及页面与后端接口的交互对接。
- 前台：商品详情页、购买信息填写页
- 后台：卖家登录页、商品管理页、意向订单页、密码修改页
不实现：在线支付、购物车、多卖家、用户注册。
角色边界：访客无需登录即可浏览商品、提交购买意向；卖家需登录后台进行商品与订单管理。
接口基址 `http://localhost:8080/api`，由后端提供，跨域已由后端配置完成。
交互约定（组员 B 提供）：所有接口返回 `{ code, message, data }`，`code === 200` 表示成功；其他 code 由前端直接把 `message` 弹窗提示，前端不自行编造错误文案。

## 2. 技术与运行
- 页面结构：HTML5
- 样式开发：CSS3（CSS 变量 + 响应式，无 UI 框架）
- 交互逻辑：原生 JavaScript（无框架、无构建依赖）
- 优势：轻量无依赖，适合 MVP 快速开发，无需构建环境
- 运行方式：本地静态服务器打开（例如 `python -m http.server 5500`），浏览器访问 `http://localhost:5500/index.html`

SQL 文件见 [database/schema.sql](../database/schema.sql)。

## 3. 页面结构
```
frontend/
├── index.html            商品详情页（前台）
├── purchase.html         购买信息填写页（前台）
├── login.html            卖家登录页（后台）
├── admin-products.html   商品管理页（后台）
├── admin-orders.html     意向订单页（后台）
├── admin-password.html   密码修改页（后台）
├── acceptance.md         前端验收清单
├── css/
│   └── style.css         全局样式
└── js/
    ├── config.js         接口基址配置
    ├── api.js            统一请求封装 + 登录态管理
    ├── common.js         Toast、确认弹窗、登录守卫、侧边栏、格式化、状态常量
    ├── product-detail.js
    ├── purchase.js
    ├── login.js
    ├── admin-products.js
    ├── admin-orders.js
    └── admin-password.js
```

## 4. 页面交互与状态
- 商品详情页：`status` 为 0 在售时「立即购买」可点击；1 冻结 / 2 已售时按钮置灰并显示对应文案。
- 购买信息填写页：姓名与联系电话为空时「提交购买」置灰；提交成功后提示「购买意向已提交，卖家将联系您」并返回商品详情页。
- 卖家登录页：校验通过保存登录态并跳转商品管理页；校验失败显示后端 `message`。
- 商品管理页：展示当前在售 / 冻结商品卡片与历史商品列表；「手动冻结」「解除冻结」「发布新商品」均带二次确认或表单校验。
- 意向订单页：仅「待交易」订单可「标记成功 / 标记失败」，操作前二次确认，完成后刷新列表。
- 密码修改页：校验原密码非空、新密码不少于 6 位、两次新密码一致；成功后清除登录态并跳转登录页。
- 登录守卫：后台三个页面进入前校验登录态，未登录重定向到登录页；接口返回 401 时清除登录态并跳转。

## 5. 页面与接口对接
| 页面 | 调用接口 | 关键字段 |
| --- | --- | --- |
| 商品详情页 | GET /api/product/detail | data.status：0 在售（按钮可点）/ 1 冻结 / 2 已售（按钮置灰） |
| 购买信息填写页 | POST /api/order/submit | body：{ productId, buyerName, buyerPhone, remark } |
| 卖家登录页 | POST /api/seller/login | body：{ username, password } |
| 商品管理页 | GET /api/product/list、POST /api/product/add、POST /api/product/freeze?id=、POST /api/product/restore?id= | add body：{ name, description, imageUrl, price } |
| 意向订单页 | GET /api/order/list、POST /api/order/updateStatus | body：{ orderId, status }，status：1 成功 / 2 失败 |
| 密码修改页 | POST /api/seller/updatePassword | body：{ username, oldPassword, newPassword, confirmPassword } |

## 6. 复用与封装
- `config.js`：集中管理接口基址，各页面无需重复拼接。
- `api.js`：统一封装 `fetch`，自动携带 `Authorization` 头、解析 `{ code, message, data }`、抛出携带 `message` 的业务异常，并在 401 时清理登录态。
- `common.js`：提供 Toast、确认弹窗、登录守卫、后台侧边栏、价格与时间格式化、状态常量等公共能力，避免页面重复实现。

## 7. 验收与限制
验收步骤见 [frontend/acceptance.md](../frontend/acceptance.md)。
当前前端已按接口契约完成全部页面结构与交互逻辑；尚未与真实后端完成联调，后端未启动时页面显示网络异常提示，属预期表现。
上线前仍需完成后端接口实现，并核对各接口返回字段与前端归一化逻辑是否一致。
