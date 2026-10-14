/**
 * product-detail.js —— 商品详情页交互
 * 接口：GET /api/product/detail
 * 关键字段：data.status  0 在售(按钮可点) / 1 冻结 / 2 已售(按钮置灰)
 */
(function () {
  var wrap = document.getElementById('detail-wrap');
  var esc = Utils.escapeHtml;

  var IMG_ICON =
    '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor"' +
    ' stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="3" y="4" width="18" height="16" rx="2"/>' +
    '<circle cx="8.5" cy="9.5" r="1.5"/>' +
    '<path d="M21 16l-5-5-4 4-2-2-4 4"/></svg>';

  function placeholder(show) {
    return (
      '<div class="media-placeholder"' + (show ? '' : ' style="display:none"') + '>' +
      '<span class="ph-icon">' + IMG_ICON + '</span><span>暂无商品图片</span></div>'
    );
  }

  function normalize(p) {
    p = p || {};
    return {
      id: p.id != null ? p.id : p.productId,
      name: p.name || p.productName || '未命名商品',
      description: p.description || p.desc || '',
      imageUrl: p.imageUrl || p.image_url || p.image || '',
      price: p.price,
      status: Number(p.status)
    };
  }

  function priceHtml(price) {
    if (price === null || price === undefined || price === '') return '<small>¥</small>--';
    var n = Number(price);
    return '<small>¥</small>' + (isNaN(n) ? esc(price) : n.toFixed(2));
  }

  function buyTip(status) {
    if (status === 0) return '商品在售，填写信息后即可提交购买意向';
    if (status === 1) return '商品已被其他买家锁定，暂时无法购买';
    if (status === 2) return '商品已售出';
    return '';
  }

  function render(p) {
    var st = PRODUCT_STATUS[p.status] || { text: '未知状态', cls: 'badge-sold' };
    var buyable = p.status === 0;
    var btnText = buyable ? '立即购买' : p.status === 1 ? '商品已被锁定' : '商品已售出';

    var media = p.imageUrl
      ? '<img class="pd-img" src="' + esc(p.imageUrl) + '" alt="' + esc(p.name) + '">' + placeholder(false)
      : placeholder(true);

    wrap.innerHTML =
      '<div class="card product-detail">' +
      '  <div class="product-media">' + media + '</div>' +
      '  <div class="product-info">' +
      '    <div class="status-row"><span class="badge ' + st.cls + '">' + st.text + '</span></div>' +
      '    <h1 class="product-name">' + esc(p.name) + '</h1>' +
      '    <div class="product-price">' + priceHtml(p.price) + '</div>' +
      '    <div class="product-desc-title">商品描述</div>' +
      '    <div class="product-desc">' + (esc(p.description) || '暂无描述') + '</div>' +
      '    <div class="product-buy-bar">' +
      '      <button id="buy-btn" class="btn btn-primary"' + (buyable ? '' : ' disabled') + '>' + btnText + '</button>' +
      '      <span class="buy-tip">' + buyTip(p.status) + '</span>' +
      '    </div>' +
      '  </div>' +
      '</div>';

    // 图片加载失败时回退到占位图
    var img = wrap.querySelector('.pd-img');
    if (img) {
      img.addEventListener('error', function () {
        img.style.display = 'none';
        var ph = wrap.querySelector('.media-placeholder');
        if (ph) ph.style.display = 'flex';
      });
    }

    var buyBtn = document.getElementById('buy-btn');
    if (buyBtn && buyable) {
      buyBtn.addEventListener('click', function () {
        if (p.id === null || p.id === undefined || p.id === '') {
          toastError('商品信息异常，暂时无法购买');
          return;
        }
        location.href = 'purchase.html?id=' + encodeURIComponent(p.id);
      });
    }
  }

  renderLoading(wrap, '商品加载中…');
  Api.getProductDetail()
    .then(function (data) {
      render(normalize(data));
    })
    .catch(function (err) {
      renderError(wrap, err.message || '商品加载失败');
    });
})();
