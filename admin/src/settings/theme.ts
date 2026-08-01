import type { GlobalThemeOverrides } from 'naive-ui';
import { lighten, darken } from '@/utils/index';

/**
 * 翡翠绿（Emerald）Apple 风主题覆盖
 * 主色 #059669，沉稳高级；圆角 8-12px；柔和扩散阴影；过渡弹性曲线
 * 由 App.vue 注入 NConfigProvider，作用于全部 Naive UI 组件
 */
export function buildThemeOverrides(appTheme: string): GlobalThemeOverrides {
  const primaryHover = lighten(appTheme, 12);
  const primaryPressed = darken(appTheme, 8);
  const primarySuppl = lighten(appTheme, 6);

  // 翡翠绿柔和聚焦光环（用于输入框等 focus 态）
  const primaryAlpha = hexToRgba(appTheme, 0.16);

  return {
    common: {
      // 主色阶梯
      primaryColor: appTheme,
      primaryColorHover: primaryHover,
      primaryColorPressed: primaryPressed,
      primaryColorSuppl: primarySuppl,

      // 圆角基准（Apple 化：偏大、柔和）
      borderRadius: '8px',
      borderRadiusSmall: '6px',

      // 字重
      fontWeightStrong: '600',

      // 柔和扩散阴影（替代默认偏硬阴影）：三级
      boxShadow1: '0 1px 2px rgba(0, 0, 0, 0.04), 0 2px 8px rgba(0, 0, 0, 0.04)',
      boxShadow2: '0 2px 8px rgba(0, 0, 0, 0.06), 0 8px 24px rgba(0, 0, 0, 0.06)',
      boxShadow3: '0 4px 16px rgba(0, 0, 0, 0.08), 0 12px 40px rgba(0, 0, 0, 0.1)',

      // 过渡曲线：Apple 风弹性
      cubicBezierEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
      cubicBezierEaseOut: 'cubic-bezier(0, 0, 0.2, 1)',
      cubicBezierEaseIn: 'cubic-bezier(0.4, 0, 1, 1)',

      // 翡翠绿聚焦光环
      focusColor: primaryAlpha,
    },
    Card: {
      borderRadius: '12px',
      // 弱化边框，靠阴影与背景区分层级（Apple 风无硬边）
      borderColor: 'transparent',
      colorEmbedded: 'transparent',
    },
    Button: {
      borderRadiusMedium: '8px',
      borderRadiusLarge: '10px',
      borderRadiusSmall: '6px',
      borderRadiusTiny: '6px',
      fontWeight: '500',
      // 柔和 hover/pressed 过渡
      colorHover: primaryHover,
      colorPressed: primaryPressed,
      colorFocus: primaryHover,
      // 次级按钮的 focus 光环
      textColorPrimary: '#ffffff',
      colorPrimary: appTheme,
    },
    Input: {
      borderRadius: '8px',
      borderHover: `1px solid ${primaryHover}`,
      borderFocus: `1px solid ${appTheme}`,
      boxShadowFocus: `0 0 0 3px ${primaryAlpha}`,
      boxShadowHover: `0 0 0 2px ${primaryAlpha}`,
    },
    InputNumber: {
      borderRadius: '8px',
    },
    Select: {
      peers: {
        InternalSelection: {
          borderRadius: '8px',
          borderHover: `1px solid ${primaryHover}`,
          borderFocus: `1px solid ${appTheme}`,
          boxShadowFocus: `0 0 0 3px ${primaryAlpha}`,
          boxShadowHover: `0 0 0 2px ${primaryAlpha}`,
        },
      },
    },
    Cascader: {
      peers: {
        InternalSelection: {
          borderRadius: '8px',
          borderHover: `1px solid ${primaryHover}`,
          borderFocus: `1px solid ${appTheme}`,
          boxShadowFocus: `0 0 0 3px ${primaryAlpha}`,
        },
      },
    },
    Modal: {
      borderRadius: '16px',
    },
    Drawer: {
      borderRadius: '16px',
    },
    Popover: {
      borderRadius: '12px',
      boxShadow: '0 4px 16px rgba(0, 0, 0, 0.08), 0 12px 40px rgba(0, 0, 0, 0.1)',
    },
    Dropdown: {
      borderRadius: '12px',
    },
    DataTable: {
      borderRadius: '12px',
      // 表头柔和底色
      thColor: 'rgba(0, 0, 0, 0.02)',
      thFontWeight: '600',
    },
    Tag: {
      borderRadius: '6px',
      // pill 风圆角（大号标签）
      borderRadiusRound: '20px',
    },
    Switch: {
      railColorActive: appTheme,
      loadingColor: appTheme,
    },
    Tabs: {
      tabTextColorActiveLine: appTheme,
      tabTextColorHoverLine: primaryHover,
      barColor: appTheme,
    },
    Menu: {
      // 选中态：柔和翡翠绿底 + 主色文字
      itemColorActive: hexToRgba(appTheme, 0.1),
      itemColorActiveHover: hexToRgba(appTheme, 0.14),
      itemColorActiveCollapsed: hexToRgba(appTheme, 0.1),
      itemTextColorActive: appTheme,
      itemTextColorActiveHover: appTheme,
      itemTextColorChildActive: appTheme,
      itemTextColorChildActiveHover: appTheme,
      itemIconColorActive: appTheme,
      itemIconColorActiveHover: appTheme,
      itemIconColorChildActive: appTheme,
      itemIconColorChildActiveHover: appTheme,
      borderRadius: '8px',
    },
    Checkbox: {
      colorChecked: appTheme,
      colorCheckedHover: primaryHover,
      borderChecked: `1px solid ${appTheme}`,
      borderFocus: `1px solid ${appTheme}`,
      boxShadowFocus: `0 0 0 2px ${primaryAlpha}`,
    },
    Radio: {
      dotColorActive: appTheme,
      dotColorActiveHover: primaryHover,
      boxShadowFocus: `0 0 0 2px ${primaryAlpha}`,
    },
    Slider: {
      fillColor: appTheme,
      fillColorHover: primaryHover,
      handleColor: appTheme,
    },
    Progress: {
      fillColor: appTheme,
    },
    LoadingBar: {
      colorLoading: appTheme,
    },
    Message: {
      borderRadius: '12px',
      boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 8px 24px rgba(0, 0, 0, 0.06)',
    },
    Notification: {
      borderRadius: '12px',
      boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 8px 24px rgba(0, 0, 0, 0.06)',
    },
    Tooltip: {
      borderRadius: '8px',
    },
    Pagination: {
      itemBorderRadius: '8px',
      itemColorActive: appTheme,
      itemTextColorActive: '#ffffff',
    },
    Avatar: {
      color: appTheme,
    },
    Divider: {
      color: 'rgba(0, 0, 0, 0.06)',
    },
  };
}

/** hex 转 rgba */
function hexToRgba(hex: string, alpha: number): string {
  const c = hex.replace('#', '');
  const full =
    c.length === 3
      ? c
          .split('')
          .map((x) => x + x)
          .join('')
      : c;
  const r = parseInt(full.substring(0, 2), 16);
  const g = parseInt(full.substring(2, 4), 16);
  const b = parseInt(full.substring(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
