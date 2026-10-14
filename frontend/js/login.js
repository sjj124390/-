/**
 * login.js —— 卖家登录页交互
 * 接口：POST /api/seller/login
 * body：{ username, password }
 */
(function () {
  var form = document.getElementById('login-form');
  var userEl = document.getElementById('username');
  var passEl = document.getElementById('password');
  var errEl = document.getElementById('login-error');
  var btn = document.getElementById('login-btn');

  // 已登录则直接进入后台
  if (window.Auth.isLogin()) {
    location.href = 'admin-products.html';
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    errEl.textContent = '';

    var username = userEl.value.trim();
    var password = passEl.value;

    if (!username) { errEl.textContent = '请输入账号'; return; }
    if (!password) { errEl.textContent = '请输入密码'; return; }

    btn.disabled = true;
    btn.textContent = '登录中…';

    Api.sellerLogin({ username: username, password: password })
      .then(function (data) {
        var token = (data && (data.token || data.accessToken)) || 'seller-token';
        window.Auth.setToken(token);
        window.Auth.setSellerName((data && (data.username || data.name)) || username);
        toastSuccess('登录成功');
        setTimeout(function () {
          location.href = 'admin-products.html';
        }, 500);
      })
      .catch(function (err) {
        btn.disabled = false;
        btn.textContent = '登录';
        errEl.textContent = err.message || '登录失败';
      });
  });
})();
