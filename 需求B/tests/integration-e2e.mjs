import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { readFile } from "node:fs/promises";
import { runInNewContext } from "node:vm";

const origin = new URL(process.env.SHOP_B_ORIGIN || "http://127.0.0.1:8080");
const window = { location: origin };
const source = await readFile(new URL("../frontend/api.js", import.meta.url), "utf8");
runInNewContext(source, {
  window, URLSearchParams, AbortSignal,
  fetch: (url, options) => fetch(new URL(url, origin), options)
});

const { live, roundTotal } = window.ShopApi;
const suffix = Date.now().toString(36) + randomBytes(2).toString("hex");
const account = name => ({
  username: name + suffix, password: "buyer123", phone: "13800000001", defaultTradePlace: "图书馆一楼"
});
const expectStatus = (request, status) => assert.rejects(request, { status });
const group = message => console.log(`PASS ${message}`);
const array = value => Array.from(value);

const seller = await live.login({ role: "seller", username: "admin",
  password: process.env.SHOP_B_ADMIN_PASSWORD || "admin123" });
const buyer = await live.register(account("buyer"));
const other = await live.register(account("other"));
const call = (method, data = {}) => live[method]({ token: buyer, role: "buyer", ...data });
const admin = (method, data = {}) => live[method]({ token: seller, role: "seller", ...data });

await expectStatus(live.login({ role: "buyer", username: account("buyer").username, password: "wrong" }), 401);
assert.ok(await live.login({ role: "buyer", ...account("buyer") }));
await expectStatus(live.cart({}), 401);
await expectStatus(live.cart({ token: seller }), 403);
assert.equal((await call("profile")).defaultTradePlace, "图书馆一楼");
group("登录、注册、角色校验与默认交易地点");

const parent = await admin("saveCategory", { category: { name: "联调分类 " + suffix } });
const child = await admin("saveCategory", { category: { name: "配件 " + suffix, parentId: parent } });
const create = (name, stock, price, images = []) => admin("saveProduct", {
  product: { name: name + suffix, stock, price, categoryId: child, images }
});
const a = await create("联调车灯", 3, 59, ["img/product-bike.svg"]);
const b = await create("联调水壶", 1, 29.9);
const c = await create("联调尾包", 10, 13.8);
assert.ok((await live.categories()).find(item => item.id === parent).children.some(item => item.id === child));
const page = await live.products({ keyword: suffix, categoryId: parent, page: 1, size: 2 });
assert.equal(page.total, 3);
assert.equal(page.records.length, 2);
assert.equal((await live.product({ id: a.id })).images[0], "img/product-bike.svg");
group("二级分类、商品发布、分页搜索与商品详情");

await call("favorite", { id: a.id, enabled: true });
await call("favorite", { id: a.id, enabled: true });
assert.deepEqual(array((await call("favorites")).map(item => item.id)), [a.id]);
await call("favorite", { id: a.id, enabled: false });
assert.equal((await call("favorites")).length, 0);
await Promise.all([1, 1].map(quantity => call("saveCart", { id: a.id, quantity, add: true })));
assert.equal((await call("cart"))[0].quantity, 2);
await expectStatus(call("saveCart", { id: a.id, quantity: 2, add: true }), 400);
await call("saveCart", { id: a.id, quantity: 1, add: false });
assert.equal((await call("cart"))[0].quantity, 1);
await call("saveCart", { id: a.id, quantity: 2, add: false });
await call("saveCart", { id: b.id, quantity: 1, add: true });
await call("saveCart", { id: c.id, quantity: 1, add: true });
await expectStatus(call("checkout", { ids: [] }), 400);
group("收藏幂等、并发加购、数量覆盖与超库存校验");

const cart = await call("cart");
const order = await call("checkout", { ids: [a.id, b.id] });
assert.equal(order.status, "PLACED");
assert.equal(order.orderNo, "B-" + order.id);
assert.deepEqual(array(order.items.map(item => item.productId)), [a.id, b.id]);
assert.equal(order.items[0].imageUrl, "img/product-bike.svg");
assert.equal(order.totalAmount, 147.9);
assert.equal(order.totalAmount, roundTotal(cart.filter(item => [a.id, b.id].includes(item.productId))));
assert.equal(order.tradePlace, "图书馆一楼");
assert.deepEqual(array((await call("cart")).map(item => item.productId)), [c.id]);
assert.equal((await live.product({ id: a.id })).stock, 1);
assert.equal((await live.product({ id: b.id })).status, "OFF_SHELF");
assert.equal((await live.orders({ token: other, role: "buyer" })).length, 0);
assert.ok((await admin("orders")).some(item => item.id === order.id && item.buyerPhone === "13800000001"));
group("批量下单、金额与快照、未选商品保留、售罄下架与订单隔离");

await expectStatus(call("moveToFavorites", { ids: [c.id, a.id] }), 400);
assert.equal((await call("cart")).length, 1);
await call("favorite", { id: c.id, enabled: true });
await call("moveToFavorites", { ids: [c.id] });
assert.equal((await call("cart")).length, 0);
assert.deepEqual(array((await call("favorites")).map(item => item.id)), [c.id]);
group("购物车转收藏原子提交，重复收藏仍只有一条");

const transition = (order, status) => admin("changeOrder", { id: order.id, status });
await expectStatus(transition(order, "COMPLETED"), 409);
for (const status of ["CONFIRMED", "PREPARED", "SHIPPED", "COMPLETED"]) {
  assert.equal((await transition(order, status)).status, status);
  const current = (await call("orders")).find(item => item.id === order.id);
  assert.equal(current.status, status);
  assert.equal(current.allowedActions.includes("CANCELLED"), ["CONFIRMED", "PREPARED"].includes(status));
  if (status === "SHIPPED") {
    await expectStatus(call("changeOrder", { id: order.id, status: "CANCELLED" }), 409);
    assert.ok((await admin("orders")).find(item => item.id === order.id).allowedActions.includes("CANCELLED"));
  }
}
await expectStatus(transition(order, "CANCELLED"), 409);
const customer = (await admin("adminCustomers")).find(item => item.username === account("buyer").username);
assert.equal(customer.totalSpent, 147.9);
assert.equal((await admin("adminCustomerOrders", { id: customer.id }))[0].status, "COMPLETED");
group("订单按五个状态流转、跳级拦截、角色取消边界与客户购买记录");

await call("saveCart", { id: a.id, quantity: 1, add: true });
const duplicates = await Promise.allSettled([1, 2].map(() => call("checkout", { ids: [a.id] })));
const successful = duplicates.filter(result => result.status === "fulfilled");
assert.equal(successful.length, 1);
assert.equal(duplicates.find(result => result.status === "rejected").reason.status, 400);
const cancelled = successful[0].value;
assert.equal((await live.product({ id: a.id })).status, "OFF_SHELF");
await transition(cancelled, "CONFIRMED");
await transition(cancelled, "PREPARED");
assert.equal((await call("changeOrder", { id: cancelled.id, status: "CANCELLED" })).cancelledBy, "CUSTOMER");
const returned = await live.product({ id: a.id });
assert.equal(returned.stock, 1);
assert.equal(returned.sales, 2);
assert.equal(returned.status, "ON_SALE");

await call("saveCart", { id: c.id, quantity: 1, add: true });
const shipped = await call("checkout", { ids: [c.id] });
for (const status of ["CONFIRMED", "PREPARED", "SHIPPED"]) await transition(shipped, status);
await admin("productStatus", { id: c.id, status: "OFF_SHELF" });
assert.equal((await transition(shipped, "CANCELLED")).cancelledBy, "SELLER");
const withdrawn = await live.product({ id: c.id });
assert.equal(withdrawn.stock, 10);
assert.equal(withdrawn.status, "OFF_SHELF");
group("同一购物车重复结算、买家发货前取消、商家发货后取消与库存回补");

const last = await create("联调末件", 1, 10);
await Promise.all([buyer, other].map(token => live.saveCart({ token, id: last.id, quantity: 1, add: true })));
const race = await Promise.allSettled([buyer, other].map(token => live.checkout({ token, ids: [last.id] })));
assert.equal(race.filter(result => result.status === "fulfilled").length, 1);
assert.equal(race.find(result => result.status === "rejected").reason.status, 400);
assert.equal((await live.product({ id: last.id })).stock, 0);
group("两名买家并发购买最后一件，只生成一笔有效订单");

console.log("需求 B 通过 8 组前后端联调验收。");
