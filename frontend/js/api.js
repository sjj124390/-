/**
 * api.js —— 接口适配层（系统集成 · 朱江）
 *
 * 说明：
 *   前端页面最初按「/product/*、/order/*、/seller/*」的约定开发，
 *   后端（孙俊杰）实际提供的是「/products、/intents、/admin/*」资源化接口 + 存储过程。
 *   本文件是两层之间的唯一适配点：页面只调用 window.Api，不感知后端路径差异，
 *   从而在不改动任何后端代码、不重写页面的前提下完成系统整合。
 *
 * 后端原始接口（http://localhost:8080/api）：
 *   GET  /products/current                     当前在售/冻结商品
 *   POST /intents                              提交购买意向（返回口令码）
 *   POST /admin/login                          卖家登录
 *   PUT  /admin/password                       修改密码
 *   GET  /admin/products                       商品历史（全部状态）
 *   POST /admin/products                       创建草稿
 *   POST /admin/products/{id}/publish          上架
 *   POST /admin/products/{id}/withdraw         下架
 *   GET  /admin/products/{id}/intents          商品意向名单
 *   POST /admin/products/{id}/freeze           选中意向并冻结（生成交易）
 *   GET  /admin/trades/pending                 待处理交易
 *   POST /admin/trades/{id}/finish             结束交易（成功/失败）
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
  // 后端为单卖家静态账号，未下发会话令牌；
  // 前端仍保留 token 机制用于页面级登录守卫（requireLogin）。
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
   * 后端存储过程 SIGNAL 出来的英文错误码文案 → 面向用户的中文提示
   * 存储过程属于数据库层，无法直接给出产品化文案，统一在此翻译
   */
  var SP_MESSAGES = {
    'Another product is active': '已有在售或冻结中的商品，请先下架或完成交易后再发布',
    'Product cannot be published': '商品当前状态不允许上架',
    'Product is not available': '商品已被冻结或已售出，暂时无法提交购买意向',
    'Product cannot be frozen': '商品当前状态不允许冻结',
    'Intent is unavailable or expired': '该购买意向已过期或已被处理',
    'Trade is not pending': '该交易已结束，请刷新后重试',
    'Initialize seller first': '卖家账号尚未初始化，请重启后端服务',
    'Invalid code or expiration': '口令码无效或已过期'
  };

  function translateMessage(message) {
    return SP_MESSAGES[message] || message;
  }

  /**
   * 统一请求方法
   * @param {string} path 以 / 开头的接口路径
   * @param {object} [options] { method, body, params, allowCodes }
   *   allowCodes: 允许出现的业务码（如后端「暂无在售商品」=1001），命中时返回 null 而不是抛错
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

    var headers = {};
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
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
              var message = translateMessage((payload && payload.message) || '请求失败，请稍后重试');

              // 空态业务码：不作为错误抛出
              if (options.allowCodes && options.allowCodes.indexOf(code) >= 0) {
                return null;
              }

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

  // ---------- 状态映射（后端枚举 → 页面数字状态） ----------
  // 商品：0 在售 / 1 冻结（含已下架，可在后台恢复）/ 2 已售（历史）
  var PRODUCT_STATUS = { ON_SALE: 0, FROZEN: 1, OFF_SHELF: 1, SOLD: 2, DRAFT: 2 };
  // 意向：0 待交易（含已选定） / 1 交易成功 / 2 交易失败
  var INTENT_STATUS = { PENDING: 0, SELECTED: 0, COMPLETED: 1, CANCELLED: 2, EXPIRED: 2 };

  function mapProduct(p) {
    if (!p) return null;
    return {
      id: p.id,
      name: p.name,
      description: p.description,
      imageUrl: p.imageUrl,
      price: p.price,
      status: PRODUCT_STATUS[p.status] != null ? PRODUCT_STATUS[p.status] : 2,
      rawStatus: p.status,
      createTime: p.createdAt
    };
  }

  function mapIntent(it, product) {
    return {
      id: it.id,
      productId: it.productId,
      productName: product ? product.name : '',
      buyerName: it.buyerName,
      buyerPhone: it.contact,
      remark: it.note || '',
      status: INTENT_STATUS[it.status] != null ? INTENT_STATUS[it.status] : 0,
      rawStatus: it.status,
      createTime: it.createdAt
    };
  }

  // 意向索引：标记交易结果时需要由订单号反查商品
  var intentIndex = {};

  function listHistory() {
    return request('/admin/products').then(function (list) {
      return list || [];
    });
  }

  function findProduct(productId) {
    return listHistory().then(function (list) {
      for (var i = 0; i < list.length; i++) {
        if (String(list[i].id) === String(productId)) return list[i];
      }
      return null;
    });
  }

  function pendingTrade(productId, intentId) {
    return request('/admin/trades/pending').then(function (list) {
      list = list || [];
      for (var i = 0; i < list.length; i++) {
        if (
          String(list[i].productId) === String(productId) &&
          String(list[i].intentId) === String(intentId)
        ) {
          return list[i];
        }
      }
      return null;
    });
  }

  /** 交易成功后自动生成同款商品并上架（“卖掉一个再制作一个”） */
  function republishClone(productId) {
    return findProduct(productId).then(function (p) {
      if (!p) return null;
      return request('/admin/products', {
        method: 'POST',
        body: {
          name: p.name,
          description: p.description,
          imageUrl: p.imageUrl,
          price: p.price
        }
      }).then(function (draft) {
        if (!draft || !draft.id) return null;
        return request('/admin/products/' + draft.id + '/publish', { method: 'POST' });
      });
    });
  }

  // ---------- 业务接口（页面只认这一层） ----------
  var Api = {
    // ===== 前台 =====
    getProductDetail: function (id) {
      // 单商品体验店：不传 id 时取当前商品；传 id 时优先匹配，其次回落到历史商品
      return request('/products/current', { allowCodes: [1001] })
        .catch(function () {
          return null;
        })
        .then(function (cur) {
          if (id === null || id === undefined || id === '') return mapProduct(cur);
          if (cur && String(cur.id) === String(id)) return mapProduct(cur);
          return listHistory().then(function (list) {
            for (var i = 0; i < list.length; i++) {
              if (String(list[i].id) === String(id)) return mapProduct(list[i]);
            }
            return mapProduct(cur) || mapProduct(list[0] || null);
          });
        });
    },

    submitOrder: function (body) {
      var productId = body.productId;
      var payload = {
        productId: productId,
        buyerName: body.buyerName,
        contact: body.buyerPhone,
        note: body.remark || null
      };
      return request('/intents', { method: 'POST', body: payload }).then(function (data) {
        var intentId = data && data.intentId;
        var code = data && (data.rawCode || data.code);
        if (!intentId) return { code: code };

        // 基线需求：提交购买意向后商品立即冻结，其他买家无法重复提交
        return request('/admin/products/' + productId + '/freeze', {
          method: 'POST',
          body: { intentId: intentId }
        })
          .then(function () {
            return { code: code };
          })
          .catch(function () {
            // 冻结失败不影响意向提交结果，由卖家在后台处理
            return { code: code };
          });
      });
    },

    // ===== 卖家后台 =====
    sellerLogin: function (body) {
      return request('/admin/login', {
        method: 'POST',
        body: { username: body.username, password: body.password }
      }).then(function (seller) {
        var token = 't-' + (seller && seller.id ? seller.id : 1) + '-' + Date.now();
        setToken(token);
        setSellerName((seller && seller.username) || body.username);
        return { token: token, username: (seller && seller.username) || body.username };
      });
    },

    getProductList: function () {
      return Promise.all([
        request('/products/current', { allowCodes: [1001] }).catch(function () {
          return null;
        }),
        listHistory().catch(function () {
          return [];
        })
      ]).then(function (res) {
        var cur = res[0];
        var history = res[1] || [];
        var merged = {};
        history.forEach(function (p) {
          // 草稿（DRAFT）尚未发布，不进入后台商品列表
          if (p.status === 'DRAFT') return;
          merged[p.id] = mapProduct(p);
        });
        if (cur) merged[cur.id] = mapProduct(cur);
        var out = [];
        Object.keys(merged).forEach(function (k) {
          out.push(merged[k]);
        });
        out.sort(function (a, b) {
          return Number(b.id) - Number(a.id);
        });
        return out;
      });
    },

    addProduct: function (body) {
      // 先做业务前置校验：已有在售/冻结商品时直接提示，避免先落草稿再发布失败留下脏数据
      return request('/products/current', { allowCodes: [1001] })
        .catch(function () {
          return null;
        })
        .then(function (active) {
          if (active) {
            throw new ApiError('已有在售或冻结中的商品，请先下架或完成交易后再发布', 4009);
          }
          return request('/admin/products', {
            method: 'POST',
            body: {
              name: body.name,
              description: body.description,
              // 后端要求图片地址必填，未填写时使用本地占位图
              imageUrl: body.imageUrl || 'img/product-default.svg',
              price: body.price
            }
          }).then(function (draft) {
            if (!draft || !draft.id) throw new ApiError('商品创建失败', 500);
            return request('/admin/products/' + draft.id + '/publish', { method: 'POST' });
          });
        });
    },

    // 手动冻结 = 下架（下架后买家无法提交意向，可在后台恢复上架）
    freezeProduct: function (id) {
      return request('/admin/products/' + id + '/withdraw', { method: 'POST' });
    },

    // 解除冻结：冻结中（有未完成交易）→ 标记交易失败恢复在售；已下架 → 直接重新上架
    restoreProduct: function (id) {
      return findProduct(id).then(function (p) {
        if (!p) throw new ApiError('商品不存在', 4004);
        if (p.status === 'OFF_SHELF' || p.status === 'DRAFT') {
          return request('/admin/products/' + id + '/publish', { method: 'POST' });
        }
        // FROZEN：结束未完成交易（失败）以恢复在售
        return request('/admin/trades/pending')
          .then(function (list) {
            var hit = null;
            (list || []).forEach(function (t) {
              if (String(t.productId) === String(id)) hit = t;
            });
            if (!hit) throw new ApiError('该商品没有待处理交易，请先下架再上架', 4005);
            return request('/admin/trades/' + hit.id + '/finish', {
              method: 'POST',
              body: { productId: Number(id), success: false, reason: '卖家手动解除冻结' }
            });
          });
      });
    },

    getOrderList: function () {
      return listHistory().then(function (products) {
        if (!products.length) return [];
        return Promise.all(
          products.map(function (p) {
            return request('/admin/products/' + p.id + '/intents', { allowCodes: [1001] })
              .catch(function () {
                return [];
              })
              .then(function (list) {
                return (list || []).map(function (it) {
                  return mapIntent(it, p);
                });
              });
          })
        ).then(function (groups) {
          var all = [];
          groups.forEach(function (g) {
            all = all.concat(g);
          });
          all.sort(function (a, b) {
            return Number(b.id) - Number(a.id);
          });
          intentIndex = {};
          all.forEach(function (o) {
            intentIndex[o.id] = o;
          });
          return all;
        });
      });
    },

    updateOrderStatus: function (body) {
      var intentId = Number(body.orderId);
      var success = Number(body.status) === 1;

      var lookup = intentIndex[intentId]
        ? Promise.resolve(intentIndex[intentId])
        : Api.getOrderList().then(function () {
            return intentIndex[intentId] || null;
          });

      return lookup.then(function (info) {
        if (!info) throw new ApiError('订单不存在或已被处理', 4004);
        var productId = Number(info.productId);

        return pendingTrade(productId, intentId)
          .then(function (trade) {
            if (trade) return trade.id;
            // 尚未冻结：先选中该意向冻结并生成交易
            return request('/admin/products/' + productId + '/freeze', {
              method: 'POST',
              body: { intentId: intentId }
            });
          })
          .then(function (tradeId) {
            return request('/admin/trades/' + tradeId + '/finish', {
              method: 'POST',
              body: {
                productId: productId,
                success: success,
                reason: success ? null : '卖家标记交易失败'
              }
            });
          })
          .then(function (res) {
            if (!success) return res;
            // 成交后自动补货：同款新商品创建并上架
            return republishClone(productId)
              .catch(function () {
                return null;
              })
              .then(function () {
                return res;
              });
          });
      });
    },

    updatePassword: function (body) {
      return request('/admin/password', {
        method: 'PUT',
        body: {
          username: body.username,
          oldPassword: body.oldPassword,
          newPassword: body.newPassword,
          confirmPassword: body.newPassword
        }
      });
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
