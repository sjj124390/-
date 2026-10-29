/**
 * api.js —— 统一接口封装（组员 A 负责）
 *
 * 对接约定（组员 B 提供）：
 *   1. 所有接口返回 { code, message, data }，code === 200 表示成功；
 *   2. 其他 code 直接把 message 交给页面弹窗提示，前端不自行编造错误文案；
 *   3. 基础地址：http://localhost:8080/api
 */
(function () {
  var BASE_URL =
    (window.APP_CONFIG && window.APP_CONFIG.API_BASE_URL) || 'http://localhost:8080/api';

  var TOKEN_KEY = 'seller_token';
  var NAME_KEY = 'seller_name';

  /** 业务异常：携带 code 与 message，供页面统一提示 */
  function ApiError(message, code) {
    this.name = 'ApiError';
    this.message = message;
    this.code = code;
  }
  ApiError.prototype = Object.create(Error.prototype);

  // ---------- 登录态存取 ----------
  function getToken() {
    return localStorage.getItem(TOKEN_KEY) || '';
  }
  function setToken(token) {
    if (token) localStorage.setItem(TOKEN_KEY, token);
  }
  function setSellerName(name) {
    if (name) localStorage.setItem(NAME_KEY, name);
  }
  function getSellerName() {
    return localStorage.getItem(NAME_KEY) || '';
  }
  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(NAME_KEY);
  }
  function isLogin() {
    return !!getToken();
  }

  /**
   * 统一请求方法
   * @param {string} path  以 / 开头的接口路径
   * @param {object} [options] { method, body, params }
   * @returns {Promise<any>} 成功时返回 payload.data
   */
  function request(path, options) {
    options = options || {};
    var method = options.method || 'GET';

    var url = BASE_URL + path;
    if (options.params) {
      var qs = new URLSearchParams(options.params).toString();
      if (qs) url += (url.indexOf('?') >= 0 ? '&' : '?') + qs;
    }

    var headers = { 'Content-Type': 'application/json' };
    var token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    var init = { method: method, headers: headers };
    if (options.body !== undefined) {
      init.body = JSON.stringify(options.body);
    }

    return fetch(url, init)
      .then(function (response) {
        return response
          .json()
          .catch(function () {
            throw new ApiError('服务返回数据格式异常（HTTP ' + response.status + '）', response.status);
          })
          .then(function (payload) {
            if (!payload || payload.code !== 200) {
              var code = payload ? payload.code : response.status;
              var message = (payload && payload.message) || '请求失败，请稍后重试';
              // 登录失效：清除本地登录态并自动跳转登录页
              if (code === 401) {
                var wasLoggedIn = !!getToken();
                clearAuth();
                if (
                  wasLoggedIn &&
                  typeof window !== 'undefined' &&
                  window.location &&
                  !/login\.html(\?|#|$)/.test(window.location.pathname + window.location.search)
                ) {
                  window.location.href = 'login.html';
                }
              }
              throw new ApiError(message, code);
            }
            return payload.data;
          });
      })
      .catch(function (err) {
        if (err instanceof ApiError) throw err;
        throw new ApiError('网络异常，请确认后端服务（8080 端口）已启动', -1);
      });
  }

  // ---------- 业务接口 ----------
  var Api = {
    // 前台
    // id 可选：不传时由后端返回当前商品（单商品体验店）
    getProductDetail: function (id) {
      var opts = {};
      if (id !== null && id !== undefined && id !== '') {
        opts.params = { id: id };
      }
      return request('/product/detail', opts);
    },
    submitOrder: function (body) {
      return request('/order/submit', { method: 'POST', body: body });
    },
    offlineProduct: function(id){
      return request('/product/offline', { method: 'POST', params: { id: id } });
    },
queryIntent: function(body){
  return request('/order/query',{method:'POST',body:body})
},

    // 卖家后台
    sellerLogin: function (body) {
      return request('/seller/login', { method: 'POST', body: body });
    },
    getProductList: function () {
      return request('/product/list');
    },
    addProduct: function (body) {
      return request('/product/add', { method: 'POST', body: body });
    },
    freezeProduct: function (id) {
      return request('/product/freeze', { method: 'POST', params: { id: id } });
    },
    restoreProduct: function (id) {
      return request('/product/restore', { method: 'POST', params: { id: id } });
    },
    getOrderList: function () {
      return request('/order/list');
    },
    updateOrderStatus: function (body) {
      return request('/order/updateStatus', { method: 'POST', body: body });
    },
    updatePassword: function (body) {
      return request('/seller/updatePassword', { method: 'POST', body: body });
    }
  };

  window.ApiError = ApiError;
  window.Api = Api;
  window.Auth = {
    getToken: getToken,
    setToken: setToken,
    setSellerName: setSellerName,
    getSellerName: getSellerName,
    clearAuth: clearAuth,
    isLogin: isLogin
  };
})();
