/**
 * admin-orders.js —— 意向订单页交互
 * 接口：
 *   GET  /api/order/list               订单列表
 *   POST /api/order/updateStatus       标记交易结果  body: {orderId, status}
 *   status：1 成功 / 2 失败
 */
(function () {
  if (!window.requireLogin()) return;

  renderAdminSidebar('orders');
  renderAdminTopbar('意向订单');
  document.getElementById('seller-avatar').textContent =
    (window.Auth.getSellerName() || '卖').charAt(0);

  var esc = Utils.escapeHtml;
  var box = document.getElementById('orders-box');

  function normalize(o) {
    o = o || {};
    return {
      id: o.id != null ? o.id : o.orderId,
      productName: o.productName || (o.product && o.product.name) || o.name || '—',
      buyerName: o.buyerName || o.name || '—',
      buyerPhone: o.buyerPhone || o.phone || o.contact || '—',
      remark: o.remark || o.note || '',
      status: Number(o.status),
      createTime: o.createTime || o.createdAt || o.created_at || o.submitTime
    };
  }

  function render(list) {
    if (!list.length) {
      renderEmpty(box, '暂无购买意向订单');
      return;
    }
    var rows = list
      .map(function (o) {
        var st = ORDER_STATUS[o.status] || { text: '未知', cls: 'badge-pending' };
        var ops;
        // 原代码：if (o.status ===0)
// 修改为：
if (o.status === 0) {
  ops =
    '<div class="table-actions">' +
    '<button class="btn btn-success btn-sm" data-act="success" data-id="' + esc(o.id) + '">标记成功</button>' +
    '<button class="btn btn-danger btn-sm" data-act="failed" data-id="' + esc(o.id) + '">标记失败</button>' +
    '</div>';
} else {
  ops = '<span style="color: var(--text-light)">—</span>';
}

        return (
          '<tr>' +
          '<td>' + esc(o.productName) + '</td>' +
          '<td>' + esc(o.buyerName) + '</td>' +
          '<td>' + esc(o.buyerPhone) + '</td>' +
          '<td>' + (esc(o.remark) || '—') + '</td>' +
          '<td>' + Utils.formatDateTime(o.createTime) + '</td>' +
          '<td><span class="badge ' + st.cls + '">' + st.text + '</span></td>' +
          '<td>' + ops + '</td>' +
          '</tr>'
        );
      })
      .join('');

    box.innerHTML =
      '<table class="table">' +
      '<thead><tr>' +
      '<th>商品</th><th>购买人</th><th>联系电话</th><th>备注</th>' +
      '<th>提交时间</th><th>状态</th><th>操作</th>' +
      '</tr></thead>' +
      '<tbody>' + rows + '</tbody></table>';

    box.querySelectorAll('[data-act]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var act = this.getAttribute('data-act');
        doAction(act, this.getAttribute('data-id'));
      });
    });
  }

  function doAction(act, id) {
    var isSuccess = act === 'success';
    var status = isSuccess ? 1 : 2;
    confirmDialog(
      isSuccess
        ? '确认将该订单标记为「交易成功」？成功后原商品将下架，并自动生成同款新商品上架。'
        : '确认将该订单标记为「交易失败」？失败后商品将恢复在售状态。',
      '标记交易结果'
    ).then(function (ok) {
      if (!ok) return;
      Api.updateOrderStatus({
        orderId: isNaN(Number(id)) ? id : Number(id),
        status: status
      })
        .then(function () {
          toastSuccess('操作成功');
          load();
        })
        .catch(handleApiError);
    });
  }

  function load() {
    renderLoading(box, '加载中…');
    Api.getOrderList()
      .then(function (data) {
        render(Utils.normalizeList(data).map(normalize));
      })
      .catch(function (err) {
        renderError(box, err.message || '加载失败');
      });
  }

  document.getElementById('btn-refresh').addEventListener('click', load);
  load();
})();
