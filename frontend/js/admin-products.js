/**
 * admin-products.js —— 商品管理页交互
 * 接口：
 *   GET  /api/product/list          商品列表（含当前 + 历史）
 *   POST /api/product/add           发布新商品  body: {name, description, imageUrl, price}
 *   POST /api/product/freeze?id=    手动冻结
 *   POST /api/product/restore?id=   解除冻结
 */
(function () {
  if (!window.requireLogin()) return;

  renderAdminSidebar('products');
  renderAdminTopbar('商品管理');
  document.getElementById('seller-avatar').textContent =
    (window.Auth.getSellerName() || '卖').charAt(0);

  var esc = Utils.escapeHtml;
  var currentBox = document.getElementById('current-box');
  var historyBox = document.getElementById('history-box');

  var IMG_ICON =
    '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"' +
    ' stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="3" y="4" width="18" height="16" rx="2"/>' +
    '<circle cx="8.5" cy="9.5" r="1.5"/>' +
    '<path d="M21 16l-5-5-4 4-2-2-4 4"/></svg>';

  function normalize(p) {
    p = p || {};
    return {
      id: p.id != null ? p.id : p.productId,
      name: p.name || p.productName || '未命名商品',
      description: p.description || p.desc || '',
      imageUrl: p.imageUrl || p.image_url || p.image || '',
      price: p.price,
      status: Number(p.status),
      createTime: p.createTime || p.createdAt || p.created_at || p.updateTime
    };
  }

  function mediaHtml(p) {
    return p.imageUrl
      ? '<img class="cp-img" src="' + esc(p.imageUrl) + '" alt="' + esc(p.name) + '">'
      : IMG_ICON;
  }

  // ---------------- 渲染：当前商品 ----------------
  function renderCurrent(p) {
    if (!p) {
      renderEmpty(currentBox, '当前没有在售或冻结的商品，点击右上角「发布新商品」开始吧');
      return;
    }
    var st = PRODUCT_STATUS[p.status] || { text: '未知', cls: 'badge-sold' };
    var action =
      p.status === 0
        ? '<button class="btn btn-ghost btn-sm" data-act="freeze" data-id="' + esc(p.id) + '">手动冻结</button>'
        : '<button class="btn btn-outline-primary btn-sm" data-act="restore" data-id="' + esc(p.id) + '">解除冻结</button>';

    currentBox.innerHTML =
      '<div class="current-product">' +
      '  <div class="cp-media">' + mediaHtml(p) + '</div>' +
      '  <div class="cp-info">' +
      '    <div class="cp-name">' + esc(p.name) +
      '      <span class="badge ' + st.cls + '">' + st.text + '</span></div>' +
      '    <div class="cp-price">' + Utils.formatPrice(p.price) + '</div>' +
      '    <div class="cp-desc">' + (esc(p.description) || '暂无描述') + '</div>' +
      '    <div class="cp-actions">' + action + '</div>' +
      '  </div>' +
      '</div>';

    // 图片加载失败时回退为占位图标
    var img = currentBox.querySelector('.cp-img');
    if (img) {
      img.addEventListener('error', function () {
        img.parentNode.innerHTML = IMG_ICON;
      });
    }

    currentBox.querySelectorAll('[data-act]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        doAction(this.getAttribute('data-act'), this.getAttribute('data-id'));
      });
    });
  }

  function doAction(type, id) {
    var isFreeze = type === 'freeze';
    confirmDialog(
      isFreeze
        ? '确定要手动冻结该商品吗？冻结后买家将无法提交购买意向。'
        : '确定要解除冻结吗？解除后商品将恢复为在售状态。',
      isFreeze ? '冻结商品' : '解除冻结'
    ).then(function (ok) {
      if (!ok) return;
      var req = isFreeze ? Api.freezeProduct(id) : Api.restoreProduct(id);
      req
        .then(function () {
          toastSuccess(isFreeze ? '商品已冻结' : '商品已解除冻结');
          load();
        })
        .catch(handleApiError);
    });
  }

  // ---------------- 渲染：历史商品 ----------------
  function renderHistory(list) {
    if (!list.length) {
      renderEmpty(historyBox, '暂无历史商品');
      return;
    }
    var rows = list
      .map(function (p) {
        var st = PRODUCT_STATUS[p.status] || { text: '未知', cls: 'badge-sold' };
        return (
          '<tr>' +
          '<td>' + esc(p.name) + '</td>' +
          '<td>' + Utils.formatPrice(p.price) + '</td>' +
          '<td><span class="badge ' + st.cls + '">' + st.text + '</span></td>' +
          '<td>' + Utils.formatDateTime(p.createTime) + '</td>' +
          '</tr>'
        );
      })
      .join('');
    historyBox.innerHTML =
      '<table class="table">' +
      '<thead><tr><th>商品名称</th><th>价格</th><th>状态</th><th>创建时间</th></tr></thead>' +
      '<tbody>' + rows + '</tbody></table>';
  }

  // ---------------- 加载列表 ----------------
  function load() {
    renderLoading(currentBox, '加载中…');
    renderLoading(historyBox, '加载中…');
    Api.getProductList()
      .then(function (data) {
        var list = Utils.normalizeList(data).map(normalize);
        var current = null;
        var history = [];
        list.forEach(function (p) {
          if (p.status === 0 || p.status === 1) {
            if (!current) current = p;
          } else if (p.status === 2) {
            history.push(p);
          }
        });
        renderCurrent(current);
        renderHistory(history);
      })
      .catch(function (err) {
        renderError(currentBox, err.message || '加载失败');
        renderError(historyBox, err.message || '加载失败');
      });
  }

  // ---------------- 发布新商品弹窗 ----------------
  function openAddModal() {
    var mask = document.createElement('div');
    mask.className = 'modal-mask';
    mask.innerHTML =
      '<div class="modal" style="max-width:500px">' +
      '  <div class="modal-title">发布新商品</div>' +
      '  <form id="add-form" novalidate>' +
      '    <div class="form-item"><label>商品名称<span class="required">*</span></label>' +
      '      <input name="name" type="text" maxlength="100" placeholder="请输入商品名称"></div>' +
      '    <div class="form-item"><label>商品价格（元）<span class="required">*</span></label>' +
      '      <input name="price" type="number" min="0.01" step="0.01" placeholder="例如 99.00"></div>' +
      '    <div class="form-item"><label>商品图片地址</label>' +
      '      <input name="imageUrl" type="text" placeholder="https://...（选填）"></div>' +
      '    <div class="form-item"><label>商品描述<span class="required">*</span></label>' +
      '      <textarea name="description" maxlength="500" placeholder="请输入商品描述"></textarea></div>' +
      '    <div class="form-error" id="add-error"></div>' +
      '    <div class="modal-footer">' +
      '      <button type="button" class="btn btn-ghost" data-act="cancel">取消</button>' +
      '      <button type="submit" class="btn btn-primary" id="add-submit">确认发布</button>' +
      '    </div>' +
      '  </form>' +
      '</div>';
    document.body.appendChild(mask);

    var formEl = mask.querySelector('#add-form');
    var errEl = mask.querySelector('#add-error');
    var submitEl = mask.querySelector('#add-submit');

    function close() {
      if (mask.parentNode) mask.parentNode.removeChild(mask);
    }

    mask.addEventListener('click', function (e) {
      var act = e.target.getAttribute && e.target.getAttribute('data-act');
      if (act === 'cancel' || e.target === mask) close();
    });

    formEl.addEventListener('submit', function (e) {
      e.preventDefault();
      errEl.textContent = '';
      var name = formEl.name.value.trim();
      var price = formEl.price.value.trim();
      var imageUrl = formEl.imageUrl.value.trim();
      var description = formEl.description.value.trim();

      if (!name) { errEl.textContent = '请填写商品名称'; return; }
      if (!price || isNaN(Number(price)) || Number(price) <= 0) {
        errEl.textContent = '请填写正确的商品价格';
        return;
      }
      if (!description) { errEl.textContent = '请填写商品描述'; return; }

      submitEl.disabled = true;
      submitEl.textContent = '发布中…';

      Api.addProduct({
        name: name,
        price: Number(price),
        imageUrl: imageUrl,
        description: description
      })
        .then(function () {
          toastSuccess('商品发布成功');
          close();
          load();
        })
        .catch(function (err) {
          submitEl.disabled = false;
          submitEl.textContent = '确认发布';
          errEl.textContent = err.message || '发布失败';
        });
    });
  }

  document.getElementById('btn-add').addEventListener('click', openAddModal);
  document.getElementById('btn-refresh').addEventListener('click', load);

  load();
})();
