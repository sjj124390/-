/**
 * purchase.js —— 购买信息填写页交互
 * 接口：POST /api/order/submit
 * body：{ productId, buyerName, buyerPhone, remark }
 */
(function () {
  var esc = Utils.escapeHtml;
  var productId = Utils.getQuery('id');

  var summary = document.getElementById('summary');
  var form = document.getElementById('purchase-form');
  var nameEl = document.getElementById('buyerName');
  var phoneEl = document.getElementById('buyerPhone');
  var remarkEl = document.getElementById('remark');
  var submitBtn = document.getElementById('submit-btn');
  var errorEl = document.getElementById('form-error');

  function normalize(p) {
    p = p || {};
    return {
      id: p.id != null ? p.id : p.productId,
      name: p.name || p.productName || '商品',
      description: p.description || p.desc || '',
      imageUrl: p.imageUrl || p.image_url || p.image || '',
      price: p.price
    };
  }

  function renderSummary(p) {
    if (!p) {
      summary.innerHTML = '<div class="state-box" style="padding:24px 0"><p class="state-text">商品信息暂不可用</p></div>';
      return;
    }
    var media = p.imageUrl
      ? '<img src="' + esc(p.imageUrl) + '" alt="' + esc(p.name) + '" onerror="this.style.display=\'none\'">'
      : '暂无图片';
    summary.innerHTML =
      '<div class="sum-media">' + media + '</div>' +
      '<div class="sum-name">' + esc(p.name) + '</div>' +
      '<div class="sum-price">' + Utils.formatPrice(p.price) + '</div>';
  }

  // 必填项为空时禁用提交按钮
  function refreshSubmitState() {
    submitBtn.disabled = !(nameEl.value.trim() && phoneEl.value.trim());
  }
  nameEl.addEventListener('input', refreshSubmitState);
  phoneEl.addEventListener('input', refreshSubmitState);

  // 载入商品简要信息
  if (productId) {
    Api.getProductDetail()
      .then(function (data) {
        var p = normalize(data);
        if (p.id != null && p.id !== '') productId = p.id;
        renderSummary(p);
      })
      .catch(function () {
        renderSummary(null);
      });
  } else {
    renderSummary(null);
  }

  function showSuccess(code) {
    var codeBlock = code
      ? '<p style="margin:14px 0 6px;color:var(--text-sub)">请妥善保存您的查询口令码，卖家核对时需出示：</p>' +
        '<div style="font-family:monospace;font-size:20px;font-weight:700;letter-spacing:2px;' +
        'background:var(--primary-light);color:var(--primary-dark);padding:14px;border-radius:10px;' +
        'word-break:break-all">' + esc(code) + '</div>'
      : '';
    document.getElementById('page-main').innerHTML =
      '<div class="container">' +
      '  <div class="card card-pad" style="max-width:560px;margin:40px auto;text-align:center">' +
      '    <div style="width:56px;height:56px;border-radius:50%;background:var(--success-light);' +
      '      color:var(--success);display:flex;align-items:center;justify-content:center;margin:0 auto 16px">' +
      '      <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor"' +
      '        stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>' +
      '    </div>' +
      '    <h2 style="margin-bottom:8px">购买意向已提交</h2>' +
      '    <p style="color:var(--text-sub)">卖家将尽快联系您，请保持电话畅通。</p>' +
      codeBlock +
      '    <button class="btn btn-primary" style="margin-top:22px" ' +
      '      onclick="location.href=\'index.html\'">返回商品详情</button>' +
      '  </div>' +
      '</div>';
    window.scrollTo(0, 0);
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    errorEl.textContent = '';

    var buyerName = nameEl.value.trim();
    var buyerPhone = phoneEl.value.trim();
    var remark = remarkEl.value.trim();

    if (!buyerName) { errorEl.textContent = '请填写联系人姓名'; return; }
    if (!buyerPhone) { errorEl.textContent = '请填写联系电话'; return; }
    if (!/^1\d{10}$/.test(buyerPhone)) { errorEl.textContent = '请填写正确的 11 位手机号'; return; }
    if (productId === null || productId === undefined || productId === '') {
      errorEl.textContent = '商品信息缺失，请返回商品详情页重试';
      return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = '提交中…';

    Api.submitOrder({
      productId: isNaN(Number(productId)) ? productId : Number(productId),
      buyerName: buyerName,
      buyerPhone: buyerPhone,
      remark: remark
    })
      .then(function (data) {
        var code = data && (data.code || data.queryCode || data.intentCode);
        if (code) {
          showSuccess(code);
        } else {
          toastSuccess('购买意向已提交，卖家将联系您');
          setTimeout(function () {
            location.href = 'index.html';
          }, 1200);
        }
      })
      .catch(function (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = '提交购买';
        refreshSubmitState();
        errorEl.textContent = err.message || '提交失败';
        handleApiError(err);
      });
  });
})();
