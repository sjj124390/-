"use strict";

window.ShopApi = (() => {
  const location = window.location;
  const defaultBase = location.protocol === "file:" ? "http://127.0.0.1:8080/api" : "/api";
  const baseUrl = (new URLSearchParams(location.search).get("api") || defaultBase).replace(/\/$/, "");
  const storageKey = "group10-requirement-b-demo-v1";
  const steps = ["PLACED", "CONFIRMED", "PREPARED", "SHIPPED", "COMPLETED"];
  const seed = [
    { id: 1, name: "城市通勤折叠车", description: "轻便折叠，适合短途通勤。", price: 699, stock: 4, imageUrl: "img/product-bike.svg" },
    { id: 2, name: "便携车灯", description: "适用于夜间骑行，可充电。", price: 59, stock: 12 },
    { id: 3, name: "骑行水壶", description: "弹盖设计，容量 600 mL。", price: 29.9, stock: 15 }
  ];
  const cents = price => Math.round(Number(price) * 100);
  const roundTotal = items => items.reduce((sum, item) => sum + cents(item.price) * item.quantity, 0) / 100;

  function readDemo() {
    const saved = localStorage.getItem(storageKey);
    return saved ? JSON.parse(saved) : {
      products: structuredClone(seed), favorites: [], cart: [], orders: [], nextId: 1
    };
  }

  function actionsFor(status, role) {
    const actions = [];
    const index = steps.indexOf(status);
    if (role === "seller" && index >= 0 && index < steps.length - 1) {
      actions.push(steps[index + 1]);
    }
    if (["PLACED", "CONFIRMED", "PREPARED"].includes(status) || (role === "seller" && status === "SHIPPED")) {
      actions.push("CANCELLED");
    }
    return actions;
  }

  const demo = {
    async products({ keyword, page, size }) {
      const matches = readDemo().products.filter(product => product.stock > 0 &&
        (product.name.includes(keyword) || product.description.includes(keyword)));
      return { records: matches.slice((page - 1) * size, page * size), total: matches.length };
    },
    async categories() { return []; },
    async product({ id }) { return readDemo().products.find(product => product.id === id); },
    async favorites() {
      const data = readDemo();
      return data.products.filter(product => data.favorites.includes(product.id));
    },
    async cart() {
      const data = readDemo();
      return data.cart.map(item => ({
        ...data.products.find(product => product.id === item.productId),
        productId: item.productId,
        quantity: item.quantity
      }));
    },
    async orders({ role }) {
      return readDemo().orders.slice().reverse().map(order => ({
        ...order, allowedActions: actionsFor(order.status, role)
      }));
    },
    async favorite({ id, enabled }) {
      const data = readDemo();
      const ids = new Set(data.favorites);
      if (enabled) ids.add(id);
      else ids.delete(id);
      data.favorites = [...ids];
      localStorage.setItem(storageKey, JSON.stringify(data));
    },
    async saveCart({ id, quantity, add }) {
      const data = readDemo();
      const product = data.products.find(item => item.id === id);
      const current = data.cart.find(item => item.productId === id);
      const nextQuantity = quantity + (add ? current?.quantity ?? 0 : 0);
      if (!product || !Number.isSafeInteger(nextQuantity) || nextQuantity < 1 || nextQuantity > Math.min(product.stock, 999)) {
        throw new Error("商品库存不足，请调整数量");
      }
      if (current) current.quantity = nextQuantity;
      else data.cart.push({ productId: id, quantity: nextQuantity });
      localStorage.setItem(storageKey, JSON.stringify(data));
    },
    async removeCart({ id }) {
      const data = readDemo();
      data.cart = data.cart.filter(item => item.productId !== id);
      localStorage.setItem(storageKey, JSON.stringify(data));
    },
    async moveToFavorites({ ids }) {
      const data = readDemo();
      if (!ids.length || ids.some(id => !data.cart.some(item => item.productId === id))) {
        throw new Error("购物车已变动，请刷新后重新选择商品");
      }
      data.favorites = [...new Set([...data.favorites, ...ids])];
      data.cart = data.cart.filter(item => !ids.includes(item.productId));
      localStorage.setItem(storageKey, JSON.stringify(data));
    },
    async checkout({ ids }) {
      const data = readDemo();
      const requested = new Set(ids);
      const selected = data.cart.filter(item => requested.has(item.productId));
      if (!selected.length || selected.length !== requested.size) {
        throw new Error("购物车已变动，请刷新后重新选择商品");
      }
      const items = selected.map(({ productId, quantity }) => {
        const product = data.products.find(item => item.id === productId);
        if (product.stock < quantity) throw new Error(`${product.name}库存不足`);
        product.stock -= quantity;
        return { productId, name: product.name, price: product.price, quantity };
      });
      const order = {
        id: data.nextId++, buyerName: "演示买家", status: "PLACED",
        totalAmount: roundTotal(items), items, createdAt: new Date().toISOString()
      };
      data.orders.push(order);
      data.cart = data.cart.filter(item => !requested.has(item.productId));
      localStorage.setItem(storageKey, JSON.stringify(data));
      return order;
    },
    async changeOrder({ id, status, role }) {
      const data = readDemo();
      const order = data.orders.find(item => item.id === id);
      if (!order || !actionsFor(order.status, role).includes(status)) {
        throw new Error("订单状态已变动，请刷新后重试");
      }
      if (status === "CANCELLED") {
        for (const item of order.items) {
          data.products.find(product => product.id === item.productId).stock += item.quantity;
        }
      }
      order.status = status;
      localStorage.setItem(storageKey, JSON.stringify(data));
    }
  };

  async function request(path, { token, method = "GET", body } = {}) {
    const headers = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    let response;
    try {
      response = await fetch(baseUrl + path, {
        method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(8000)
      });
    } catch {
      throw new Error("无法连接后端，请检查服务地址与跨域配置");
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.code !== 200) {
      const error = new Error(payload?.message || `接口请求失败（HTTP ${response.status}）`);
      error.status = response.status;
      throw error;
    }
    return payload.data;
  }

  const live = {
    async login({ role, username, password }) {
      const path = role === "buyer" ? "/customers/login" : "/admin/login";
      const data = await request(path, { method: "POST", body: { username, password } });
      if (!data?.token) throw new Error("登录接口未返回可用于联调的 token");
      return data.token;
    },
    async register({ username, password, phone, defaultTradePlace }) {
      const data = await request("/customers/register", {
        method: "POST", body: { username, password, phone, defaultTradePlace }
      });
      return data.token;
    },
    products({ keyword, categoryId, page, size }) {
      const query = new URLSearchParams({ keyword, page, size });
      if (categoryId) query.set("categoryId", categoryId);
      return request(`/products?${query}`);
    },
    categories: () => request("/categories"),
    product: ({ id }) => request(`/products/${encodeURIComponent(id)}`),
    profile: ({ token }) => request("/customers/me", { token }),
    adminProducts({ token, keyword, categoryId, page, size }) {
      const query = new URLSearchParams({ keyword, page, size });
      if (categoryId) query.set("categoryId", categoryId);
      return request(`/admin/products?${query}`, { token });
    },
    adminCategories: ({ token }) => request("/admin/categories", { token }),
    saveProduct: ({ id, product, token }) => request(id ? `/admin/products/${id}` : "/admin/products", {
      token, method: id ? "PUT" : "POST", body: product
    }),
    productStatus: ({ id, status, token }) => request(`/admin/products/${id}/status`, {
      token, method: "PUT", body: { status }
    }),
    saveCategory: ({ id, category, token }) => request(id ? `/admin/categories/${id}` : "/admin/categories", {
      token, method: id ? "PUT" : "POST", body: category
    }),
    deleteCategory: ({ id, token }) => request(`/admin/categories/${id}`, { token, method: "DELETE" }),
    adminCustomers: ({ token }) => request("/admin/customers", { token }),
    adminCustomerOrders: ({ id, token }) => request(`/admin/customers/${id}/orders`, { token }),
    changePassword: ({ oldPassword, newPassword, token }) => request("/admin/password", {
      token, method: "PUT", body: { oldPassword, newPassword }
    }),
    favorites: ({ token }) => request("/customers/me/favorites", { token }),
    cart: ({ token }) => request("/customers/me/cart/items", { token }),
    orders: ({ token, role }) => request(role === "buyer" ? "/customers/me/orders" : "/admin/orders", { token }),
    favorite: ({ id, enabled, token }) => request(`/customers/me/favorites/${encodeURIComponent(id)}`, {
      token, method: enabled ? "PUT" : "DELETE"
    }),
    saveCart: ({ id, quantity, add, token }) => add
      ? request("/customers/me/cart/items", { token, method: "POST", body: { productId: id, quantity } })
      : request(`/customers/me/cart/items/${encodeURIComponent(id)}`, { token, method: "PUT", body: { quantity } }),
    removeCart: ({ id, token }) => request(`/customers/me/cart/items/${encodeURIComponent(id)}`, { token, method: "DELETE" }),
    moveToFavorites: ({ ids, token }) => request("/customers/me/cart/to-favorites", {
      token, method: "POST", body: { productIds: ids }
    }),
    checkout: ({ ids, token }) => request("/customers/me/orders", { token, method: "POST", body: { productIds: ids } }),
    changeOrder: ({ id, status, role, token }) => {
      const scope = role === "buyer" ? "/customers/me/orders" : "/admin/orders";
      const path = `${scope}/${encodeURIComponent(id)}`;
      if (status === "CANCELLED") return request(`${path}/cancel`, { token, method: "POST" });
      return request(`${path}/status`, { token, method: "PUT", body: { status } });
    }
  };

  return { demo, live, roundTotal };
})();
