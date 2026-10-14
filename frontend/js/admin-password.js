/**
 * admin-password.js —— 密码修改页交互
 * 接口：POST /api/seller/updatePassword
 * body：{ username, oldPassword, newPassword, confirmPassword }
 */
(function () {
  if (!window.requireLogin()) return;

  renderAdminSidebar('password');
  renderAdminTopbar('密码修改');
  document.getElementById('seller-avatar').textContent =
    (window.Auth.getSellerName() || '卖').charAt(0);

  var form = document.getElementById('pw-form');
  var userEl = document.getElementById('username');
  var oldEl = document.getElementById('oldPassword');
  var newEl = document.getElementById('newPassword');
  var confirmEl = document.getElementById('confirmPassword');
  var errEl = document.getElementById('pw-error');
  var btn = document.getElementById('pw-submit');

  userEl.value = window.Auth.getSellerName() || '';

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    errEl.textContent = '';

    var username = userEl.value.trim();
    var oldPassword = oldEl.value;
    var newPassword = newEl.value;
    var confirmPassword = confirmEl.value;

    if (!username) { errEl.textContent = '请输入账号'; return; }
    if (!oldPassword) { errEl.textContent = '请输入原密码'; return; }
    if (!newPassword) { errEl.textContent = '请输入新密码'; return; }
    if (newPassword.length < 6) { errEl.textContent = '新密码长度至少 6 位'; return; }
    if (newPassword !== confirmPassword) { errEl.textContent = '两次输入的新密码不一致'; return; }

    btn.disabled = true;
    btn.textContent = '提交中…';

    Api.updatePassword({
      username: username,
      oldPassword: oldPassword,
      newPassword: newPassword,
      confirmPassword: confirmPassword
    })
      .then(function () {
        toastSuccess('密码已更新，请使用新密码重新登录');
        window.Auth.clearAuth();
        setTimeout(function () {
          location.href = 'login.html';
        }, 1200);
      })
      .catch(function (err) {
        btn.disabled = false;
        btn.textContent = '确认修改';
        errEl.textContent = err.message || '修改失败';
      });
  });
})();
