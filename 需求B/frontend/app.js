"use strict";

const { demo, live, roundTotal } = window.ShopApi;
const modeSelect = document.querySelector("#mode");
const roleSelect = document.querySelector("#role");
const navigation = document.querySelector("#navigation");
const heading = document.querySelector("#heading");
const view = document.querySelector("#view");
const notice = document.querySelector("#notice");
const logout = document.querySelector("#logout");
const session = { buyer: null, seller: null };
const selection = new Set();
const titles = { products: "商品浏览", favorites: "我的收藏", cart: "购物车", orders: "我的订单" };
const statuses = {
  PLACED: "客户下单", CONFIRMED: "商家确认", PREPARED: "备货完成",
  SHIPPED: "开始发货", COMPLETED: "交易完成", CANCELLED: "已取消"
};
const actionNames = {
  CONFIRMED: "确认订单", PREPARED: "标记备货完成", SHIPPED: "开始发货",
  COMPLETED: "标记交易完成", CANCELLED: "取消订单"
};
const money = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });

let page = "products";
let keyword = "";
let refreshing = 0;
let saving = false;

function node(tag, attributes = {}, ...children) {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) el.setAttribute(name, value);
  el.append(...children);
  return el;
}

function text(tag, value, className) {
  return node(tag, className ? { class: className } : {}, String(value ?? ""));
}

function button(label, handler, className) {
  const el = text("button", label, className);
  el.type = "button";
  el.addEventListener("click", handler);
  return el;
}

function role() {
  return roleSelect.value;
}

function connected() {
  return modeSelect.value === "live";
}

function send(action, data = {}) {
  const api = connected() ? live : demo;
  return api[action]({ ...data, role: role(), token: session[role()] });
}

function feedback(message = "", error = false) {
  notice.textContent = message;
  notice.hidden = !message;
  notice.classList.toggle("error", error);
}

function empty(message) {
  return text("p", message, "empty muted");
}

function open(next) {
  page = next;
  feedback();
  heading.focus();
  void render();
}

async function mutate(action, data, message, after) {
  if (saving) return;
  saving = true;
  modeSelect.disabled = true;
  roleSelect.disabled = true;
  const controls = [...document.querySelectorAll("main button:not(:disabled), #view input:not(:disabled)")];
  controls.forEach(el => { el.disabled = true; });
  view.setAttribute("aria-busy", "true");
  feedback();
  try {
    const result = await send(action, data);
    after?.(result);
    feedback(message);
    await render();
  } catch (error) {
    feedback(error.message, true);
  } finally {
    saving = false;
    modeSelect.disabled = false;
    roleSelect.disabled = false;
    controls.forEach(el => { el.disabled = false; });
    view.removeAttribute("aria-busy");
  }
}

function loginForm() {
  const username = node("input", { name: "username", autocomplete: "username", required: "" });
  const password = node("input", { name: "password", type: "password", autocomplete: "current-password", required: "" });
  const form = node("form", { class: "login" },
    node("label", {}, "用户名", username),
    node("label", {}, "密码", password),
    node("button", { type: "submit" }, "登录")
  );
  form.addEventListener("submit", event => {
    event.preventDefault();
    void mutate("login", { username: username.value.trim(), password: password.value }, "登录成功", token => {
      session[role()] = token;
    });
  });
  return form;
}

function catalogSearch() {
  const input = node("input", { type: "search", "aria-label": "商品关键词", placeholder: "搜索商品" });
  input.value = keyword;
  const form = node("form", { class: "search" }, input, node("button", { type: "submit" }, "搜索"));
  form.addEventListener("submit", event => {
    event.preventDefault();
    keyword = input.value.trim();
    feedback();
    void render();
  });
  return form;
}

function productCards(products, favoriteIds) {
  if (!products.length) return empty(page === "favorites" ? "还没有收藏商品" : "没有符合条件的商品");
  return node("div", { class: "grid" }, ...products.map(product => {
    const card = node("article", { class: "card" });
    if (product.imageUrl) {
      try {
        const url = new URL(product.imageUrl, document.baseURI);
        if (["http:", "https:", "file:"].includes(url.protocol)) {
          const img = node("img", { src: url.href, alt: "", class: "card-image", loading: "lazy" });
          img.addEventListener("error", () => img.remove());
          card.append(img);
        }
      } catch {
        // 商品缺少有效图片时仍可浏览和购买。
      }
    }
    const saved = favoriteIds.has(String(product.id));
    const favorite = button(saved ? "取消收藏" : "收藏", () => {
      void mutate("favorite", { id: product.id, enabled: !saved }, saved ? "已取消收藏" : "已加入收藏");
    }, "secondary");
    favorite.setAttribute("aria-pressed", String(saved));
    const add = button("加入购物车", () => {
      void mutate("saveCart", { id: product.id, quantity: 1, add: true }, "已加入购物车");
    });
    add.disabled = product.stock < 1;
    card.append(text("h2", product.name), text("p", product.description, "muted"),
      text("div", money.format(product.price), "price"), text("p", `库存 ${product.stock} 件`, "muted"),
      node("div", { class: "actions" }, add, favorite));
    return card;
  }));
}

function cartList(items) {
  const validIds = new Set(items.filter(item => item.quantity <= item.stock).map(item => item.productId));
  for (const id of selection) if (!validIds.has(id)) selection.delete(id);
  if (!items.length) return empty("购物车为空，可以先去浏览商品");
  const total = node("strong");
  const checkout = button("提交所选商品", () => {
    void mutate("checkout", { ids: [...selection] }, "下单成功", () => {
      selection.clear();
      page = "orders";
    });
  });
  const updateTotal = () => {
    const selected = items.filter(item => selection.has(item.productId));
    checkout.disabled = !selected.length;
    total.textContent = `已选 ${selected.length} 种商品 · ${money.format(roundTotal(selected))}`;
  };
  const list = node("div", { class: "list" }, ...items.map(item => {
    const id = item.productId;
    const check = node("input", { type: "checkbox", "aria-label": `选择 ${item.name}` });
    check.checked = selection.has(id);
    check.disabled = !validIds.has(id);
    check.addEventListener("change", () => {
      if (check.checked) selection.add(id);
      else selection.delete(id);
      updateTotal();
    });
    const quantity = node("input", {
      type: "number", min: 1, max: item.stock, step: 1, required: "",
      value: item.quantity, "aria-label": `${item.name} 的数量`
    });
    const save = button("更新数量", () => {
      if (quantity.reportValidity()) {
        void mutate("saveCart", { id, quantity: quantity.valueAsNumber, add: false }, "数量已更新");
      }
    }, "secondary");
    save.disabled = item.stock < 1;
    const remove = button("移除", () => {
      void mutate("removeCart", { id }, "已从购物车移除");
    }, "danger");
    return node("article", { class: "card row" },
      node("div", {}, node("h2", {}, node("label", {}, check, ` ${item.name}`)),
        text("div", `${money.format(item.price)} / 件 · 库存 ${item.stock} 件`, "muted")),
      node("div", { class: "actions" }, quantity, save, remove)
    );
  }));
  updateTotal();
  return node("div", {}, list, node("div", { class: "total" }, total, checkout));
}

function orderList(orders) {
  if (!orders.length) return empty("暂无订单");
  return node("div", { class: "list" }, ...orders.map(order => {
    const actions = (order.allowedActions || []).filter(status => Object.hasOwn(actionNames, status)).map(status => {
      return button(actionNames[status], () => {
        if (status === "CANCELLED" && !window.confirm(`确认取消订单 #${order.id}？`)) return;
        void mutate("changeOrder", { id: order.id, status }, "订单状态已更新");
      }, status === "CANCELLED" ? "danger" : "");
    });
    const date = new Date(order.createdAt);
    const details = [role() === "seller" ? order.buyerName : "", Number.isNaN(date.valueOf()) ? "" : date.toLocaleString("zh-CN")]
      .filter(Boolean).join(" · ");
    return node("article", { class: "card" },
      node("div", { class: "row" }, text("h2", `订单 #${order.id}`),
        text("span", Object.hasOwn(statuses, order.status) ? statuses[order.status] : order.status, "status")),
      ...(details ? [text("p", details, "muted")] : []),
      node("ul", { class: "order-items" }, ...order.items.map(item =>
        text("li", `${item.name} × ${item.quantity} · ${money.format(item.price)} / 件`)
      )),
      text("strong", `合计 ${money.format(order.totalAmount)}`),
      ...(actions.length ? [node("div", { class: "actions" }, ...actions)] : [])
    );
  }));
}

async function render() {
  const version = ++refreshing;
  const title = key => role() === "seller" ? "订单管理" : titles[key];
  const needsLogin = connected() && !session[role()];
  navigation.replaceChildren();
  heading.textContent = needsLogin ? (role() === "seller" ? "商家登录" : "买家登录") : title(page);
  logout.hidden = !connected() || needsLogin;
  if (needsLogin) {
    view.replaceChildren(loginForm());
    return;
  }
  const pages = role() === "seller" ? ["orders"] : Object.keys(titles);
  for (const key of pages) {
    const tab = button(title(key), () => open(key));
    if (key === page) tab.setAttribute("aria-current", "page");
    navigation.append(tab);
  }

  const results = node("div", { "aria-busy": "true" }, empty("正在加载…"));
  view.replaceChildren(...(page === "products" ? [catalogSearch()] : []), results);
  try {
    let content;
    if (page === "products") {
      const [products, favorites] = await Promise.all([send("products", { keyword }), send("favorites")]);
      if (version !== refreshing) return;
      content = productCards(products, new Set(favorites.map(item => String(item.id))));
    } else {
      const list = await send(page);
      if (version !== refreshing) return;
      if (page === "favorites") content = productCards(list, new Set(list.map(item => String(item.id))));
      if (page === "cart") content = cartList(list);
      if (page === "orders") content = orderList(list);
    }
    results.replaceChildren(content);
  } catch (error) {
    if (version !== refreshing) return;
    feedback(error.message, true);
    results.replaceChildren(button("重新加载", () => {
      feedback();
      void render();
    }, "secondary"));
  } finally {
    results.removeAttribute("aria-busy");
  }
}

function resetView() {
  selection.clear();
  keyword = "";
  open(role() === "seller" ? "orders" : "products");
}

roleSelect.addEventListener("change", resetView);
modeSelect.addEventListener("change", () => {
  session.buyer = null;
  session.seller = null;
  resetView();
});
logout.addEventListener("click", () => {
  session[role()] = null;
  resetView();
});
void render();
