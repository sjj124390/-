(function(){
  var form = document.getElementById('query-form');
  var codeEl = document.getElementById('queryCode');
  var errEl = document.getElementById('query-error');
  var resultBox = document.getElementById('result-box');

  form.addEventListener('submit',function(e){
    e.preventDefault();
    errEl.textContent = '';
    resultBox.innerHTML = '';
    var code = codeEl.value.trim();
    if(!code){
      errEl.textContent="请输入查询口令";
      return;
    }
    Api.queryIntent({queryCode:code})
    .then(function(data){
      var st = ORDER_STATUS[data.status];
      // 迭代2核心：失效口令提示
      if(data.status ===3){
        resultBox.innerHTML = `<div class="state-box"><p class="state-text state-error">口令码已失效</p></div>`;
        return;
      }
      resultBox.innerHTML=`
        <div style="margin-top:12px;border-top:1px solid var(--border);padding-top:16px">
          <p>商品名称：${Utils.escapeHtml(data.productName)}</p>
          <p>买家姓名：${Utils.escapeHtml(data.buyerName)}</p>
          <p>状态：<span class="badge ${st.cls}">${st.text}</span></p>
          <p>备注：${Utils.escapeHtml(data.remark||'无')}</p>
        </div>
      `
    })
    .catch(function(err){
      resultBox.innerHTML = `<div class="state-box"><p class="state-text state-error">口令码已失效</p></div>`;
      handleApiError(err);
    })
  })
})()
