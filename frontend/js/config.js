/**
 * config.js —— 全局配置（组员 A 负责）
 * 联调环境：后端运行在 8080 端口，跨域已由后端配好
 */
window.APP_CONFIG = {
  // 所有接口的基础地址，页面统一通过 Api 调用，无需在各页面重复拼接
  API_BASE_URL: 'http://localhost:8080/api'
};
