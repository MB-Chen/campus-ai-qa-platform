/* ============================================================
   xservice 在线客服 - 浮窗 SDK（原生 JS，无依赖）
   学习 WeKnora embed 安全模型：publish token(长期) → exchange → session token(短期)

   安全模式（推荐，带 channel）：
     <script src="https://your-host/chat/widget.js"
       data-channel="1"
       data-token-endpoint="http://api-host/im/embed/issue"
       data-position="bottom-right"
       data-primary-color="#07C05F"
       data-title="在线客服"></script>
     说明：SDK 用 channelId 调 issue 换取约 30 分钟的短期 session token，并自动
     续期（80% 生命周期）。域名白名单+限流在后端校验。tokenEndpoint 可省略，
     缺省时由 widget.js 同源推导为 /im/embed/issue。跨域嵌入自动加 sandbox。

   兼容旧模式（不带 channel，直接 apiUrl）：
     <script src=".../widget.js" data-api-url="http://localhost:8080"></script>

   编程式：
     XServiceChat.init({ channel, tokenEndpoint, position, primaryColor, title, apiUrl, wsUrl });
     XServiceChat.open() | close() | destroy()
   ============================================================ */
(function (global) {
  'use strict';

  var POSITIONS = ['bottom-right', 'bottom-left', 'top-right', 'top-left'];
  var DEFAULT_POSITION = 'bottom-right';
  var DEFAULT_COLOR = '#07C05F';
  var DEFAULT_TITLE = '在线客服';
  var SESSION_REFRESH_RATIO = 0.8; // 80% 生命周期续期

  var ICON_CHAT =
    '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>';
  var ICON_CLOSE =
    '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>';
  var ICON_PHONE =
    '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6.62 10.79a15.05 15.05 0 006.59 6.59l2.2-2.2a1 1 0 011.05-.24 11.36 11.36 0 003.56.57 1 1 0 011 1V20a1 1 0 01-1 1A17 17 0 013 4a1 1 0 011-1h3.5a1 1 0 011 1c0 1.25.2 2.45.57 3.56a1 1 0 01-.24 1.05l-2.21 2.18z"/></svg>';

  var state = {
    inited: false,
    options: null,
    config: null,
    launcher: null,
    window: null,
    iframe: null,
    open: false,
    sessionToken: '',
    sessionExpiresIn: 0,
    refreshTimer: null,
  };

  function resolveBaseUrl() {
    var scripts = document.getElementsByTagName('script');
    for (var i = scripts.length - 1; i >= 0; i--) {
      var src = scripts[i].src || '';
      var idx = src.indexOf('/widget.js');
      if (idx > -1) return src.substring(0, idx);
    }
    return '';
  }

  function normalizePosition(pos) {
    if (!pos || POSITIONS.indexOf(pos) < 0) return DEFAULT_POSITION;
    return pos;
  }

  /**
   * 初始化 SDK
   * @param {Object} opts
   */
  function init(opts) {
    if (state.inited) return;
    state.inited = true;
    state.options = Object.assign(
      {
        apiUrl: '',
        wsUrl: '',
        chatPageUrl: '',
        channel: '',
        publishToken: '',
        tokenEndpoint: '',
        position: DEFAULT_POSITION,
        primaryColor: DEFAULT_COLOR,
        title: DEFAULT_TITLE,
      },
      opts || {}
    );

    var baseUrl = resolveBaseUrl();
    if (!state.options.chatPageUrl) {
      state.options.chatPageUrl = baseUrl + '/index.html';
    }

    _injectStyles(baseUrl);
    _createLauncher();
    _loadConfig();

    // 嵌入渠道模式：用 channelId 换 session token（即使面板未打开，提前准备）
    if (state.options.channel) {
      _loadSessionToken().catch(function () { /* 打开时再试 */ });
    }
  }

  // 加载访客端入口配置（电话/工单等），用于外层浮窗头部
  function _loadConfig() {
    var apiUrl = state.options.apiUrl || '';
    if (!apiUrl) return;
    try {
      fetch(apiUrl.replace(/\/$/, '') + '/im/config/visitor')
        .then(function (r) { return r.json(); })
        .then(function (res) {
          state.config = (res && res.data) || res || {};
          // 若浮窗已打开，补画电话入口
          if (state.window) _renderHeaderExtras();
        })
        .catch(function () { /* 忽略，用默认 */ });
    } catch (e) { /* 忽略 */ }
  }

  // 在外层 header 渲染电话入口（标题与关闭按钮之间）
  function _renderHeaderExtras() {
    var header = state.window.querySelector('.xs-w-header');
    if (!header) return;
    // 先清掉旧的电话区，避免重复
    var old = header.querySelector('.xs-w-actions');
    if (old) old.remove();
    var cfg = state.config || {};
    if (!(cfg.phoneShow == 1 && cfg.phone)) return;
    var actions = document.createElement('div');
    actions.className = 'xs-w-actions';
    var phoneA = document.createElement('a');
    phoneA.className = 'xs-w-phone';
    phoneA.href = 'tel:' + cfg.phone;
    phoneA.title = cfg.phone;
    phoneA.innerHTML = ICON_PHONE + '<span>' + cfg.phone + '</span>';
    actions.appendChild(phoneA);
    // 插在关闭按钮之前
    var closeBtn = header.querySelector('.xs-w-close');
    header.insertBefore(actions, closeBtn);
  }

  function _injectStyles(baseUrl) {
    if (!document.getElementById('xservice-chat-css')) {
      var link = document.createElement('link');
      link.id = 'xservice-chat-css';
      link.rel = 'stylesheet';
      link.href = baseUrl + '/chat.css';
      document.head.appendChild(link);
    }
  }

  // ============ 安全：issue 换取短期 token（用 channelId） ============
  function _loadSessionToken() {
    var opt = state.options;
    if (!opt.channel) return Promise.resolve(''); // 旧模式无需 token
    // 默认走 issue（channelId 直接换）；若调用方显式指定 tokenEndpoint，则尊重其路径（兼容旧 exchange）
    var url = opt.tokenEndpoint;
    if (!url && opt.apiUrl) url = opt.apiUrl.replace(/\/$/, '') + '/im/embed/issue';
    if (!url) {
      // 兜底：由 widget.js 所在同源后端推导
      url = resolveBaseUrl() + '/im/embed/issue';
    }
    var body = { channel: opt.channel };

    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
      credentials: 'include',
    })
      .then(function (res) {
        if (!res.ok) throw new Error('issue HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        var d = (data && data.data) || data || {};
        var tok = d.token || '';
        if (!tok) throw new Error('issue 未返回 token');
        state.sessionToken = tok;
        state.sessionExpiresIn = Number(d.expiresIn) || 1800;
        _scheduleRefresh(state.sessionExpiresIn);
        return tok;
      });
  }

  function _scheduleRefresh(expiresInSec) {
    if (state.refreshTimer) clearTimeout(state.refreshTimer);
    var ttl = Number(expiresInSec) > 0 ? Number(expiresInSec) : 1800;
    var delayMs = Math.max(Math.floor(ttl * SESSION_REFRESH_RATIO), 30) * 1000;
    state.refreshTimer = setTimeout(function () {
      _loadSessionToken().catch(function () { /* 下次交互重试 */ });
    }, delayMs);
  }

  function _createLauncher() {
    var btn = document.createElement('button');
    btn.className = 'xs-launcher';
    btn.innerHTML = ICON_CHAT;
    btn.title = state.options.title;
    btn.style.background = state.options.primaryColor || DEFAULT_COLOR;
    var pos = normalizePosition(state.options.position);
    if (pos.indexOf('left') >= 0) { btn.style.right = 'auto'; btn.style.left = '24px'; }
    if (pos.indexOf('top') >= 0) { btn.style.bottom = 'auto'; btn.style.top = '24px'; }
    btn.addEventListener('click', function () {
      if (state.open) _closeWindow();
      else _openWindow();
    });
    document.body.appendChild(btn);
    state.launcher = btn;
  }

  // 浮窗展开高度（与 chat.css .xs-widget-window 的 height 保持一致）
  var WINDOW_HEIGHT = 680;

  function _openWindow() {
    if (state.window) {
      // 已创建过：从收起态展开（先显示再设置目标高度，触发 transition）
      state.window.style.display = 'flex';
      void state.window.offsetHeight;
      state.window.style.height = Math.min(WINDOW_HEIGHT, window.innerHeight - 120) + 'px';
      state.window.style.opacity = '1';
      state.open = true;
      return;
    }
    var w = document.createElement('div');
    w.className = 'xs-widget-window';
    w.style.height = Math.min(WINDOW_HEIGHT, window.innerHeight - 120) + 'px';
    var pos = normalizePosition(state.options.position);
    if (pos.indexOf('left') >= 0) { w.style.right = 'auto'; w.style.left = '24px'; }
    if (pos.indexOf('top') >= 0) { w.style.bottom = 'auto'; w.style.top = '88px'; }

    var header = document.createElement('div');
    header.className = 'xs-w-header';
    header.style.background = state.options.primaryColor || DEFAULT_COLOR;
    var title = document.createElement('span');
    title.className = 'xs-w-title';
    title.textContent = state.options.title;
    var closeBtn = document.createElement('button');
    closeBtn.className = 'xs-w-close';
    closeBtn.innerHTML = ICON_CLOSE;
    closeBtn.addEventListener('click', _closeWindow);
    header.appendChild(title);
    header.appendChild(closeBtn);

    var iframe = document.createElement('iframe');
    _setIframeSrc(iframe);
    _applySandbox(iframe);
    iframe.setAttribute('allow', 'microphone; camera');
    iframe.setAttribute('title', state.options.title);

    w.appendChild(header);
    w.appendChild(iframe);
    document.body.appendChild(w);

    state.window = w;
    state.iframe = iframe;
    state.open = true;
    // config 已加载则补画电话入口
    _renderHeaderExtras();

    // iframe 加载后，通过 postMessage 注入 session token（安全模式）
    iframe.addEventListener('load', function () {
      _provideTokenToIframe();
    });
  }

  function _provideTokenToIframe() {
    if (!state.iframe || !state.iframe.contentWindow) return;
    if (!state.options.channel) return;
    var tok = state.sessionToken;
    if (!tok) {
      // 尚未换取，立即换一次再发
      _loadSessionToken()
        .then(function (t) { _postToken(t); })
        .catch(function () { /* 子页面将保持等待 */ });
      return;
    }
    _postToken(tok);
  }

  function _postToken(tok) {
    if (!state.iframe || !state.iframe.contentWindow || !tok) return;
    var targetOrigin;
    try {
      targetOrigin = new URL(state.options.chatPageUrl, global.location.href).origin;
    } catch (e) {
      targetOrigin = '*';
    }
    state.iframe.contentWindow.postMessage(
      { source: 'xservice-widget', type: 'session_token', token: tok },
      targetOrigin
    );
  }

  function _setIframeSrc(iframe) {
    var opt = state.options;
    var params = ['source=2', 'embed=1', 'bare=1'];
    if (opt.apiUrl) params.push('apiUrl=' + encodeURIComponent(opt.apiUrl));
    if (opt.wsUrl) params.push('wsUrl=' + encodeURIComponent(opt.wsUrl));
    if (opt.channel) params.push('channel=' + encodeURIComponent(opt.channel));
    // 安全模式：session token 通过 postMessage 传递，不放 URL，避免长期暴露
    iframe.src = opt.chatPageUrl + '?' + params.join('&');
  }

  // 跨域嵌入自动加 sandbox；同源若配置强制则也加
  function _applySandbox(iframe) {
    try {
      var hostOrigin = global.location ? global.location.origin : '';
      var chatOrigin = new URL(state.options.chatPageUrl, global.location.href).origin;
      if (hostOrigin && chatOrigin && hostOrigin !== chatOrigin) {
        iframe.setAttribute('sandbox', 'allow-scripts allow-forms allow-same-origin allow-popups');
      }
    } catch (e) { /* ignore */ }
  }

  function _closeWindow() {
    if (state.window) {
      // 先收缩高度（触发过渡），过渡结束后再隐藏，保证收起有动画
      state.window.style.height = '0';
      state.window.style.opacity = '0';
      var w = state.window;
      setTimeout(function () {
        if (!state.open && w) w.style.display = 'none';
      }, 250);
    }
    state.open = false;
  }
  // 窗口尺寸变化时，若已展开则同步更新高度，避免超出视口
  global.addEventListener('resize', function () {
    if (state.open && state.window) {
      state.window.style.height = Math.min(WINDOW_HEIGHT, window.innerHeight - 120) + 'px';
    }
  });

  function destroy() {
    if (state.refreshTimer) clearTimeout(state.refreshTimer);
    if (state.launcher && state.launcher.parentNode) state.launcher.parentNode.removeChild(state.launcher);
    if (state.window && state.window.parentNode) state.window.parentNode.removeChild(state.window);
    state.inited = false;
    state.launcher = null;
    state.window = null;
    state.iframe = null;
    state.sessionToken = '';
  }

  // 暴露 API
  global.XServiceChat = {
    init: init,
    open: _openWindow,
    close: _closeWindow,
    destroy: destroy,
    // 对外提供当前 session token，供聊天页通过 postMessage 取用
    getSessionToken: function () { return state.sessionToken; },
  };

  // data-* 自动初始化
  (function autoInit() {
    var scripts = document.getElementsByTagName('script');
    for (var i = scripts.length - 1; i >= 0; i--) {
      var s = scripts[i];
      if (s.src && s.src.indexOf('/widget.js') > -1) {
        var channel = s.getAttribute('data-channel');
        var tokenEndpoint = s.getAttribute('data-token-endpoint');
        var apiUrl = s.getAttribute('data-api-url');
        if (channel && tokenEndpoint) {
          init({
            channel: channel,
            tokenEndpoint: tokenEndpoint,
            position: s.getAttribute('data-position'),
            primaryColor: s.getAttribute('data-primary-color'),
            title: s.getAttribute('data-title'),
          });
        } else if (apiUrl) {
          // 旧模式兼容
          init({ apiUrl: apiUrl });
        }
        break;
      }
    }
  })();
})(window);
