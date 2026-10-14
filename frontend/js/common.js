/**
 * common.js —— 通用工具（组员 A 负责）
 * 包含：Toast 提示、确认弹窗、登录守卫、格式化、后台侧边栏渲染、常量定义
 */
(function () {
  // ========================= 常量 =========================
  // 商品状态（对接字段 data.status / product.status）
  window.PRODUCT_STATUS = {
    0: { text: '在售', cls: 'badge-on-sale' },
    1: { text: '冻结', cls: 'badge-frozen' },
    2: { text: '已售', cls: 'badge-sold' }
  };
  // 订单状态
  window.ORDER_STATUS = {
    0: { text: '待交易', cls: 'badge-pending' },
    1: { text: '交易成功', cls: 'badge-success' },
    2: { text: '交易失败', cls: 'badge-failed' }
  };

  // ========================= 通用工具 =========================
  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatPrice(v) {
    if (v === null || v === undefined || v === '') return '¥ --';
    var n = Number(v);
    if (isNaN(n)) return '¥ ' + v;
    return '¥ ' + n.toFixed(2);
  }

  function formatDateTime(v) {
    if (!v) return '--';
    var d = new Date(v);
    if (isNaN(d.getTime())) return String(v);
    function p(x) {
      return x < 10 ? '0' + x : '' + x;
    }
    return (
      d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
      ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
    );
  }

  function getQuery(name) {
    return new URLSearchParams(location.search).get(name);
  }

  // 兼容后端可能的数组包装方式，统一取出列表
  function normalizeList(data) {
    if (Array.isArray(data)) return data;
    if (!data) return [];
    if (Array.isArray(data.list)) return data.list;
    if (Array.isArray(data.records)) return data.records;
    if (Array.isArray(data.data)) return data.data;
    if (Array.isArray(data.items)) return data.items;
    return [];
  }

  window.Utils = {
    escapeHtml: escapeHtml,
    formatPrice: formatPrice,
    formatDateTime: formatDateTime,
    getQuery: getQuery,
    normalizeList: normalizeList
  };

  // ========================= Toast 提示 =========================
  function ensureToastBox() {
    var box = document.getElementById('toast-container');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toast-container';
      box.className = 'toast-container';
      document.body.appendChild(box);
    }
    return box;
  }

  function toast(message, type) {
    if (!message) return;
    var box = ensureToastBox();
    var item = document.createElement('div');
    item.className = 'toast toast-' + (type || 'info');
    item.textContent = message;
    box.appendChild(item);
    requestAnimationFrame(function () {
      item.classList.add('show');
    });
    setTimeout(function () {
      item.classList.remove('show');
      setTimeout(function () {
        if (item.parentNode) item.parentNode.removeChild(item);
      }, 300);
    }, 2600);
  }

  window.toast = toast;
  window.toastSuccess = function (m) { toast(m, 'success'); };
  window.toastError = function (m) { toast(m, 'error'); };

  // 统一处理接口异常：直接把后端 message 弹出，登录失效则回登录页
  window.handleApiError = function (err) {
    if (err && err.code === 401) {
      toast(err.message || '登录已失效，请重新登录', 'error');
      setTimeout(function () { location.href = 'login.html'; }, 800);
      return;
    }
    toast((err && err.message) || '操作失败', 'error');
  };

  // ========================= 确认弹窗 =========================
  window.confirmDialog = function (message, title) {
    return new Promise(function (resolve) {
      var mask = document.createElement('div');
      mask.className = 'modal-mask';
      mask.innerHTML =
        '<div class="modal">' +
        '  <div class="modal-title">' + escapeHtml(title || '操作确认') + '</div>' +
        '  <div class="modal-body">' + escapeHtml(message) + '</div>' +
        '  <div class="modal-footer">' +
        '    <button type="button" class="btn btn-ghost" data-act="cancel">取消</button>' +
        '    <button type="button" class="btn btn-primary" data-act="ok">确定</button>' +
        '  </div>' +
        '</div>';
      document.body.appendChild(mask);
      function close(val) {
        if (mask.parentNode) mask.parentNode.removeChild(mask);
        resolve(val);
      }
      mask.addEventListener('click', function (e) {
        var act = e.target.getAttribute && e.target.getAttribute('data-act');
        if (act === 'ok') close(true);
        else if (act === 'cancel' || e.target === mask) close(false);
      });
    });
  };

  // ========================= 登录守卫 =========================
  // 后台页面在初始化时调用；未登录直接跳转登录页
  window.requireLogin = function () {
    if (!window.Auth.isLogin()) {
      location.href = 'login.html';
      return false;
    }
    return true;
  };

  window.logout = function () {
    window.Auth.clearAuth();
    location.href = 'login.html';
  };

  // ========================= 后台侧边栏 =========================
  var ADMIN_MENU = [
    { key: 'products', text: '商品管理', href: 'admin-products.html' },
    { key: 'orders', text: '意向订单', href: 'admin-orders.html' },
    { key: 'password', text: '密码修改', href: 'admin-password.html' }
  ];

  window.renderAdminSidebar = function (activeKey) {
    var el = document.getElementById('admin-sidebar');
    if (!el) return;
    var links = ADMIN_MENU.map(function (m) {
      var active = m.key === activeKey ? ' active' : '';
      return '<a class="sidebar-link' + active + '" href="' + m.href + '">' + m.text + '</a>';
    }).join('');
    el.innerHTML =
      '<div class="sidebar-brand">' +
      '  <span class="brand-mark">购</span>' +
      '  <span class="brand-text">在线购物 · 后台</span>' +
      '</div>' +
      '<nav class="sidebar-nav">' + links + '</nav>' +
      '<div class="sidebar-foot">卖家管理后台</div>';
  };

  // 顶部栏展示当前卖家 + 退出按钮
  window.renderAdminTopbar = function (title) {
    var nameEl = document.getElementById('seller-name');
    if (nameEl) nameEl.textContent = window.Auth.getSellerName() || '卖家';
    var titleEl = document.getElementById('page-title');
    if (titleEl && title) titleEl.textContent = title;
  };

  // ========================= 状态渲染片段 =========================
  // 加载 / 空数据 / 错误 三种占位
  window.renderLoading = function (wrap, text) {
    wrap.innerHTML = '<div class="state-box"><div class="spinner"></div><p>' + (text || '加载中…') + '</p></div>';
  };
  window.renderEmpty = function (wrap, text) {
    wrap.innerHTML = '<div class="state-box"><p class="state-text">' + (text || '暂无数据') + '</p></div>';
  };
  window.renderError = function (wrap, text) {
    wrap.innerHTML = '<div class="state-box"><p class="state-text state-error">' + (text || '加载失败') + '</p></div>';
  };
})();
