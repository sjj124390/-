/**
 * integration-e2e.mjs —— 前后端集成自动化验收脚本（系统集成 · 朱江）
 *
 * 作用：
 *   在 Node 中加载真实的 frontend/js/api.js（接口适配层），
 *   对接真实的 Spring Boot 后端（8080）+ MySQL，逐项验证：
 *     商品查询 → 登录 → 发布 → 下单自动冻结 → 订单查询 → 标记成交 → 自动补货 → 改密校验
 *   这是「运行」验收要求的自动化执行脚本，可重复运行。
 *
 * 前置：后端与 MySQL 已启动（scripts\start-all.ps1）
 * 用法：node tests\integration-e2e.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api';

// ---------- 浏览器环境桩 ----------
const store = new Map();
globalThis.window = globalThis;
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
  clear: () => store.clear()
};
window.APP_CONFIG = { API_BASE_URL: API_BASE };
window.location = { pathname: '/login.html', search: '', href: 'login.html' };

const apiSource = fs.readFileSync(path.join(__dirname, '..', 'frontend', 'js', 'api.js'), 'utf8');
new Function(apiSource)();
const { Api, Auth } = globalThis;

// ---------- 断言与统计 ----------
let passed = 0;
const failures = [];

function ok(name, condition, detail = '') {
  if (condition) {
    passed++;
    console.log(`  [PASS] ${name}${detail ? ' — ' + detail : ''}`);
  } else {
    failures.push(name);
    console.log(`  [FAIL] ${name}${detail ? ' — ' + detail : ''}`);
  }
}

async function step(title, fn) {
  console.log(`\n== ${title}`);
  try {
    await fn();
  } catch (err) {
    failures.push(title);
    console.log(`  [FAIL] ${title} — 未预期异常：${err.message}`);
  }
}

// ---------- 用例 ----------
console.log('在线购物系统 · 集成自动化验收');
console.log('后端地址：' + API_BASE);

// 0. 准备：确保存在在售商品
let current = await Api.getProductDetail();
if (!current) {
  console.log('\n== 准备数据：当前无在售商品，自动发布一件');
  await Api.sellerLogin({ username: 'admin', password: 'admin123' });
  await Api.addProduct({
    name: '集成验收测试商品',
    price: 199,
    description: '用于集成自动化验收的测试商品',
    imageUrl: 'img/product-default.svg'
  });
  current = await Api.getProductDetail();
}

await step('用例 1：前台商品详情（GET /products/current 适配）', async () => {
  ok('返回在售商品', !!current, current ? `${current.name} / ¥${current.price}` : '为空');
  ok('状态映射为「在售(0)」', current && current.status === 0, `status=${current && current.status}`);
  ok('字段完整（名称/价格/描述/图片）',
    !!(current && current.name && current.price && current.description && current.imageUrl));
});

await step('用例 2：按 id 查询详情（历史商品也可查）', async () => {
  const byId = await Api.getProductDetail(current.id);
  ok('id 查询命中同一商品', !!byId && String(byId.id) === String(current.id));
});

await step('用例 3：商品列表合并（当前 + 历史）', async () => {
  const list = await Api.getProductList();
  ok('列表非空', Array.isArray(list) && list.length > 0, `共 ${list.length} 条`);
  ok('包含当前在售商品', list.some((p) => String(p.id) === String(current.id)));
  ok('按 id 倒序', list.every((p, i) => i === 0 || Number(list[i - 1].id) >= Number(p.id)));
});

await step('用例 4：卖家登录失败（错误密码）', async () => {
  let msg = '';
  try {
    await Api.sellerLogin({ username: 'admin', password: 'wrong-pass' });
  } catch (e) {
    msg = e.message;
  }
  ok('提示后端原始错误信息', msg === '账号或密码错误', msg);
  ok('未写入登录态', Auth.isLogin() === false);
});

await step('用例 5：卖家登录成功', async () => {
  const res = await Api.sellerLogin({ username: 'admin', password: 'admin123' });
  ok('返回 token', !!(res && res.token));
  ok('返回用户名 admin', res.username === 'admin');
  ok('登录态已写入', Auth.isLogin() === true);
});

await step('用例 6：已有在售商品时发布新商品被业务规则拒绝', async () => {
  let blocked = false;
  let msg = '';
  try {
    await Api.addProduct({ name: '重复在售测试', price: 1, description: 'x' });
  } catch (e) {
    blocked = true;
    msg = e.message;
  }
  ok('唯一在售约束生效（发布会失败）', blocked, msg);
  ok('错误文案已本地化为中文', msg.indexOf('已有在售') === 0, msg);
});

await step('用例 7：买家提交购买意向进入排队（迭代 2 排队制）', async () => {
  const res = await Api.submitOrder({
    productId: current.id,
    buyerName: '王小明',
    buyerPhone: '13900001234',
    remark: '周末下午面交，地铁站口'
  });
  ok('返回口令码', !!(res && res.code), res && res.code);
  const after = await Api.getProductDetail(current.id);
  ok('商品保持「在售(0)」，允许后续买家排队',
    after && after.status === 0, `status=${after && after.status}`);
});

await step('用例 8：后台意向订单列表', async () => {
  const orders = await Api.getOrderList();
  const mine = orders.find((o) => String(o.productId) === String(current.id));
  ok('能查到该意向单', !!mine, mine ? `${mine.buyerName} / ${mine.buyerPhone}` : '未找到');
  ok('买家信息映射正确（姓名/电话/备注）',
    !!(mine && mine.buyerName === '王小明' && mine.buyerPhone === '13900001234' && mine.remark));
  ok('订单状态为「待交易(0)」', mine && mine.status === 0, `status=${mine && mine.status}`);
});

await step('用例 9：标记交易成功 → 商品售出', async () => {
  const orders = await Api.getOrderList();
  const mine = orders.find((o) => String(o.productId) === String(current.id));
  await Api.updateOrderStatus({ orderId: mine.id, status: 1 });

  const after = await Api.getProductDetail(current.id);
  ok('商品状态变为「已售(2)」', after && after.status === 2, `status=${after && after.status}`);
});

await step('用例 10：成交后自动补货（卖掉一个再制作一个）', async () => {
  const fresh = await Api.getProductDetail();
  ok('自动生成新的在售商品', !!fresh && String(fresh.id) !== String(current.id),
    fresh ? `新商品 id=${fresh.id}` : '无在售商品');
  ok('新商品为同款（名称一致）', fresh && fresh.name === current.name);
  ok('新商品价格一致', fresh && Number(fresh.price) === Number(current.price));
  current = fresh;
});

await step('用例 11：修改密码校验原密码', async () => {
  let msg = '';
  try {
    await Api.updatePassword({ username: 'admin', oldPassword: 'bad-old', newPassword: 'admin123' });
  } catch (e) {
    msg = e.message;
  }
  ok('原密码错误被拒绝', msg === '原密码错误', msg);
  ok('原密码仍可登录', !!(await Api.sellerLogin({ username: 'admin', password: 'admin123' })).token);
});

await step('用例 12：登录态存取与登出', async () => {
  ok('Auth 可读取卖家名', Auth.getSellerName() === 'admin', Auth.getSellerName());
  Auth.clearAuth();
  ok('clearAuth 后不再是登录态', Auth.isLogin() === false);
});

await step('用例 13：意向排队 → 手动下架作废 → 口令失效 → 恢复上架（迭代 2）', async () => {
  // 提交排队意向（迭代 2：商品保持「在售」，不再下单即冻结）
  const order = await Api.submitOrder({
    productId: current.id,
    buyerName: '李雷',
    buyerPhone: '13900004567',
    remark: '迭代2排队验证'
  });
  const passcode = order && order.code;
  ok('排队意向提交成功（返回口令码）', !!passcode);

  const listed = await Api.getProductDetail(current.id);
  ok('商品保持「在售(0)」等待卖家处理', listed && listed.status === 0, `status=${listed && listed.status}`);

  const progress = await Api.queryIntent({ queryCode: passcode });
  ok('口令码查询到交易进度', !!progress && progress.productName === current.name,
    progress ? `商品=${progress.productName}` : '无返回');
  ok('进度为「待交易(0)」', !!progress && progress.status === 0, `status=${progress && progress.status}`);

  // 手动下架：商品转「手动下架(3)」，排队意向统一失效
  await Api.offlineProduct(current.id);
  const offline = await Api.getProductDetail(current.id);
  ok('商品状态变为「手动下架(3)」', offline && offline.status === 3, `status=${offline && offline.status}`);

  const orders = await Api.getOrderList();
  const voided = orders.find((o) => o.buyerName === '李雷');
  ok('排队意向被作废为「意向失效(3)」', !!voided && voided.status === 3,
    voided ? `status=${voided.status}` : '未找到该订单');

  let voidedMsg = '';
  try {
    await Api.queryIntent({ queryCode: passcode });
  } catch (e) {
    voidedMsg = e.message;
  }
  ok('已作废意向的口令码查询被拒绝', /失效|无效/.test(voidedMsg), voidedMsg);

  await Api.restoreProduct(current.id);
  const back = await Api.getProductDetail(current.id);
  ok('恢复上架后回到「在售(0)」', back && back.status === 0, `status=${back && back.status}`);
});

await step('用例 14：无效口令码查询被拒绝（迭代 2）', async () => {
  let msg = '';
  try {
    await Api.queryIntent({ queryCode: '00000000deadbeef' });
  } catch (e) {
    msg = e.message;
  }
  ok('无效口令码返回中文错误', /失效|无效/.test(msg), msg);
});

// ---------- 汇总 ----------
console.log('\n============================================');
console.log(`通过 ${passed} 项，失败 ${failures.length} 项`);
if (failures.length) {
  failures.forEach((f) => console.log('  失败：' + f));
  process.exit(1);
}
console.log('集成验收全部通过');
console.log('============================================');
