<template>
  <n-modal v-model:show="visible" :mask-closable="false" :close-on-esc="false" transform-origin="center">
    <div class="captcha-modal">
      <!-- 顶部装饰渐变条 -->
      <div class="captcha-topbar">
        <div class="topbar-inner">
          <div class="topbar-icon">
            <n-icon size="20"><ShieldCheckmarkOutline /></n-icon>
          </div>
          <div class="topbar-text">
            <span class="topbar-title">安全验证</span>
            <span class="topbar-sub">为了您的账户安全，请完成下方验证</span>
          </div>
        </div>
      </div>

      <div class="captcha-content">
        <!-- 提示文字 -->
        <div class="captcha-tip" v-if="tipChars.length">
          <span class="tip-label">请依次点击</span>
          <transition-group name="char-pop" tag="div" class="target-chars">
            <span
              v-for="(char, index) in tipChars"
              :key="char + index"
              class="target-char"
              :class="{ 'is-done': index < clickPoints.length, 'is-current': index === clickPoints.length }"
            >
              {{ char }}
            </span>
          </transition-group>
        </div>

        <!-- 验证码图片 -->
        <div class="captcha-stage">
          <div class="captcha-image-wrapper" v-if="captchaImg">
            <img
              ref="captchaImgRef"
              :src="captchaImg"
              class="captcha-image"
              @click="handleImageClick"
              draggable="false"
            />
            <!-- 点击标记 -->
            <transition-group name="marker-pop" tag="div">
              <div
                v-for="(point, index) in clickPoints"
                :key="index"
                class="click-marker"
                :style="{ left: point.x + 'px', top: point.y + 'px' }"
              >
                <span class="marker-num">{{ index + 1 }}</span>
              </div>
            </transition-group>
            <!-- 图片遮罩边框 -->
            <div class="image-glow"></div>
          </div>
          <div class="captcha-loading" v-else>
            <n-spin size="medium" />
            <span class="loading-text">正在生成验证码...</span>
          </div>
        </div>

        <!-- 错误提示 -->
        <transition name="fade-slide">
          <div class="captcha-hint" v-if="errorMsg">
            <n-icon><AlertCircleOutline /></n-icon>
            <span>{{ errorMsg }}</span>
          </div>
        </transition>
      </div>

      <!-- 底部操作栏 -->
      <div class="captcha-footer">
        <div class="captcha-status">
          <transition name="fade" mode="out-in">
            <span v-if="clickPoints.length > 0" :key="clickPoints.length" class="status-progress">
              <span class="progress-num">{{ clickPoints.length }}</span>
              <span class="progress-sep">/</span>
              <span class="progress-total">{{ tipChars.length }}</span>
              <span class="progress-text">已选择</span>
            </span>
            <span v-else key="empty" class="status-empty">
              <n-icon size="14"><HandRightOutline /></n-icon>
              点击图片中的汉字
            </span>
          </transition>
        </div>
        <div class="captcha-actions">
          <n-button size="small" quaternary @click="refreshCaptcha" class="action-btn refresh-btn">
            <template #icon>
              <n-icon><RefreshOutline /></n-icon>
            </template>
            换一批
          </n-button>
          <n-button size="small" tertiary @click="handleCancel" class="action-btn">
            取消
          </n-button>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script lang="ts" setup>
  import { ref, watch, nextTick } from 'vue';
  import {
    RefreshOutline,
    ShieldCheckmarkOutline,
    AlertCircleOutline,
    HandRightOutline,
  } from '@vicons/ionicons5';
  import { getCaptcha } from '@/api/user';

  interface ClickPoint {
    x: number;
    y: number;
  }

  const props = defineProps<{
    show: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:show', value: boolean): void;
    (e: 'success', data: { key: string; points: ClickPoint[] }): void;
    (e: 'cancel'): void;
  }>();

  const visible = ref(false);
  const captchaImgRef = ref<HTMLImageElement | null>(null);
  const captchaImg = ref('');
  const captchaKey = ref('');
  const tipChars = ref<string[]>([]);
  const clickPoints = ref<ClickPoint[]>([]);
  const errorMsg = ref('');

  // 从后端获取验证码
  const generateCaptcha = async () => {
    errorMsg.value = '';
    clickPoints.value = [];
    captchaImg.value = '';
    captchaKey.value = '';
    tipChars.value = [];

    try {
      const res = await getCaptcha();

      if (res.code === 0 && res.data) {
        captchaKey.value = res.data.key;
        captchaImg.value = res.data.img;
        tipChars.value = res.data.tipChars || [];
      } else {
        errorMsg.value = res.message || '获取验证码失败，请重试';
      }
    } catch (e) {
      console.error('获取验证码失败:', e);
      errorMsg.value = '获取验证码失败，请重试';
    }
  };

  // 处理图片点击
  const handleImageClick = (e: MouseEvent) => {
    if (clickPoints.value.length >= tipChars.value.length) return;

    const img = captchaImgRef.value;
    if (!img) return;

    const rect = img.getBoundingClientRect();
    // 计算相对于图片左上角的坐标（考虑图片缩放）
    const scaleX = img.naturalWidth / rect.width;
    const scaleY = img.naturalHeight / rect.height;
    const x = Math.round((e.clientX - rect.left) * scaleX);
    const y = Math.round((e.clientY - rect.top) * scaleY);

    clickPoints.value.push({ x, y });
    errorMsg.value = '';

    // 选满后自动验证
    if (clickPoints.value.length === tipChars.value.length) {
      submitCaptcha();
    }
  };

  // 提交验证
  const submitCaptcha = () => {
    const key = captchaKey.value;
    const points = [...clickPoints.value];
    visible.value = false;
    captchaKey.value = '';
    captchaImg.value = '';
    emit('success', { key, points });
  };

  // 刷新验证码
  const refreshCaptcha = () => {
    generateCaptcha();
  };

  // 取消
  const handleCancel = () => {
    visible.value = false;
    emit('cancel');
  };

  // 监听show属性变化
  watch(
    () => props.show,
    (val) => {
      visible.value = val;
      if (val) {
        nextTick(() => {
          generateCaptcha();
        });
      }
    }
  );

  watch(visible, (val) => {
    emit('update:show', val);
  });
</script>

<style lang="less" scoped>
  .captcha-modal {
    width: 420px;
    background: #fff;
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 24px 70px -10px rgba(32, 50, 96, 0.22), 0 8px 24px rgba(0, 0, 0, 0.06);
  }

  .captcha-topbar {
    padding: 22px 26px 20px;
    background: linear-gradient(135deg, #5b6cff 0%, #3b82f6 55%, #22d3ee 100%);
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: -40px;
      right: -30px;
      width: 140px;
      height: 140px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.1);
    }

    &::after {
      content: '';
      position: absolute;
      bottom: -50px;
      right: 40px;
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.08);
    }

    .topbar-inner {
      display: flex;
      align-items: center;
      gap: 12px;
      position: relative;
      z-index: 1;
    }

    .topbar-icon {
      width: 42px;
      height: 42px;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.22);
      backdrop-filter: blur(10px);
      border: 1px solid rgba(255, 255, 255, 0.25);
      color: #fff;
    }

    .topbar-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .topbar-title {
      font-size: 17px;
      font-weight: 600;
      color: #fff;
      letter-spacing: 0.5px;
    }

    .topbar-sub {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.85);
    }
  }

  .captcha-content {
    padding: 22px 26px 14px;
  }

  .captcha-tip {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 16px;

    .tip-label {
      font-size: 13px;
      color: #8a92a6;
      flex-shrink: 0;
    }

    .target-chars {
      display: inline-flex;
      gap: 8px;
    }

    .target-char {
      width: 34px;
      height: 34px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      font-weight: 600;
      color: #364152;
      border-radius: 9px;
      background: #f1f3f9;
      border: 1.5px solid transparent;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &.is-current {
        background: #eff4ff;
        border-color: #5b6cff;
        color: #5b6cff;
        box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.12);
        transform: translateY(-1px);
      }

      &.is-done {
        background: linear-gradient(135deg, #36cfc9, #52c41a);
        border-color: transparent;
        color: #fff;
        box-shadow: 0 3px 10px rgba(82, 196, 26, 0.35);
      }
    }
  }

  .captcha-stage {
    display: flex;
    justify-content: center;
  }

  .captcha-image-wrapper {
    position: relative;
    display: inline-block;
    cursor: crosshair;
    border-radius: 14px;
    overflow: hidden;
    border: 1px solid #eef0f5;
    box-shadow: 0 4px 16px rgba(32, 50, 96, 0.08);

    &:hover .image-glow {
      opacity: 1;
    }
  }

  .captcha-image {
    display: block;
    width: 300px;
    height: 180px;
    user-select: none;
    -webkit-user-drag: none;
  }

  .image-glow {
    position: absolute;
    inset: 0;
    border-radius: 14px;
    box-shadow: inset 0 0 0 2px rgba(91, 108, 255, 0.4);
    opacity: 0;
    transition: opacity 0.25s ease;
    pointer-events: none;
  }

  .click-marker {
    position: absolute;
    transform: translate(-50%, -50%);
    z-index: 10;
    pointer-events: none;

    .marker-num {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      font-size: 13px;
      font-weight: 700;
      color: #fff;
      background: linear-gradient(135deg, #5b6cff, #3b82f6);
      box-shadow: 0 3px 10px rgba(91, 108, 255, 0.5), 0 0 0 3px rgba(255, 255, 255, 0.85);
    }
  }

  .captcha-loading {
    width: 300px;
    height: 180px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    background: linear-gradient(135deg, #f8f9fb, #eef1f6);
    border-radius: 14px;
    border: 2px dashed #dee2ea;

    .loading-text {
      color: #98a0b3;
      font-size: 13px;
    }
  }

  .captcha-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 14px;
    padding: 9px 14px;
    border-radius: 10px;
    background: linear-gradient(135deg, #fff1f0, #ffeee9);
    border: 1px solid #ffccc7;
    font-size: 13px;
    color: #cf1322;
    font-weight: 500;
  }

  .captcha-footer {
    padding: 14px 26px 18px;
    background: linear-gradient(180deg, #fbfcfe, #f5f7fb);
    border-top: 1px solid #eef0f5;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .captcha-status {
    .status-empty {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 13px;
      color: #98a0b3;
    }

    .status-progress {
      display: inline-flex;
      align-items: baseline;
      gap: 2px;

      .progress-num {
        font-size: 20px;
        font-weight: 700;
        color: #5b6cff;
        line-height: 1;
      }

      .progress-sep {
        font-size: 14px;
        color: #c0c5d2;
        margin: 0 1px;
      }

      .progress-total {
        font-size: 14px;
        font-weight: 600;
        color: #8a92a6;
      }

      .progress-text {
        font-size: 12px;
        color: #98a0b3;
        margin-left: 6px;
      }
    }
  }

  .captcha-actions {
    display: flex;
    gap: 8px;

    .action-btn {
      border-radius: 9px;
    }
  }

  /* 汉字格子弹入 */
  .char-pop-enter-active {
    transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
  .char-pop-enter-from {
    opacity: 0;
    transform: scale(0.4) translateY(8px);
  }

  /* 点击标记弹入 */
  .marker-pop-enter-active {
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
  .marker-pop-enter-from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0);
  }

  /* 错误提示 */
  .fade-slide-enter-active {
    transition: all 0.3s ease;
  }
  .fade-slide-enter-from {
    opacity: 0;
    transform: translateY(-6px);
  }

  /* 状态文字 */
  .fade-enter-active,
  .fade-leave-active {
    transition: opacity 0.2s ease;
  }
  .fade-enter-from,
  .fade-leave-to {
    opacity: 0;
  }
</style>
