"use strict";

function formField(tag, label, attributes, value = "") {
  const control = node(tag, attributes);
  control.value = value ?? "";
  return node("label", {}, label, control);
}

function manageForm(title, fields, submit, label = "保存") {
  const form = node("form", { class: "manage-form" }, ...fields,
    node("div", { class: "actions" }, node("button", { type: "submit" }, label)));
  const dialog = node("dialog", { class: "card detail", "aria-label": title }, text("h2", title),
    node("p", { class: "dialog-notice", role: "status", hidden: "" }), form);
  form.lastElementChild.append(button("返回", () => dialog.close(), "secondary"));
  form.addEventListener("submit", event => {
    event.preventDefault();
    submit(Object.fromEntries(new FormData(form)), dialog);
  });
  dialog.addEventListener("cancel", event => { if (saving) event.preventDefault(); });
  dialog.addEventListener("close", () => dialog.remove());
  document.body.append(dialog);
  dialog.showModal();
}

function categoryField(categories, label, value, options = {}) {
  const select = node("select", { name: "categoryId" }, node("option", { value: "" }, options.empty || "不分类"));
  for (const group of categories) {
    if (options.exclude === group.id) continue;
    select.append(node("option", { value: group.id }, group.name));
    if (!options.rootsOnly) {
      for (const child of group.children) select.append(node("option", { value: child.id }, `　${child.name}`));
    }
  }
  select.value = value ?? "";
  return node("label", {}, label, select);
}

function productEditor(categories, product = {}) {
  manageForm(product.id ? "编辑商品" : "发布商品", [
    formField("input", "商品名称", { name: "name", maxlength: 100, required: "" }, product.name),
    formField("input", "价格（元）", { name: "price", type: "number", min: .01, step: .01, max: 99999999.99, required: "" }, product.price),
    formField("input", "库存", { name: "stock", type: "number", min: 0, max: 2147483647, required: "" }, product.stock ?? 1),
    categoryField(categories, "分类", product.categoryId),
    formField("textarea", "商品描述", { name: "description", rows: 4 }, product.description),
    formField("input", "封面图片地址", { name: "imageUrl", maxlength: 500 }, product.imageUrl),
    formField("textarea", "其他图片地址（每行一张）", { name: "images", rows: 3 },
      (product.images || []).filter(image => image !== product.imageUrl).join("\n"))
  ], (data, dialog) => {
    void mutate("saveProduct", { id: product.id, product: {
      ...data, price: Number(data.price), stock: Number(data.stock), categoryId: Number(data.categoryId) || null,
      images: data.images.split(/\r?\n/).map(image => image.trim()).filter(Boolean)
    } }, product.id ? "商品已更新" : "商品已发布", () => dialog.close());
  }, product.id ? "保存" : "发布");
}

function adminProductList(products, categories) {
  const publish = node("div", { class: "actions admin-toolbar" },
    button("发布商品", () => productEditor(categories)));
  const cards = products.map(product => {
    const active = product.status === "ON_SALE";
    return node("article", { class: "card row" },
      node("div", {}, text("h2", product.name),
        text("p", `${active ? "在售" : "已下架"} · ${product.categoryName || "未分类"} · ${money.format(product.price)} · 库存 ${product.stock} · 销量 ${product.sales}`, "muted")),
      node("div", { class: "actions" }, button("编辑 / 补货", () => productEditor(categories, product), "secondary"),
        button(active ? "下架" : "上架", () => {
          void mutate("productStatus", { id: product.id, status: active ? "OFF_SHELF" : "ON_SALE" }, active ? "商品已下架" : "商品已上架");
        }, active ? "danger" : "")));
  });
  return node("div", {}, publish, cards.length ? node("div", { class: "list" }, ...cards) : empty("没有符合条件的商品"));
}

function categoryEditor(categories, category = {}) {
  const fields = [formField("input", "分类名称", { name: "name", maxlength: 50, required: "" }, category.name)];
  if (!category.children?.length) fields.push(categoryField(categories, "上级分类", category.parentId,
    { rootsOnly: true, exclude: category.id, empty: "设为一级分类" }));
  manageForm(category.id ? "编辑分类" : "新增分类", fields, (data, dialog) => {
    void mutate("saveCategory", {
      id: category.id, category: { name: data.name, parentId: Number(data.categoryId) || null }
    }, "分类已保存", () => dialog.close());
  });
}

function adminCategoryList(categories) {
  const list = node("div", { class: "list" });
  for (const group of categories) {
    for (const category of [group, ...group.children]) {
      list.append(node("article", { class: "card row" },
        text("h2", category.parentId ? `　${category.name}` : category.name),
        node("div", { class: "actions" }, button("编辑分类", () => categoryEditor(categories, category), "secondary"),
          button("删除分类", () => {
            if (window.confirm(`确认删除分类「${category.name}」？`)) {
              void mutate("deleteCategory", { id: category.id }, "分类已删除");
            }
          }, "danger"))));
    }
  }
  return node("div", {}, node("div", { class: "actions admin-toolbar" },
    button("新增分类", () => categoryEditor(categories))), list);
}

function adminCustomerList(customers) {
  if (!customers.length) return empty("暂无注册客户");
  return node("div", { class: "list" }, ...customers.map(customer => node("article", { class: "card row" },
    node("div", {}, text("h2", customer.username),
      text("p", `${customer.phone} · ${customer.defaultTradePlace}`, "muted"),
      text("p", `订单 ${customer.orderCount} 笔 · 累计 ${money.format(customer.totalSpent)}`, "muted")),
    button("查看购买记录", () => void showCustomerOrders(customer), "secondary"))));
}

async function showCustomerOrders(customer) {
  const version = refreshing;
  try {
    const orders = await send("adminCustomerOrders", { id: customer.id });
    if (version !== refreshing || document.querySelector("dialog[open]")) return;
    const title = `${customer.username}的购买记录`;
    const dialog = node("dialog", { class: "card detail", "aria-label": title }, text("h2", title), orderList(orders, false));
    dialog.append(button("关闭购买记录", () => dialog.close(), "secondary"));
    dialog.addEventListener("close", () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
  } catch (error) {
    await showRequestError(error);
  }
}

function passwordForm() {
  const form = node("form", { class: "login" },
    formField("input", "当前密码", { name: "oldPassword", type: "password", required: "", autocomplete: "current-password" }),
    formField("input", "新密码", { name: "newPassword", type: "password", required: "", minlength: 6, maxlength: 32, autocomplete: "new-password" }),
    node("button", { type: "submit" }, "保存新密码"));
  form.addEventListener("submit", event => {
    event.preventDefault();
    void mutate("changePassword", Object.fromEntries(new FormData(form)), "密码已更新，请重新登录", () => {
      session.seller = null;
      page = "orders";
    });
  });
  return form;
}