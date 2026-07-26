<template>
  <div class="login-layout">
    <div class="animated-bg">
      <div class="knowledge-node node-1">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
        </svg>
      </div>
      <div class="knowledge-node node-2">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
        </svg>
      </div>
      <div class="knowledge-node node-3">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <div class="knowledge-node node-4">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <ellipse cx="12" cy="5" rx="9" ry="3"/>
          <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/>
          <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
        </svg>
      </div>
      <div class="knowledge-node node-5">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <path d="m21 21-4.35-4.35"/>
        </svg>
      </div>
      <div class="knowledge-node node-6">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
          <line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
      </div>
      <div class="knowledge-node node-7">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
      </div>
      <div class="knowledge-node node-8">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
        </svg>
      </div>
      <div class="knowledge-node node-9">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
      </div>
      <div class="knowledge-node node-10">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M12 1v6m0 6v6M5.64 5.64l4.24 4.24m4.24 4.24l4.24 4.24M1 12h6m6 0h6M5.64 18.36l4.24-4.24m4.24-4.24l4.24-4.24"/>
        </svg>
      </div>
      <div class="knowledge-node node-11">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 11l3 3L22 4"/>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
        </svg>
      </div>
      <div class="knowledge-node node-12">
        <svg class="node-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
        </svg>
      </div>

      <svg class="knowledge-lines" viewBox="0 0 100 100" preserveAspectRatio="none">
        <line class="connection-line line-1" x1="20" y1="15" x2="35" y2="25" />
        <line class="connection-line line-2" x1="35" y1="25" x2="55" y2="20" />
        <line class="connection-line line-3" x1="55" y1="20" x2="85" y2="12" />
        <line class="connection-line line-4" x1="8" y1="35" x2="25" y2="45" />
        <line class="connection-line line-5" x1="25" y1="45" x2="65" y2="48" />
        <line class="connection-line line-6" x1="20" y1="60" x2="60" y2="75" />
        <line class="connection-line line-7" x1="20" y1="15" x2="20" y2="60" />
        <line class="connection-line line-8" x1="55" y1="20" x2="45" y2="50" />
        <line class="connection-line line-9" x1="65" y1="48" x2="90" y2="38" />
        <line class="connection-line line-10" x1="40" y1="70" x2="75" y2="80" />
        <line class="connection-line line-11" x1="35" y1="25" x2="25" y2="45" />
        <line class="connection-line line-12" x1="75" y1="30" x2="65" y2="48" />
      </svg>
    </div>

    <!-- 居中登录卡片：左侧介绍 + 右侧登录 -->
    <div class="login-card-wrapper">
      <!-- 左侧品牌展示区 -->
      <div class="showcase-section">
        <div class="showcase-content">
          <h1 class="showcase-title">{{ websiteConfig.title }}</h1>
          <p class="showcase-description">
            结合 AI 智能知识库客服 + 人工客服 + 工单客服于一体，提供完整的客户服务体验
          </p>

          <div class="feature-tags">
            <span class="tag">智能对话</span>
            <span class="tag">知识库</span>
            <span class="tag">工单系统</span>
          </div>

          <!-- 特性列表 -->
          <div class="login-features">
            <div class="feature-item">
              <span class="feature-icon">✓</span>
              <span class="feature-text">AI 驱动的自然语言处理</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">✓</span>
              <span class="feature-text">7×24 小时全天候智能服务</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">✓</span>
              <span class="feature-text">实时数据洞察与分析</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-panel">
          <div class="form-card">
            <div class="form-header">
              <h2 class="form-title">账号登录</h2>
              <p class="form-welcome">欢迎回来，请登录您的账号</p>
            </div>

            <div class="form-content">
              <n-form
                ref="formRef"
                :model="formInline"
                :rules="rules"
                label-placement="left"
                size="large"
                class="login-form"
                @keyup.enter="handleSubmit"
              >
                <n-form-item path="account" :label="'账号'">
                  <n-input
                    v-model:value="formInline.account"
                    placeholder="请输入账号"
                    size="large"
                    :disabled="loading"
                  />
                </n-form-item>

                <n-form-item path="password" :label="'密码'">
                  <n-input
                    v-model:value="formInline.password"
                    type="password"
                    show-password-on="click"
                    placeholder="请输入密码"
                    size="large"
                    :disabled="loading"
                  />
                </n-form-item>

                <n-button
                  type="primary"
                  size="large"
                  block
                  :loading="loading"
                  class="submit-button"
                  @click="handleSubmit"
                >
                  {{ loading ? '登录中...' : '登 录' }}
                </n-button>

                <div class="form-options">
                  <n-checkbox v-model:checked="autoLogin">自动登录</n-checkbox>
                </div>
              </n-form>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 汉字点选验证码 -->
  <ClickCaptcha
    v-model:show="showCaptcha"
    @success="handleCaptchaSuccess"
    @cancel="handleCaptchaCancel"
  />
</template>

<script lang="ts" setup>
  import { reactive, ref, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useUserStore } from '@/store/modules/user';
  import { useMessage } from 'naive-ui';
  import { ResultEnum } from '@/enums/httpEnum';
  import { websiteConfig } from '@/config/website.config';
  import ClickCaptcha from '@/components/Captcha/ClickCaptcha.vue';

  const formRef = ref();
  const message = useMessage();
  const loading = ref(false);
  const autoLogin = ref(true);
  const showCaptcha = ref(false);

  // 验证码key
  const captchaKey = ref('');

  const formInline = reactive({
    account: '',
    password: '',
  });

  const rules = {
    account: { required: true, message: '请输入账号', trigger: 'blur' },
    password: { required: true, message: '请输入密码', trigger: 'blur' },
  };

  const userStore = useUserStore();
  const router = useRouter();
  const route = useRoute();

  // 页面加载
  onMounted(() => {
    setTimeout(() => {
      const usernameInput = document.querySelector('input[placeholder="请输入账号"]');
      if (usernameInput) {
        (usernameInput as HTMLElement).focus();
      }
    }, 500);
  });

  // 点击登录 - 先弹出验证码
  const handleSubmit = (e) => {
    e.preventDefault();
    // 防止重复点击
    if (loading.value) return;

    formRef.value.validate((errors) => {
      if (!errors) {
        // 显示验证码弹窗
        showCaptcha.value = true;
      } else {
        message.error('请填写账号和密码');
      }
    });
  };

  // 验证码验证成功
  const handleCaptchaSuccess = async (data: {
    key: string;
    points: Array<{ x: number; y: number }>;
  }) => {
    captchaKey.value = data.key;
    const { account, password } = formInline;
    message.loading('登录中...');
    loading.value = true;

    const params = {
      username: account,
      password,
      captcha: JSON.stringify(data.points), // 点击坐标序列 JSON
      key: data.key,
    };

    try {
      const res = await userStore.login(params);

      message.destroyAll();
      if (res.code == ResultEnum.SUCCESS) {
        const toPath = decodeURIComponent(
          (route.query?.redirect || '/dashboard/console') as string
        );
        message.success('登录成功，即将进入系统');

        setTimeout(() => {
          router.replace(toPath);
        }, 100);
      } else {
        const msg = res.message || '登录失败';
        message.error(msg);
        // 验证码错误：提示后重新打开验证码弹窗并自动刷新
        if (msg.includes('验证码')) {
          setTimeout(() => {
            showCaptcha.value = true;
          }, 800);
        }
      }
    } catch (error) {
      console.error('登录错误:', error);
      message.error('登录失败，请重试');
    } finally {
      loading.value = false;
    }
  };

  // 验证码取消
  const handleCaptchaCancel = () => {
    showCaptcha.value = false;
  };
</script>

<style lang="less" scoped>
  .login-layout {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    min-height: 100vh;
    overflow: hidden;
    position: relative;
    padding: 24px;
    box-sizing: border-box;
    background: linear-gradient(
      225deg,
      #022c22 0%,
      #064e3b 15%,
      #065f46 25%,
      #047857 38%,
      #059669 50%,
      #07c05f 65%,
      #10b981 78%,
      #34d399 90%,
      #6ee7b7 100%
    );

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: radial-gradient(circle at 20% 50%, rgba(255, 255, 255, 0.06) 0%, transparent 50%),
        radial-gradient(circle at 80% 50%, rgba(255, 255, 255, 0.04) 0%, transparent 50%);
      pointer-events: none;
    }
  }

  /* 整体居中卡片容器 */
  .login-card-wrapper {
    position: relative;
    z-index: 2;
    display: flex;
    width: 920px;
    max-width: 100%;
    min-height: 540px;
    background: rgba(6, 78, 59, 0.55);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 20px;
    backdrop-filter: blur(16px);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
    overflow: hidden;
  }

  .animated-bg {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    pointer-events: none;
    z-index: 1;
    overflow: hidden;
    contain: strict;
  }

  .knowledge-node {
    position: absolute;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.15);
    border: 2px solid rgba(255, 255, 255, 0.3);
    box-shadow:
      0 0 15px rgba(255, 255, 255, 0.35),
      0 0 30px rgba(16, 185, 129, 0.2),
      inset 0 0 8px rgba(255, 255, 255, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;
    animation: nodePulse 5s infinite ease-in-out;
    will-change: transform, opacity;
  }

  .node-icon {
    width: 20px;
    height: 20px;
    color: rgba(255, 255, 255, 0.9);
  }

  .node-1 { top: 15%; left: 20%; animation-delay: 0s; }
  .node-2 { top: 25%; left: 35%; animation-delay: 0.5s; }
  .node-3 { top: 20%; left: 55%; animation-delay: 1s; }
  .node-4 { top: 30%; left: 75%; animation-delay: 1.5s; }
  .node-5 { top: 45%; left: 25%; animation-delay: 2s; }
  .node-6 { top: 50%; left: 45%; animation-delay: 2.5s; }
  .node-7 { top: 48%; left: 65%; animation-delay: 3s; }
  .node-8 { top: 60%; left: 20%; animation-delay: 0.3s; }
  .node-9 { top: 12%; right: 15%; animation-delay: 1.8s; }
  .node-10 { top: 38%; right: 10%; animation-delay: 2.3s; }
  .node-11 { top: 70%; left: 40%; animation-delay: 0.8s; }
  .node-12 { top: 65%; left: 80%; animation-delay: 1.3s; }

  @keyframes nodePulse {
    0%, 100% { transform: scale(1); opacity: 0.65; }
    50% { transform: scale(1.08); opacity: 0.9; }
  }

  .knowledge-lines {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    opacity: 0.35;
  }

  .connection-line {
    stroke: rgba(255, 255, 255, 0.5);
    stroke-width: 1.5;
    stroke-dasharray: 6, 3;
    stroke-linecap: round;
    animation: lineFlow 10s infinite linear;
    will-change: stroke-dashoffset;
  }

  .line-1 { animation-delay: 0s; }
  .line-2 { animation-delay: 0.5s; }
  .line-3 { animation-delay: 1s; }
  .line-4 { animation-delay: 0.3s; }
  .line-5 { animation-delay: 0.8s; }
  .line-6 { animation-delay: 1.3s; }
  .line-7 { animation-delay: 1.8s; }
  .line-8 { animation-delay: 2.3s; }
  .line-9 { animation-delay: 0.2s; }
  .line-10 { animation-delay: 0.7s; }
  .line-11 { animation-delay: 0.9s; }
  .line-12 { animation-delay: 1.5s; }

  @keyframes lineFlow {
    0% { stroke-dashoffset: 0; }
    100% { stroke-dashoffset: 18; }
  }

  @media (prefers-reduced-motion: reduce) {
    .knowledge-node { animation: none; opacity: 0.65; }
    .connection-line { animation: none; }
  }

  /* 左侧品牌展示区 */
  .showcase-section {
    flex: 1;
    display: flex;
    align-items: center;
    padding: 50px 36px;
    box-sizing: border-box;
    position: relative;
  }

  .showcase-content {
    width: 100%;
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
  }

  .showcase-title {
    font-size: 40px;
    color: rgba(255, 255, 255, 0.98);
    margin: 0 0 8px 0;
    font-weight: 700;
    line-height: 1.3;
    letter-spacing: 1px;
  }

  .showcase-subtitle {
    font-size: 22px;
    color: rgba(255, 255, 255, 0.95);
    margin: 0 0 8px 0;
    line-height: 1.4;
    font-weight: 500;
  }

  .showcase-description {
    font-size: 15px;
    color: rgba(255, 255, 255, 0.8);
    margin: 0 0 28px 0;
    line-height: 1.5;
  }

  .feature-tags {
    display: flex;
    gap: 12px;
    margin-bottom: 40px;
    flex-wrap: wrap;
  }

  .tag {
    display: inline-block;
    padding: 8px 20px;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 20px;
    color: #fff;
    font-size: 14px;
    font-weight: 500;
  }

  .login-features {
    margin-top: 8px;
    padding: 0;

    .feature-item {
      display: flex;
      align-items: center;
      margin-bottom: 12px;
      font-size: 14px;
      color: rgba(255, 255, 255, 0.88);

      &:last-child {
        margin-bottom: 0;
      }

      .feature-icon {
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.25);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 700;
        margin-right: 10px;
        flex-shrink: 0;
      }

      .feature-text {
        line-height: 1.4;
      }
    }
  }

  /* 右侧表单区 */
  .form-section {
    flex: 0 0 380px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px 32px;
    box-sizing: border-box;
    position: relative;
    background: rgba(255, 255, 255, 0.97);
  }

  .form-panel {
    width: 100%;
    position: relative;
    z-index: 2;
  }

  .form-card {
    border-radius: 0;
    padding: 0;
    box-shadow: none;
    box-sizing: border-box;
    width: 100%;
  }

  .form-header {
    text-align: center;
    margin-bottom: 28px;
  }

  .form-title {
    font-size: 24px;
    font-weight: 600;
    color: #1d2129;
    margin: 0 0 6px 0;
  }

  .form-welcome {
    font-size: 13px;
    color: #86909c;
    margin: 0;
  }

  .form-content {
    :deep(.n-form-item) {
      margin-bottom: 18px;
    }

    :deep(.n-form-item-label) {
      font-size: 14px;
      color: #1d2129;
      font-weight: 500;
      margin-bottom: 6px;
      padding: 0;
    }

    :deep(.n-form-item-feedback-wrapper) {
      min-height: 18px;
    }

    :deep(.n-input) {
      border: 1px solid #e5e6eb;
      border-radius: 8px;
      transition: all 0.2s;

      &:hover {
        border-color: #18a058;
      }

      &:focus-within {
        border-color: #18a058;
        box-shadow: 0 0 0 3px rgba(24, 160, 88, 0.1);
      }
    }

    :deep(.n-input__input-el) {
      font-size: 15px;
    }
  }

  .submit-button {
    height: 46px;
    border-radius: 8px;
    font-size: 16px;
    font-weight: 500;
    margin: 8px 0 16px 0;
  }

  .form-options {
    display: flex;
    justify-content: center;
    align-items: center;
    margin-top: 4px;
    font-size: 14px;
    color: #86909c;
  }

  /* 响应式 */
  @media (max-width: 1024px) {
    .showcase-title {
      font-size: 32px;
    }
  }

  @media (max-width: 768px) {
    .login-card-wrapper {
      width: 100%;
      flex-direction: column;
      min-height: auto;
    }

    .knowledge-node:nth-of-type(n + 9) {
      display: none;
    }

    .connection-line:nth-of-type(n + 9) {
      display: none;
    }

    .showcase-section {
      padding: 36px 28px 24px;
    }

    .showcase-title {
      font-size: 26px;
    }

    .showcase-subtitle {
      font-size: 18px;
    }

    .feature-tags {
      margin-bottom: 20px;
    }

    .login-features {
      display: none;
    }

    .form-section {
      flex: 0 0 auto;
      padding: 28px 24px 36px;
    }
  }

  @media (max-width: 480px) {
    .animated-bg {
      display: none;
    }

    .login-layout {
      padding: 12px;
    }

    .showcase-section {
      padding: 28px 20px 16px;
    }

    .showcase-title {
      font-size: 22px;
    }

    .tag {
      font-size: 12px;
      padding: 6px 16px;
    }

    .form-section {
      padding: 24px 20px 28px;
    }

    .form-header {
      margin-bottom: 24px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .knowledge-node,
    .connection-line {
      animation: none !important;
      transition: none !important;
    }
  }
</style>
