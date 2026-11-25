"use strict";

const { demo, live, roundTotal } = window.ShopApi;
const modeSelect = document.querySelector("#mode");
const roleSelect = document.querySelector("#role");
const navigation = document.querySelector("#navigation");
const heading = document.querySelector("#heading");
const view = document.querySelector("#view");
const notice = document.querySelector("#notice");
const logout = document.querySelector("#logout");
const signin = document.querySelector("#signin");
const session = { buyer: null, seller: null };
const selection = new Set();
const titles = { products: "商品浏览", favorites: "我的收藏", cart: "购物车", orders: "我的订单", profile: "个人资料" };
const adminTitles = { orders: "订单管理", adminProducts: "商品管理", adminCategories: "分类管理",
  adminCustomers: "客户管理", password: "修改密码" };
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
let categoryId = "";
let catalogPage = 1;
const pageSize = 12;
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
  const dialogNotice = document.querySelector("dialog[open] [role=status]");
  if (dialogNotice) {
    dialogNotice.textContent = message;
    dialogNotice.hidden = !message;
    dialogNotice.classList.toggle("error", error);
  }
}

function empty(message) {
  return text("p", message, "empty muted");
}

function open(next) {
  if (page !== next && ["adminProducts", "products"].includes(next)) {
    keyword = "";
    categoryId = "";
    catalogPage = 1;
  }
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
  const controls = [...document.querySelectorAll("main button:not(:disabled), #view input:not(:disabled), dialog :is(button,input,select,textarea):not(:disabled)")];
  controls.forEach(el => { el.disabled = true; });
  view.setAttribute("aria-busy", "true");
  feedback();
  try {
    const result = await send(action, data);
    after?.(result);
    feedback(message);
    await render();
  } catch (error) {
    await showRequestError(error);
  } finally {
    saving = false;
    modeSelect.disabled = false;
    roleSelect.disabled = false;
    controls.forEach(el => { el.disabled = false; });
    view.removeAttribute("aria-busy");
  }
}

async function showRequestError(error) {
  if (error.status === 401 && session[role()]) {
    session[role()] = null;
    selection.clear();
    if (role() === "buyer") page = "login";
    await render();
  }
  feedback(error.message, true);
}

function loginForm(registering = false) {
  const username = node("input", { name: "username", autocomplete: "username", required: "" });
  const password = node("input", { name: "password", type: "password", autocomplete: "current-password", required: "" });
  const phone = node("input", { name: "phone", type: "tel", required: "", maxlength: 20 });
  const tradePlace = node("input", { name: "defaultTradePlace", required: "", maxlength: 200 });
  if (registering) {
    username.minLength = 3;
    username.maxLength = 20;
    password.autocomplete = "new-password";
    password.minLength = 6;
    password.maxLength = 32;
  }
  const form = node("form", { class: "login" },
    node("label", {}, "用户名", username),
    node("label", {}, "密码", password),
    ...(registering ? [node("label", {}, "电话", phone), node("label", {}, "默认交易地点", tradePlace)] : []),
    node("button", { type: "submit" }, registering ? "注册并登录" : "登录")
  );
  if (role() === "buyer") form.append(button(registering ? "返回登录" : "注册买家", () => {
    feedback();
    view.replaceChildren(loginForm(!registering));
  }, "secondary"));
  form.addEventListener("submit", event => {
    event.preventDefault();
    const credentials = { username: username.value.trim(), password: password.value };
    const data = registering ? { ...credentials, phone: phone.value.trim(), defaultTradePlace: tradePlace.value.trim() } : credentials;
    void mutate(registering ? "register" : "login", data, registering ? "注册成功" : "登录成功", token => {
      session[role()] = token;
      if (page === "login") page = "products";
    });
  });
  return form;
}

function catalogSearch(categories) {
  const input = node("input", { type: "search", "aria-label": "商品关键词", placeholder: "搜索商品" });
  input.value = keyword;
  const category = node("select", { "aria-label": "商品分类" }, node("option", { value: "" }, "全部分类"));
  for (const group of categories) {
    category.append(node("option", { value: group.id }, group.name));
    for (const child of group.children) category.append(node("option", { value: child.id }, `　${child.name}`));
  }
  category.value = categoryId;
  const form = node("form", { class: "search" }, input, ...(categories.length ? [category] : []),
    node("button", { type: "submit" }, "搜索"));
  form.addEventListener("submit", event => {
    event.preventDefault();
    keyword = input.value.trim();
    categoryId = category.value;
    catalogPage = 1;
    feedback();
    void render();
  });
  return form;
}

function catalogPagination(content, total) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  if (pageCount === 1 && catalogPage === 1) return content;
  const previous = button("上一页", () => { catalogPage--; void render(); }, "secondary");
  const next = button("下一页", () => { catalogPage++; void render(); }, "secondary");
  previous.disabled = catalogPage <= 1;
  next.disabled = catalogPage >= pageCount;
  return node("div", {}, content, node("div", { class: "actions pager" }, previous,
    text("span", `第 ${catalogPage} / ${pageCount} 页`), next));
}

function descriptionTemplate(html) {
  const template = document.createElement("template");
  template.innerHTML = html || "";
  template.content.querySelectorAll("script,style,iframe,object,template").forEach(element => element.remove());
  return template.content;
}

function descriptionContent(html) {
  const result = node("div", { class: "description muted" });
  const allowed = new Set(["P", "BR", "B", "STRONG", "I", "EM", "UL", "OL", "LI", "H3", "H4", "BLOCKQUOTE"]);
  function append(source, target) {
    for (const child of source.childNodes) {
      if (child.nodeType === Node.TEXT_NODE) {
        target.append(child.textContent);
      } else if (child.nodeName === "IMG") {
        const img = productImage(child.getAttribute("src"));
        if (img) target.append(img);
      } else if (!["SCRIPT", "STYLE", "IFRAME", "OBJECT", "TEMPLATE"].includes(child.nodeName)) {
        const element = allowed.has(child.nodeName) ? node(child.nodeName.toLowerCase()) : target;
        append(child, element);
        if (element !== target) target.append(element);
      }
    }
  }
  append(descriptionTemplate(html), result);
  return result;
}

function productImage(source) {
  if (!source) return null;
  try {
    const url = new URL(source, document.baseURI);
    if (!["http:", "https:", "file:"].includes(url.protocol)) return null;
    const img = node("img", { src: url.href, alt: "", class: "card-image", loading: "lazy" });
    img.addEventListener("error", () => img.remove());
    return img;
  } catch {
    return null;
  }
}

async function showProduct(id) {
  const version = refreshing;
  try {
    const product = await send("product", { id });
    if (version !== refreshing || document.querySelector("dialog[open]")) return;
    const images = [...new Set([product.imageUrl, ...(product.images || [])])].map(productImage).filter(Boolean);
    const dialog = node("dialog", { class: "card detail", "aria-label": product.name },
      text("h2", product.name), descriptionContent(product.description),
      text("p", `${money.format(product.price)} · 库存 ${product.stock} 件`),
      node("div", { class: "grid" }, ...images));
    dialog.append(button("关闭详情", () => dialog.close(), "secondary"));
    dialog.addEventListener("close", () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
  } catch (error) {
    await showRequestError(error);
  }
}

function productCards(products, favoriteIds) {
  if (!products.length) return empty(page === "favorites" ? "还没有收藏商品" : "没有符合条件的商品");
  return node("div", { class: "grid" }, ...products.map(product => {
    const card = node("article", { class: "card" });
    const image = productImage(product.imageUrl);
    if (image) card.append(image);
    const saved = favoriteIds.has(String(product.id));
    const favorite = button(saved ? "取消收藏" : "收藏", () => {
      if (connected() && !session.buyer) return open("login");
      void mutate("favorite", { id: product.id, enabled: !saved }, saved ? "已取消收藏" : "已加入收藏");
    }, "secondary");
    favorite.setAttribute("aria-pressed", String(saved));
    const add = button("加入购物车", () => {
      if (connected() && !session.buyer) return open("login");
      void mutate("saveCart", { id: product.id, quantity: 1, add: true }, "已加入购物车");
    });
    add.disabled = product.stock < 1 || (Boolean(product.status) && product.status !== "ON_SALE");
    card.append(text("h2", product.name), text("p", descriptionTemplate(product.description).textContent, "muted"),
      text("div", money.format(product.price), "price"), text("p", product.status && product.status !== "ON_SALE" ? "已下架" : `库存 ${product.stock} 件`, "muted"),
      node("div", { class: "actions" }, add, favorite,
        button("查看详情", () => void showProduct(product.id), "secondary")));
    return card;
  }));
}

function cartList(items) {
  const available = item => item.stock > 0 && (!item.status || item.status === "ON_SALE");
  const idsInCart = new Set(items.map(item => item.productId));
  for (const id of selection) if (!idsInCart.has(id)) selection.delete(id);
  if (!items.length) return empty("购物车为空，可以先去浏览商品");
  const total = node("strong");
  const checkout = button("提交所选商品", () => {
    void mutate("checkout", { ids: [...selection] }, "下单成功", () => {
      selection.clear();
      page = "orders";
    });
  });
  const move = button("将所选商品转入收藏", () => {
    void mutate("moveToFavorites", { ids: [...selection] }, "已转入收藏，并从购物车移除", () => selection.clear());
  }, "secondary");
  const updateTotal = () => {
    const selected = items.filter(item => selection.has(item.productId));
    const unavailable = selected.some(item => !available(item) || item.quantity > item.stock);
    checkout.disabled = !selected.length || unavailable || selected.length > 50;
    move.disabled = !selected.length;
    const hint = unavailable ? " · 所选有下架或库存不足商品" : selected.length > 50 ? " · 单次最多下单 50 种" : "";
    total.textContent = `已选 ${selected.length} 种商品 · ${money.format(roundTotal(selected))}${hint}`;
  };
  const list = node("div", { class: "list" }, ...items.map(item => {
    const id = item.productId;
    const check = node("input", { type: "checkbox", "aria-label": `选择 ${item.name}` });
    check.checked = selection.has(id);
    check.addEventListener("change", () => {
      if (check.checked) selection.add(id);
      else selection.delete(id);
      updateTotal();
    });
    const quantity = node("input", {
      type: "number", min: 1, max: Math.min(item.stock, 999), step: 1, required: "",
      value: item.quantity, "aria-label": `${item.name} 的数量`
    });
    const save = button("更新数量", () => {
      if (quantity.reportValidity()) {
        void mutate("saveCart", { id, quantity: quantity.valueAsNumber, add: false }, "数量已更新");
      }
    }, "secondary");
    save.disabled = !available(item);
    quantity.disabled = !available(item);
    const remove = button("移除", () => {
      void mutate("removeCart", { id }, "已从购物车移除");
    }, "danger");
    return node("article", { class: "card row" },
      node("div", {}, node("h2", {}, node("label", {}, check, ` ${item.name}`)),
        text("div", `${money.format(item.price)} / 件 · ${item.status && item.status !== "ON_SALE" ? "已下架" : `库存 ${item.stock} 件`}`, "muted")),
      node("div", { class: "actions" }, quantity, save, remove)
    );
  }));
  updateTotal();
  return node("div", {}, list, node("div", { class: "total" }, total, node("div", { class: "actions" }, move, checkout)));
}

function orderList(orders, showActions = true) {
  if (!orders.length) return empty("暂无订单");
  return node("div", { class: "list" }, ...orders.map(order => {
    const actions = (showActions ? order.allowedActions || [] : []).filter(status => Object.hasOwn(actionNames, status)).map(status => {
      return button(actionNames[status], () => {
        if (status === "CANCELLED" && !window.confirm(`确认取消订单 #${order.id}？`)) return;
        void mutate("changeOrder", { id: order.id, status }, "订单状态已更新");
      }, status === "CANCELLED" ? "danger" : "");
    });
    const date = new Date(order.createdAt);
    const details = [role() === "seller" ? [order.buyerName, order.buyerPhone].filter(Boolean).join(" · ") : "",
      order.tradePlace, Number.isNaN(date.valueOf()) ? "" : date.toLocaleString("zh-CN")]
      .filter(Boolean).join(" · ");
    return node("article", { class: "card" },
      node("div", { class: "row" }, text("h2", `订单 #${order.orderNo ?? order.id}`),
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
  const title = key => role() === "seller" ? adminTitles[key] : titles[key];
  const signedIn = Boolean(session[role()]);
  const needsLogin = connected() && !signedIn && (role() === "seller" || page !== "products");
  navigation.replaceChildren();
  heading.textContent = needsLogin ? (role() === "seller" ? "商家登录" : "买家登录") : title(page);
  logout.hidden = !connected() || !signedIn;
  signin.hidden = !connected() || signedIn || role() !== "buyer" || needsLogin;
  const pages = role() === "seller" ? (needsLogin ? [] : connected() ? Object.keys(adminTitles) : ["orders"]) :
    Object.keys(titles).filter(key => key !== "profile" || connected());
  for (const key of pages) {
    const tab = button(title(key), () => open(key));
    if (key === page) tab.setAttribute("aria-current", "page");
    navigation.append(tab);
  }

  if (needsLogin) {
    view.replaceChildren(loginForm());
    return;
  }
  const results = node("div", { "aria-busy": "true" }, empty("正在加载…"));
  view.replaceChildren(results);
  try {
    let content;
    if (page === "adminProducts") {
      const [products, categories] = await Promise.all([send("adminProducts", { keyword, categoryId, page: catalogPage, size: pageSize }),
        send("adminCategories")]);
      if (version !== refreshing) return;
      view.prepend(catalogSearch(categories));
      content = catalogPagination(adminProductList(products.records, categories), products.total);
    } else if (page === "password") {
      content = passwordForm();
    } else if (page === "products") {
      const [products, categories, favorites] = await Promise.all([
        send("products", { keyword, categoryId, page: catalogPage, size: pageSize }),
        send("categories"),
        connected() && !signedIn ? [] : send("favorites")
      ]);
      if (version !== refreshing) return;
      view.prepend(catalogSearch(categories));
      content = productCards(products.records, new Set(favorites.map(item => String(item.id))));
      content = catalogPagination(content, products.total);
    } else {
      const list = await send(page);
      if (version !== refreshing) return;
      if (page === "favorites") content = productCards(list, new Set(list.map(item => String(item.id))));
      if (page === "cart") content = cartList(list);
      if (page === "orders") content = orderList(list);
      if (page === "adminCategories") content = adminCategoryList(list);
      if (page === "adminCustomers") content = adminCustomerList(list);
      if (page === "profile") content = node("article", { class: "card" }, text("h2", list.username),
        text("p", `电话 ${list.phone}`), text("p", `默认交易地点 ${list.defaultTradePlace}`),
        text("p", `订单 ${list.orderCount} 笔 · 累计 ${money.format(list.totalSpent)}`));
    }
    results.replaceChildren(content);
  } catch (error) {
    if (version !== refreshing) return;
    await showRequestError(error);
    if (version !== refreshing) return;
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
  categoryId = "";
  catalogPage = 1;
  open(role() === "seller" ? "orders" : "products");
}

roleSelect.addEventListener("change", resetView);
modeSelect.addEventListener("change", () => {
  session.buyer = null;
  session.seller = null;
  resetView();
});
signin.addEventListener("click", () => open("login"));
logout.addEventListener("click", () => {
  session[role()] = null;
  resetView();
});
void render();
