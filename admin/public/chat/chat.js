/* ============================================================
   xservice 在线客服 - 访客端聊天核心（原生 JS，无依赖）
   提供 window.XServiceChatCore，供全屏页 / iframe 页 共用
   ============================================================ */
(function (global) {
  'use strict';

  // ---------- 工具 ----------
  function uuid() {
    return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = (Math.random() * 16) | 0;
      var v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  function qs(selector, root) {
    return (root || document).querySelector(selector);
  }

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (k === 'class') node.className = attrs[k];
        else if (k === 'html') node.innerHTML = attrs[k];
        else if (k.indexOf('on') === 0) node.addEventListener(k.slice(2), attrs[k]);
        else node.setAttribute(k, attrs[k]);
      });
    }
    if (children) {
      (Array.isArray(children) ? children : [children]).forEach(function (c) {
        if (c == null) return;
        node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
      });
    }
    return node;
  }

  function fmtTime(t) {
    if (!t) return '';
    // 兼容两种格式：yyyy-MM-dd HH:mm:ss（空格分隔）/ ISO 8601（T 分隔，带毫秒）
    var s = String(t).replace('T', ' ');
    var p = s.split(' ');
    if (p.length > 1) {
      // 取 HH:mm:ss 后截到 HH:mm
      var time = p[1].split('.')[0]; // 去掉毫秒 .092958
      return time.length >= 5 ? time.substring(0, 5) : time;
    }
    return s;
  }

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // SVG 图标
  var ICON = {
    chat:
      '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>',
    image:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>',
    send:
      '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3.4 20.4l17.45-7.48a1 1 0 000-1.84L3.4 3.6a.993.993 0 00-1.39.91L2 9.12c0 .5.37.93.87.99L17 12 2.87 13.88c-.5.07-.87.5-.87 1l.01 4.61c0 .71.73 1.2 1.39.91z"/></svg>',
    close: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>',
    phone:
      '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6.62 10.79a15.05 15.05 0 006.59 6.59l2.2-2.2a1 1 0 011.05-.24 11.36 11.36 0 003.56.57 1 1 0 011 1V20a1 1 0 01-1 1A17 17 0 013 4a1 1 0 011-1h3.5a1 1 0 011 1c0 1.25.2 2.45.57 3.56a1 1 0 01-.24 1.05l-2.21 2.18z"/></svg>',
    ticket:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7v3a2 2 0 010 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 010-4V7a2 2 0 00-2-2H5a2 2 0 00-2 2z"/><path d="M13 5v14"/></svg>',
    emoji:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/></svg>',
    mic: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z"/><path d="M19 10v2a7 7 0 01-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>',
    file: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>',
    soundOn:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 010 14.14M15.54 8.46a5 5 0 010 7.07"/></svg>',
    soundOff:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>',
    transfer:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>',
  };

  // 大图表情清单（与 admin emojiConfig.ts 保持一致）
  var EMOJI_LIST = [
    'smile', 'laugh', 'grin', 'wink', 'love', 'cool', 'shy', 'surprised',
    'cry', 'sad', 'angry', 'speechless', 'sleepy', 'ok', 'thumbup', 'thanks',
    'sweat', 'shock', 'think', 'puke', 'excited', 'hug', 'rose', 'kiss'
  ];

  var STORAGE_KEY = 'xservice_visitor_key';
  // session token 续期：80% 生命周期自动换发（对齐 widget.js）
  var SESSION_REFRESH_RATIO = 0.8;

  // ---------- 核心 ----------
  function ChatCore(options) {
    this.apiUrl = options.apiUrl || ''; // 后端地址，如 http://localhost:8080
    this.wsUrl = options.wsUrl || this._deriveWsUrl(this.apiUrl);
    this.source = options.source || 2; // 1网页 2弹窗 3iframe
    this.channel = options.channel || ''; // 嵌入渠道 id（决定主题色等）
    this.preview = options.preview === true; // 预览模式：不连后端、不建会话，仅渲染演示对话
    this.bare = options.bare === true; // 无头模式：隐藏内部主题色头部（供浮窗 widget 复用，避免与外层头部重复）
    this.container = options.container; // DOM 容器
    this.primaryColor = ''; // 渠道主题色（由 embed/config 注入）
    this.soundOn = true; // 新消息提示音开关（默认开）
    this.config = null; // 入口配置
    this.visitor = null; // {visitorId, visitorKey, nick}
    this.conversation = null; // 当前会话
    this.messages = [];
    this.ws = null;
    this.heartbeatTimer = null;
    this.reconnectTimer = null;
    this.manualClose = false;
    this._elements = {};
    // 嵌入渠道 session token（ems_ 前缀，短期，自动续期）
    this.sessionToken = '';
    this._sessionRefreshTimer = null;
    // AI 流式：messageId → { dom, content }，流式期间增量更新避免全量重渲染
    this._streamingMsgs = {};
  }

  ChatCore.prototype._deriveWsUrl = function (apiUrl) {
    if (!apiUrl) return '';
    return apiUrl.replace(/^http/, 'ws').replace(/\/$/, '') + '/ws/im';
  };

  // ---------- 初始化 ----------
  ChatCore.prototype.init = async function () {
    this._renderSkeleton();
    // 预览模式：仅渲染界面与演示对话，不注册访客、不建会话、不连 WS
    if (this.preview) {
      try {
        await this._initPreviewMode();
      } catch (e) {
        console.error('[xservice-chat] preview init error', e);
      }
      return;
    }
    try {
      // 嵌入渠道：先换 session token，保证后续请求（register/connect 等）都能带上
      if (this.channel) {
        await this._loadSessionToken();
      }
      await this._loadConfig();
      this._render();
      await this._registerVisitor();
      this._loadHistory();
      this._connectWs();
    } catch (e) {
      console.error('[xservice-chat] init error', e);
    }
  };

  // ============ 嵌入渠道 session token：换发 + 自动续期 ============
  // 用 channelId 调 /im/embed/issue 换短期 token（域名白名单+限流在后端校验）
  ChatCore.prototype._loadSessionToken = async function () {
    if (!this.channel) return;
    try {
      var res = await this._httpPost('/im/embed/issue', { channel: this.channel });
      var d = (res && res.data) || res || {};
      if (d && d.token) {
        this.setSessionToken(d.token, Number(d.expiresIn) || 0);
      }
    } catch (e) {
      // 换取失败不阻塞主流程（仍可用白名单直接访问），续期 timer 不启动
      console.warn('[xservice-chat] session token 获取失败', e);
    }
  };

  // 设置 session token 并调度续期；供内部换发 + 父页面 postMessage 注入复用
  ChatCore.prototype.setSessionToken = function (token, expiresIn) {
    if (!token) return;
    this.sessionToken = token;
    this._scheduleSessionRefresh(Number(expiresIn) || 0);
  };

  // 80% 生命周期自动换发，避免长会话中途过期
  ChatCore.prototype._scheduleSessionRefresh = function (expiresInSec) {
    if (this._sessionRefreshTimer) clearTimeout(this._sessionRefreshTimer);
    var ttl = Number(expiresInSec) > 0 ? Number(expiresInSec) : 1800;
    var delayMs = Math.max(Math.floor(ttl * SESSION_REFRESH_RATIO), 30) * 1000;
    var self = this;
    this._sessionRefreshTimer = setTimeout(function () {
      self._loadSessionToken();
    }, delayMs);
  };

  // 预览模式初始化：填充默认配置 + 演示对话，保证界面完整可看
  ChatCore.prototype._initPreviewMode = async function () {
    // 默认入口配置（与 _loadConfig 的兜底一致，含演示电话便于预览入口效果）
    this.config = {
      onlineChatShow: 1,
      ticketShow: 1,
      phoneShow: 1,
      phone: '400-888-8888',
      ticketUrl: '/workOrder',
      title: '在线客服',
    };
    // 嵌入渠道：读取渠道主题色（只读 GET，不会建会话，安全）
    if (this.channel) {
      try {
        var embed = await this._httpGet('/im/embed/config?channel=' + encodeURIComponent(this.channel));
        var data = (embed && embed.data) || embed || {};
        if (data.primaryColor) {
          this.primaryColor = data.primaryColor;
        }
      } catch (e) {
        /* 用默认主题色 */
      }
    }
    this._render();
    if (this.primaryColor) this._applyThemeColor(this.primaryColor);
    // 填充演示对话（先渲染消息，再插提示，避免被 _renderMessages 的 innerHTML='' 清空）
    this.messages = this._buildDemoMessages();
    this._renderMessages();
    // 在消息列表顶部插入「预览模式」提示
    var tip = el('div', { class: 'xs-system-tip' }, '预览模式 · 仅作演示，不会创建会话');
    if (this._elements.msgList && this._elements.msgList.firstChild) {
      this._elements.msgList.insertBefore(tip, this._elements.msgList.firstChild);
    } else if (this._elements.msgList) {
      this._elements.msgList.appendChild(tip);
    }
    // 滚动到最底部：演示对话含图片/表情，需等图片加载完撑高后再定位
    this._scrollBottomOnLoaded();
  };

  // 等消息列表内的图片加载完成后滚动到底部（图片异步加载会改变容器高度）
  ChatCore.prototype._scrollBottomOnLoaded = function () {
    var self = this;
    var done = function () { self._scrollBottom(); };
    done();
    if (window.requestAnimationFrame) requestAnimationFrame(done);
    var imgs = this._elements.msgList ? this._elements.msgList.querySelectorAll('img') : [];
    Array.prototype.forEach.call(imgs, function (img) {
      if (img.complete) return;
      img.addEventListener('load', done, { once: true });
      img.addEventListener('error', done, { once: true });
    });
    // 兜底：延迟再滚一次，覆盖图片加载较慢的情况
    setTimeout(done, 250);
  };

  // 内置演示对话数据（覆盖文字/图片/大图表情/双方消息）
  ChatCore.prototype._buildDemoMessages = function () {
    var base = this.apiUrl.replace(/\/$/, '');
    return [
      {
        id: 1001,
        senderType: 1, // 访客
        senderName: '访客',
        msgType: 1,
        content: '你好，我想咨询一下产品价格',
        isRecalled: 0,
        createTime: '2024-01-01 10:00:00',
      },
      {
        id: 1002,
        senderType: 2, // 客服
        senderName: '客服小助手',
        msgType: 1,
        content: '您好，很高兴为您服务！请问您对哪款产品感兴趣？',
        isRecalled: 0,
        createTime: '2024-01-01 10:00:12',
      },
      {
        id: 1003,
        senderType: 2,
        senderName: '客服小助手',
        msgType: 2, // 图片
        content: base + '/emoji/smile.png',
        isRecalled: 0,
        createTime: '2024-01-01 10:00:30',
      },
      {
        id: 1004,
        senderType: 1,
        senderName: '访客',
        msgType: 3, // 大图表情
        content: base + '/emoji/thanks.png',
        isRecalled: 0,
        createTime: '2024-01-01 10:01:00',
      },
      {
        id: 1005,
        senderType: 2,
        senderName: '客服小助手',
        msgType: 1,
        content: '这是我们的最新报价单，您可以参考一下，有任何问题随时联系我哦 😊',
        isRecalled: 0,
        createTime: '2024-01-01 10:01:20',
      },
    ];
  };

  ChatCore.prototype._loadConfig = async function () {
    try {
      var res = await this._httpGet('/im/config/visitor');
      this.config = (res && res.data) || res || {};
    } catch (e) {
      this.config = {
        onlineChatShow: 1,
        ticketShow: 1,
        phoneShow: 1,
        phone: '',
        ticketUrl: '/workOrder',
        title: '在线客服',
      };
    }
    // 嵌入渠道：读取渠道主题色，应用到 CSS 变量 --xs-brand
    if (this.channel) {
      try {
        var embed = await this._httpGet('/im/embed/config?channel=' + encodeURIComponent(this.channel));
        var data = (embed && embed.data) || embed || {};
        if (data.primaryColor) {
          this.primaryColor = data.primaryColor;
          this._applyThemeColor(data.primaryColor);
        }
      } catch (e) {
        /* 用默认主题色 */
      }
    }
  };

  // 把主题色应用到容器（气泡自己、发送按钮、入口条高亮等）
  ChatCore.prototype._applyThemeColor = function (color) {
    if (!color || !this.container) return;
    // 同时设置 root/widget/iframe 三种容器类，确保命中
    var targets = [this.container];
    targets.forEach(function (node) {
      if (node && node.style) node.style.setProperty('--xs-brand', color);
    });
    // 自己气泡色 = 主题色加亮版（白色文字可读）
    var selfBubble = this._lighten(color, 0.35);
    if (this.container && this.container.style) {
      this.container.style.setProperty('--xs-bubble-self', color);
    }
  };

  // 简单颜色提亮（hex → 更接近白的 hex），用于自己气泡背景
  ChatCore.prototype._lighten = function (hex, amount) {
    var h = hex.replace('#', '');
    if (h.length !== 6) return hex;
    var r = parseInt(h.slice(0, 2), 16);
    var g = parseInt(h.slice(2, 4), 16);
    var b = parseInt(h.slice(4, 6), 16);
    r = Math.round(r + (255 - r) * amount);
    g = Math.round(g + (255 - g) * amount);
    b = Math.round(b + (255 - b) * amount);
    return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
  };

  ChatCore.prototype._registerVisitor = async function () {
    var key = localStorage.getItem(STORAGE_KEY) || uuid();
    localStorage.setItem(STORAGE_KEY, key);
    try {
      var res = await this._httpPost('/im/visitor/register', {
        visitorKey: key,
        sourceUrl: location.href,
        source: this.source,
      });
      this.visitor = (res && res.data) || res;
      localStorage.setItem(STORAGE_KEY, this.visitor.visitorKey);
    } catch (e) {
      console.error('[xservice-chat] register failed', e);
    }
  };

  ChatCore.prototype._loadHistory = async function () {
    // 先确保有会话
    try {
      var conv = await this._httpPost(
        '/im/visitor/startConversation?visitorId=' +
          this.visitor.visitorId +
          '&source=' +
          this.source
      );
      this.conversation = (conv && conv.data) || conv;
    } catch (e) {
      // ignore
    }
    if (!this.conversation) return;
    try {
      var res = await this._httpGet(
        '/im/visitor/history?conversationId=' +
          this.conversation.id +
          '&page=1&size=50'
      );
      var data = (res && res.data) || res || {};
      var list = data.data || [];
      this.messages = list;
      this._renderMessages();
      this._scrollBottom();
      this._refreshTransferBtn();
    } catch (e) {
      // ignore
    }
  };

  // ---------- WebSocket ----------
  ChatCore.prototype._connectWs = function () {
    var self = this;
    if (!this.visitor) return;
    var url =
      this.wsUrl + '?role=visitor&key=' + encodeURIComponent(this.visitor.visitorKey);
    this.manualClose = false;
    try {
      this.ws = new WebSocket(url);
    } catch (e) {
      this._scheduleReconnect();
      return;
    }
    this.ws.onopen = function () {
      self._startHeartbeat();
    };
    this.ws.onclose = function () {
      self._stopHeartbeat();
      if (!self.manualClose) self._scheduleReconnect();
    };
    this.ws.onerror = function () {};
    this.ws.onmessage = function (ev) {
      try {
        var frame = JSON.parse(ev.data);
        self._handleFrame(frame);
      } catch (e) {}
    };
  };

  ChatCore.prototype._handleFrame = function (frame) {
    var t = frame.type;
    if (t === 'new_message') {
      var msg = frame.message;
      // 去重
      if (!this.messages.find(function (m) { return m.id === msg.id; })) {
        this.messages.push(msg);
        this._renderMessages();
        this._scrollBottom();
        // 收到对方消息时播放提示音（senderType===2 为客服）
        if (msg.senderType === 2) {
          this._playSound();
        }
      }
    } else if (t === 'chat_ack') {
      // 发送回执，可做已发送标记（此处简化）
    } else if (t === 'status') {
      // 转人工提示（status=0 带 tip）
      if (frame.status === 0 && frame.tip) {
        this._renderSystemTip(frame.tip);
      } else {
        this._renderSystemTip(
          frame.status === 1
            ? '客服 ' + (frame.csNick || '') + ' 已为您服务'
            : frame.status === 3
            ? '会话已结束'
            : ''
        );
      }
      if (frame.status === 1 && this.conversation) {
        this.conversation.status = 1;
        this.conversation.csNick = frame.csNick;
      }
      if (frame.status === 3 && this.conversation) {
        this.conversation.status = 3;
        this._maybeShowEvaluate();
      }
      this._refreshTransferBtn();
    } else if (t === 'typing') {
      if (frame.senderType === 2) this._showTyping();
    } else if (t === 'stream_start') {
      this._handleStreamStart(frame);
    } else if (t === 'stream_chunk') {
      this._handleStreamChunk(frame);
    } else if (t === 'stream_end') {
      this._handleStreamEnd(frame);
    } else if (t === 'stream_error') {
      this._handleStreamError(frame);
    }
  };

  // ---------- AI 流式帧 ----------
  // 后端在会话未接入（status=0）且 AI 开启时，对访客消息异步生成回答，
  // 通过已有的 WS 连接推送流式帧：stream_start → stream_chunk(多次) → stream_end。
  ChatCore.prototype._handleStreamStart = function (frame) {
    var msg = {
      id: frame.messageId,
      senderType: 4, // AI 助手
      msgType: 1,
      content: '',
      senderName: 'AI 助手',
      streaming: true,
      createTime: new Date(),
    };
    this.messages.push(msg);
    this._renderMessages();
    this._scrollBottom();
    // 记录流式消息引用，便于增量更新
    this._streamingMsgs[frame.messageId] = { content: '' };
  };

  ChatCore.prototype._handleStreamChunk = function (frame) {
    var entry = this._streamingMsgs[frame.messageId];
    if (!entry) return;
    entry.content += frame.token || '';
    // 找到对应 DOM 气泡增量更新（不全量重渲染）
    var bubble = this._elements.msgList && this._elements.msgList.querySelector(
      '.xs-bubble[data-msg-id="' + frame.messageId + '"] .xs-bubble-content'
    );
    if (bubble) {
      bubble.innerHTML = this._renderAiMarkdown(entry.content) + this._typingCursorHtml();
      this._scrollBottom();
    }
  };

  ChatCore.prototype._handleStreamEnd = function (frame) {
    var entry = this._streamingMsgs[frame.messageId];
    // 后端落库后用真实 messageId 替换临时 id
    var realId = frame.messageId;
    var tempId = frame.tempMsgId || realId;
    var msg = this.messages.find(function (m) { return m.id === tempId; });
    if (msg) {
      msg.id = realId;
      msg.streaming = false;
      if (entry) msg.content = entry.content;
      msg.createTime = new Date();
    }
    delete this._streamingMsgs[tempId];
    this._renderMessages();
    this._scrollBottom();
  };

  ChatCore.prototype._handleStreamError = function (frame) {
    var msg = this.messages.find(function (m) { return m.id === frame.messageId; });
    if (msg) {
      msg.streaming = false;
      msg.content = frame.message || 'AI 回答失败，已为您转接人工客服';
    }
    delete this._streamingMsgs[frame.messageId];
    this._renderMessages();
    this._scrollBottom();
  };

  // AI 消息 markdown → 安全 HTML（白名单标签，过滤 script/事件属性）
  ChatCore.prototype._renderAiMarkdown = function (text) {
    if (!text) return '';
    var html;
    try {
      html = window.marked ? window.marked.parse(text, { breaks: true, gfm: true }) : this._escapeHtml(text);
    } catch (e) {
      html = this._escapeHtml(text);
    }
    return this._sanitizeHtml(html);
  };

  // 极简 XSS 过滤：移除 script/style/事件属性
  ChatCore.prototype._sanitizeHtml = function (html) {
    if (!html) return '';
    // 移除 script/style/iframe 块
    html = html.replace(/<\s*(script|style|iframe|object|embed)[\s\S]*?<\/\s*\1\s*>/gi, '');
    // 移除所有 on* 事件属性
    html = html.replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '');
    // 移除 javascript: 协议
    html = html.replace(/(href|src)\s*=\s*("javascript:[^"]*"|'javascript:[^']*'|javascript:[^\s>]+)/gi, '$1="#"');
    return html;
  };

  ChatCore.prototype._escapeHtml = function (s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  };

  // 流式打字光标 HTML
  ChatCore.prototype._typingCursorHtml = function () {
    return '<span class="xs-ai-typing"><i></i><i></i><i></i></span>';
  };

  ChatCore.prototype._send = function (frame) {
    if (this.ws && this.ws.readyState === 1) {
      this.ws.send(JSON.stringify(frame));
      return true;
    }
    return false;
  };

  ChatCore.prototype._startHeartbeat = function () {
    var self = this;
    this._stopHeartbeat();
    this.heartbeatTimer = setInterval(function () {
      self._send({ type: 'ping' });
    }, 25000);
  };

  ChatCore.prototype._stopHeartbeat = function () {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  };

  ChatCore.prototype._scheduleReconnect = function () {
    var self = this;
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(function () {
      self.reconnectTimer = null;
      self._connectWs();
    }, 3000);
  };

  // ---------- HTTP ----------
  ChatCore.prototype._httpGet = function (path) {
    return this._http('GET', path, null);
  };
  ChatCore.prototype._httpPost = function (path, body) {
    return this._http('POST', path, body);
  };
  ChatCore.prototype._http = function (method, path, body) {
    var url = path.indexOf('http') === 0 ? path : this.apiUrl + path;
    var headers = { 'Content-Type': 'application/json' };
    // 嵌入渠道会话 token，后端可据此识别来源（当前白名单+限流保护，校验可选）
    if (this.sessionToken) {
      headers['X-Embed-Token'] = this.sessionToken;
    }
    return fetch(url, {
      method: method,
      headers: headers,
      body: body ? JSON.stringify(body) : null,
    }).then(function (res) {
      return res.json();
    });
  };

  // ---------- 渲染 ----------
  ChatCore.prototype._renderSkeleton = function () {
    if (!this.container) return;
    this.container.innerHTML = '<div style="padding:40px;text-align:center;color:#bbb;">加载中...</div>';
  };

  ChatCore.prototype._render = function () {
    if (!this.container) return;
    var cfg = this.config;
    this.container.innerHTML = '';

    var pageCls = 'xs-chat-page';
    if (this.source === 3) pageCls += ' xs-embed';
    if (this.bare) pageCls += ' xs-bare';
    var page = el('div', { class: pageCls });
    this._elements.page = page;

    // 顶部入口条（在线客服/电话）——工单、提示音入口已挪到底部输入工具栏
    var bar = el('div', { class: 'xs-entry-bar' });
    bar.appendChild(el('span', { class: 'xs-title' }, cfg.title || '在线客服'));
    var actions = el('div', { class: 'xs-entry-actions' });
    if (cfg.phoneShow == 1 && cfg.phone) {
      actions.appendChild(
        el(
          'a',
          {
            class: 'xs-entry-btn',
            href: 'tel:' + cfg.phone,
            html: ICON.phone + '<span>' + cfg.phone + '</span>',
          },
          null
        )
      );
    }
    bar.appendChild(actions);
    page.appendChild(bar);

    // 消息列表
    var list = el('div', { class: 'xs-msg-list' });
    this._elements.msgList = list;
    page.appendChild(list);

    // 输入区
    var self0 = this;
    var inputArea = el('div', { class: 'xs-input-area' });
    var toolbar = el('div', { class: 'xs-input-toolbar' });
    // 表情按钮
    var emojiBtn = el('button', { class: 'xs-tool-btn', title: '发送表情', html: ICON.emoji });
    // 图片上传按钮
    var fileInput = el('input', { type: 'file', accept: 'image/jpeg,image/png,image/gif,image/webp', style: 'display:none' });
    var imgBtn = el('button', { class: 'xs-tool-btn', title: '发送图片', html: ICON.image });
    toolbar.appendChild(emojiBtn);
    toolbar.appendChild(imgBtn);
    // 工单入口（从顶部挪到底部工具栏）
    if (cfg.ticketShow == 1 && cfg.ticketUrl) {
      toolbar.appendChild(
        el(
          'a',
          {
            class: 'xs-tool-btn xs-tool-link',
            title: '提交工单',
            href: cfg.ticketUrl,
            target: '_blank',
            rel: 'noopener',
            html: ICON.ticket + '<span>工单</span>',
          },
          null
        )
      );
    }
    // 提示音开关（从顶部挪到底部工具栏，浮窗 bare 模式也能用）
    var soundBtn = el('button', {
      class: 'xs-tool-btn xs-sound-tool',
      title: '提示音开关',
      html: this.soundOn ? ICON.soundOn : ICON.soundOff,
    });
    soundBtn.addEventListener('click', function () {
      self0.soundOn = !self0.soundOn;
      soundBtn.innerHTML = self0.soundOn ? ICON.soundOn : ICON.soundOff;
      soundBtn.classList.toggle('off', !self0.soundOn);
      // 开启时立即播放一次：既作试听反馈，又在用户手势内「解锁」Audio，
      // 否则浏览器自动播放策略会阻止后续收到新消息时的 _playSound。
      if (self0.soundOn) {
        self0._playSound();
      }
    });
    toolbar.appendChild(soundBtn);

    // 转人工按钮：仅在 AI 开启且会话未接入（status=0，AI 接待中）时显示
    var transferBtn = el('button', {
      class: 'xs-tool-btn xs-transfer-tool',
      title: '转人工客服',
      html: ICON.transfer || '人工',
    });
    transferBtn.style.display = 'none'; // 默认隐藏，由 _refreshTransferBtn 控制显隐
    transferBtn.addEventListener('click', function () {
      self0._handleTransferHuman();
    });
    toolbar.appendChild(transferBtn);

    toolbar.appendChild(fileInput);
    inputArea.appendChild(toolbar);

    var inputRow = el('div', { class: 'xs-input-row' });
    var textarea = el('textarea', { placeholder: '请输入消息，Enter 发送', rows: '1' });
    var sendBtn = el('button', { class: 'xs-send-btn', html: ICON.send, title: '发送' });
    inputRow.appendChild(textarea);
    inputRow.appendChild(sendBtn);
    inputArea.appendChild(inputRow);
    page.appendChild(inputArea);

    this.container.appendChild(page);
    this._elements.textarea = textarea;
    this._elements.sendBtn = sendBtn;
    this._elements.fileInput = fileInput;
    this._elements.transferBtn = transferBtn;

    // 事件绑定
    var self = this;
    sendBtn.addEventListener('click', function () {
      self._handleSend();
    });
    textarea.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        self._handleSend();
      } else {
        self._send({ type: 'typing', conversationId: self.conversation && self.conversation.id });
      }
    });
    imgBtn.addEventListener('click', function () {
      fileInput.click();
    });
    emojiBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      self._toggleEmojiPanel();
    });
    fileInput.addEventListener('change', function () {
      if (fileInput.files && fileInput.files[0]) {
        self._handleUpload(fileInput.files[0]);
        fileInput.value = '';
      }
    });
  };

  ChatCore.prototype._renderMessages = function () {
    if (!this._elements.msgList) return;
    var list = this._elements.msgList;
    list.innerHTML = '';
    var self = this;
    this.messages.forEach(function (m) {
      list.appendChild(self._renderBubble(m));
    });
  };

  ChatCore.prototype._renderBubble = function (m) {
    var isSelf = m.senderType === 1;
    var isAi = m.senderType === 4;
    var recalled = m.isRecalled === 1;
    var name = isSelf ? '我' : isAi ? 'AI' : m.senderName || '客服';
    var avatarText = (name || '?').charAt(0).toUpperCase();

    var wrapClass = 'xs-bubble' + (isSelf ? ' is-self' : '') + (isAi ? ' is-ai' : '');
    var wrap = el('div', { class: wrapClass });
    if (m.id != null) wrap.setAttribute('data-msg-id', m.id);
    wrap.appendChild(el('div', { class: 'xs-avatar' }, avatarText));

    var body = el('div', { class: 'xs-bubble-body' });
    var content;
    if (recalled) {
      content = el('div', { class: 'xs-bubble-content xs-recalled' }, '该消息已撤回');
    } else if (m.msgType === 2) {
      content = el('div', { class: 'xs-bubble-content' });
      var img = el('img', { src: m.content });
      var self2 = this;
      img.addEventListener('click', function () {
        self2._previewImage(m.content);
      });
      content.appendChild(img);
    } else if (m.msgType === 3) {
      // 大图表情：透明背景，表情图直接呈现
      content = el('div', { class: 'xs-bubble-content xs-emoji-bubble' });
      content.appendChild(el('img', { src: m.content, draggable: 'false' }));
    } else if (isAi) {
      // AI 消息：markdown 渲染（安全过滤），流式中追加打字光标
      var aiInner = this._renderAiMarkdown(m.content || '');
      if (m.streaming) aiInner += this._typingCursorHtml();
      content = el('div', { class: 'xs-bubble-content xs-ai-content' });
      content.innerHTML = aiInner;
    } else {
      content = el('div', { class: 'xs-bubble-content' }, m.content || '');
    }
    body.appendChild(content);
    body.appendChild(el('div', { class: 'xs-bubble-meta' }, fmtTime(m.createTime)));
    wrap.appendChild(body);
    return wrap;
  };

  ChatCore.prototype._renderSystemTip = function (text) {
    if (!text || !this._elements.msgList) return;
    var tip = el('div', { class: 'xs-system-tip' }, text);
    this._elements.msgList.appendChild(tip);
    this._scrollBottom();
  };

  ChatCore.prototype._showTyping = function () {
    if (!this._elements.msgList) return;
    var exist = qs('.xs-typing', this._elements.msgList);
    if (exist) return;
    var tip = el('div', { class: 'xs-typing' });
    tip.appendChild(el('span'));
    tip.appendChild(el('span'));
    tip.appendChild(el('span'));
    this._elements.msgList.appendChild(tip);
    this._scrollBottom();
    var self = this;
    setTimeout(function () {
      if (tip.parentNode) tip.parentNode.removeChild(tip);
    }, 3000);
  };

  ChatCore.prototype._scrollBottom = function () {
    var l = this._elements.msgList;
    if (l) l.scrollTop = l.scrollHeight;
  };

  // ---------- 转人工 ----------
  // 根据会话状态与 AI 开关，刷新「转人工」按钮显隐：
  // 仅 AI 开启 + 会话未接入（status=0，AI 接待中）时显示。
  ChatCore.prototype._refreshTransferBtn = function () {
    var btn = this._elements.transferBtn;
    if (!btn) return;
    var aiEnable = this.config && (this.config.aiEnable == 1 || this.config.ai_enable == 1);
    var statusZero = this.conversation && (this.conversation.status == null || this.conversation.status == 0);
    var show = aiEnable && statusZero;
    btn.style.display = show ? '' : 'none';
  };

  ChatCore.prototype._handleTransferHuman = function () {
    var self = this;
    if (!this.conversation) return;
    this._httpPost('/im/visitor/transferHuman?conversationId=' + this.conversation.id)
      .then(function (res) {
        if (res && res.code === 0) {
          self._renderSystemTip('正在为您转接人工客服，请稍候…');
        } else {
          self._toast((res && res.message) || '转接失败');
        }
      })
      .catch(function () {
        self._toast('网络错误，转接失败');
      });
  };

  // ---------- 发送 ----------
  ChatCore.prototype._handleSend = function () {
    // 预览模式：拦截发送，仅提示
    if (this.preview) {
      this._toast('预览模式，不可发送');
      return;
    }
    if (!this.conversation) return;
    var ta = this._elements.textarea;
    var text = ta.value.trim();
    if (!text) return;
    var ok = this._send({
      type: 'chat',
      conversationId: this.conversation.id,
      msgType: 1,
      content: text,
      clientMsgId: uuid(),
    });
    if (ok) {
      ta.value = '';
      ta.style.height = 'auto';
    }
  };

  // ---------- 表情面板 ----------
  ChatCore.prototype._toggleEmojiPanel = function () {
    var existing = this._elements.emojiPanel;
    if (existing) {
      existing.remove();
      this._elements.emojiPanel = null;
      return;
    }
    var self = this;
    var panel = el('div', { class: 'xs-emoji-panel' });
    EMOJI_LIST.forEach(function (eid) {
      var url = self.apiUrl.replace(/\/$/, '') + '/emoji/' + eid + '.png';
      var cell = el('button', { class: 'xs-emoji-cell', title: eid });
      cell.appendChild(el('img', { src: url, alt: eid, draggable: 'false' }));
      cell.addEventListener('click', function () {
        self._sendEmoji(url);
        panel.remove();
        self._elements.emojiPanel = null;
      });
      panel.appendChild(cell);
    });
    // 定位到工具栏上方
    var area = this._elements.page.querySelector('.xs-input-area');
    area.style.position = 'relative';
    area.appendChild(panel);
    this._elements.emojiPanel = panel;
    // 点击外部关闭
    setTimeout(function () {
      function close(e) {
        if (!panel.contains(e.target)) {
          panel.remove();
          self._elements.emojiPanel = null;
          document.removeEventListener('click', close);
        }
      }
      document.addEventListener('click', close);
    }, 0);
  };

  ChatCore.prototype._sendEmoji = function (url) {
    // 预览模式：拦截发送，仅提示
    if (this.preview) {
      this._toast('预览模式，不可发送');
      return;
    }
    if (!this.conversation) return;
    this._send({
      type: 'chat',
      conversationId: this.conversation.id,
      msgType: 3,
      content: url,
      clientMsgId: uuid(),
    });
  };

  // 图片上传前校验：扩展名 + MIME + 文件头魔数 + 大小
  ChatCore.prototype._validateImage = async function (file) {
    if (!file) return '请选择图片';
    if (file.size > 5 * 1024 * 1024) return '图片不能超过 5MB';
    var name = (file.name || '').toLowerCase();
    var dot = name.lastIndexOf('.');
    var ext = dot >= 0 ? name.slice(dot + 1) : '';
    var rules = {
      jpg: { mimes: ['image/jpeg'], magic: [[0xff, 0xd8, 0xff]] },
      jpeg: { mimes: ['image/jpeg'], magic: [[0xff, 0xd8, 0xff]] },
      png: { mimes: ['image/png'], magic: [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]] },
      gif: { mimes: ['image/gif'], magic: [[0x47, 0x49, 0x46, 0x38, 0x37, 0x61], [0x47, 0x49, 0x46, 0x38, 0x39, 0x61]] },
      webp: { mimes: ['image/webp'], magic: [[0x52, 0x49, 0x46, 0x46]] }
    };
    var rule = rules[ext];
    if (!rule) return '不支持的图片格式（仅支持 jpg、jpeg、png、gif、webp）';
    if (file.type && rule.mimes.indexOf(file.type) === -1) return '文件类型与扩展名不匹配';
    // 文件头魔数
    return new Promise(function (resolve) {
      var reader = new FileReader();
      reader.onerror = function () { resolve('读取文件失败，请重试'); };
      reader.onload = function () {
        var buf = new Uint8Array(reader.result);
        function matchMagic(magic) {
          if (buf.length < magic.length) return false;
          for (var i = 0; i < magic.length; i++) { if (buf[i] !== magic[i]) return false; }
          return true;
        }
        var ok;
        if (ext === 'webp') {
          var isRiff = matchMagic([0x52, 0x49, 0x46, 0x46]);
          var isWebp = buf.length >= 12 && buf[8] === 0x57 && buf[9] === 0x45 && buf[10] === 0x42 && buf[11] === 0x50;
          ok = isRiff && isWebp;
        } else {
          ok = rule.magic.some(matchMagic);
        }
        resolve(ok ? null : '文件已损坏或不是有效的图片（内容与格式不符）');
      };
      reader.readAsArrayBuffer(file.slice(0, 16));
    });
  };

  ChatCore.prototype._handleUpload = async function (file) {
    // 预览模式：拦截上传，仅提示
    if (this.preview) {
      this._toast('预览模式，不可上传');
      return;
    }
    if (!this.conversation) return;
    // 前端严格校验：扩展名 + MIME + 文件头魔数 + 大小
    var err = await this._validateImage(file);
    if (err) {
      this._toast(err);
      return;
    }
    var formData = new FormData();
    formData.append('file', file);
    try {
      var res = await fetch(this.apiUrl + '/im/visitor/uploadImage', {
        method: 'POST',
        body: formData,
      }).then(function (r) { return r.json(); });
      var url = (res && res.data) || res;
      this._send({
        type: 'chat',
        conversationId: this.conversation.id,
        msgType: 2,
        content: url,
        clientMsgId: uuid(),
      });
    } catch (e) {
      console.error('[xservice-chat] upload failed', e);
      this._toast('图片上传失败');
    }
  };

  // 轻量提示（访客端无 UI 框架）
  ChatCore.prototype._toast = function (text) {
    var t = el('div', { class: 'xs-toast' }, text);
    document.body.appendChild(t);
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 2200);
  };

  // 播放新消息提示音（voice/new_msg.wav，由后端托管）
  ChatCore.prototype._playSound = function () {
    if (!this.soundOn) return;
    if (this._audio) {
      try { this._audio.currentTime = 0; this._audio.play(); } catch (e) {}
      return;
    }
    try {
      this._audio = new Audio(this.apiUrl.replace(/\/$/, '') + '/voice/new_msg.wav');
      this._audio.play();
    } catch (e) { /* 浏览器禁止自动播放，忽略 */ }
  };

  ChatCore.prototype._previewImage = function (url) {
    var mask = el('div', { class: 'xs-preview-mask' });
    mask.appendChild(el('img', { src: url }));
    mask.addEventListener('click', function () {
      if (mask.parentNode) mask.parentNode.removeChild(mask);
    });
    document.body.appendChild(mask);
  };

  // ---------- 评价 ----------
  ChatCore.prototype._maybeShowEvaluate = function () {
    var self = this;
    var mask = el('div', { class: 'xs-eval-mask' });
    var card = el('div', { class: 'xs-eval-card' });
    card.appendChild(el('h3', null, '会话已结束，请为本次服务评分'));
    var stars = el('div', { class: 'xs-eval-stars' });
    var score = 5;
    function renderStars(n) {
      stars.innerHTML = '';
      for (var i = 1; i <= 5; i++) {
        var s = el('span', { 'data-n': i, html: i <= n ? '★' : '☆' });
        if (i <= n) s.className = 'on';
        s.addEventListener('click', function () {
          score = parseInt(this.getAttribute('data-n'), 10);
          renderStars(score);
        });
        stars.appendChild(s);
      }
    }
    renderStars(score);
    card.appendChild(stars);
    var actions = el('div', { class: 'xs-eval-actions' });
    var skipBtn = el('button', null, '跳过');
    var okBtn = el('button', { class: 'primary' }, '提交');
    actions.appendChild(skipBtn);
    actions.appendChild(okBtn);
    card.appendChild(actions);
    mask.appendChild(card);
    document.body.appendChild(mask);

    skipBtn.addEventListener('click', function () {
      document.body.removeChild(mask);
    });
    okBtn.addEventListener('click', async function () {
      try {
        await self._httpPost('/im/visitor/evaluate', {
          conversationId: self.conversation.id,
          score: score,
        });
      } catch (e) {}
      document.body.removeChild(mask);
    });
  };

  // ---------- 暴露 ----------
  global.XServiceChatCore = ChatCore;
})(window);
