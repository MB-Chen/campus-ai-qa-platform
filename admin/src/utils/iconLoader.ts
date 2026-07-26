import { renderIcon } from '@/utils/index';
import { h } from 'vue';

// 缓存已加载的图标
const iconCache = new Map<string, any>();

/**
 * 动态加载 @vicons/antd 图标
 * @param iconName 图标名称，如 'DeploymentUnitOutlined'
 * @returns 返回渲染函数或null
 */
export async function dynamicLoadIcon(iconName: string) {
  if (!iconName) {
    return null;
  }

  // 检查缓存
  if (iconCache.has(iconName)) {
    return iconCache.get(iconName);
  }

  try {
    // 动态导入 @vicons/antd 中的图标
    // 尝试两种可能的导入路径
    let iconModule;
    try {
      iconModule = await import(/* @vite-ignore */ `@vicons/antd/es/icons/${iconName}`);
    } catch (e) {
      // 如果第一种路径失败，尝试第二种路径
      iconModule = await import(/* @vite-ignore */ `@vicons/antd/${iconName}`);
    }

    const IconComponent = iconModule.default || iconModule[iconName];

    if (IconComponent) {
      const renderedIcon = renderIcon(IconComponent);
      // 缓存结果
      iconCache.set(iconName, renderedIcon);
      return renderedIcon;
    }
  } catch (error) {
    console.warn(`图标 ${iconName} 加载失败:`, error);
  }

  // 如果加载失败，返回一个默认图标
  return getDefaultIcon();
}

/**
 * 同步方式加载图标（优先从缓存获取）
 * @param iconName 图标名称
 * @returns 返回缓存的图标或默认图标
 */
export function getIcon(iconName: string) {
  if (!iconName) {
    return getDefaultIcon();
  }

  // 优先返回缓存中的图标
  if (iconCache.has(iconName)) {
    return iconCache.get(iconName);
  }

  // 异步预加载图标
  dynamicLoadIcon(iconName).then((icon) => {
    if (icon) {
      // 图标加载完成后，通知组件更新（如果需要的话）
      return icon;
    }
  });

  // 立即返回默认图标
  return getDefaultIcon();
}

/**
 * 获取默认图标
 */
function getDefaultIcon() {
  // 如果缓存中有默认图标，直接返回
  const defaultIconName = 'AppstoreOutlined';
  if (iconCache.has(defaultIconName)) {
    return iconCache.get(defaultIconName);
  }

  // 返回一个简单的占位符图标
  return () => h('span', { style: 'font-size: 16px;' }, '📁');
}

/**
 * 批量预加载图标
 * @param iconNames 图标名称数组
 */
export async function preloadIcons(iconNames: string[]) {
  const promises = iconNames.map((name) => dynamicLoadIcon(name));
  await Promise.allSettled(promises);
}

/**
 * 获取所有可用的图标名称列表（用于调试）
 */
export async function getAvailableIcons() {
  try {
    // 这里可以返回一个常用图标列表，或者通过其他方式获取
    return [
      'DashboardOutlined',
      'SafetyCertificateOutlined',
      'AppstoreOutlined',
      'ShopOutlined',
      'DeploymentUnitOutlined',
      'UserOutlined',
      'SettingOutlined',
      'FileTextOutlined',
      'BarChartOutlined',
      'PieChartOutlined',
      'LineChartOutlined',
      'TeamOutlined',
      'IdcardOutlined',
      'CalendarOutlined',
      'ClockCircleOutlined',
      'EnvironmentOutlined',
      'MailOutlined',
      'PhoneOutlined',
      'GlobalOutlined',
      'LockOutlined',
      'UnlockOutlined',
      'EyeOutlined',
      'EyeInvisibleOutlined',
      'EditOutlined',
      'DeleteOutlined',
      'PlusOutlined',
      'MinusOutlined',
      'CheckOutlined',
      'CloseOutlined',
      'UpOutlined',
      'DownOutlined',
      'LeftOutlined',
      'RightOutlined',
      'SearchOutlined',
      'FilterOutlined',
      'ReloadOutlined',
      'DownloadOutlined',
      'UploadOutlined',
      'ExportOutlined',
      'ImportOutlined',
      'PrinterOutlined',
      'ShareAltOutlined',
      'LinkOutlined',
      'DisconnectOutlined',
      'ApiOutlined',
      'CodeOutlined',
      'DatabaseOutlined',
      'ServerOutlined',
      'CloudOutlined',
      'MobileOutlined',
      'TabletOutlined',
      'LaptopOutlined',
      'DesktopOutlined',
      'MonitorOutlined',
      'CameraOutlined',
      'VideoCameraOutlined',
      'AudioOutlined',
      'SoundOutlined',
      'PictureOutlined',
      'FileImageOutlined',
      'FilePdfOutlined',
      'FileExcelOutlined',
      'FileWordOutlined',
      'FilePptOutlined',
      'FileZipOutlined',
      'FileUnknownOutlined',
      'FolderOutlined',
      'FolderOpenOutlined',
      'InboxOutlined',
      'HddOutlined',
      'UsbOutlined',
      'WifiOutlined',
      'BluetoothOutlined',
      'SignalFilled',
      'ThunderboltOutlined',
      'PoweroffOutlined',
      'WarningOutlined',
      'ExclamationCircleOutlined',
      'InfoCircleOutlined',
      'CheckCircleOutlined',
      'CloseCircleOutlined',
      'QuestionCircleOutlined',
      'PlusCircleOutlined',
      'MinusCircleOutlined',
      'EditFilled',
      'DeleteFilled',
      'EyeFilled',
      'EyeInvisibleFilled',
      'HeartOutlined',
      'HeartFilled',
      'StarOutlined',
      'StarFilled',
      'LikeOutlined',
      'LikeFilled',
      'DislikeOutlined',
      'DislikeFilled',
      'FireOutlined',
      'FireFilled',
      'FlagOutlined',
      'FlagFilled',
      'TagOutlined',
      'TagsOutlined',
      'BookOutlined',
      'BookFilled',
      'ReadOutlined',
      'WriteOutlined',
      'EditSquareOutlined',
      'HighlightOutlined',
      'CopyOutlined',
      'ScissorOutlined',
      'SnippetsOutlined',
      'DiffOutlined',
      'MergeOutlined',
      'AlignLeftOutlined',
      'AlignCenterOutlined',
      'AlignRightOutlined',
      'BgcColorOutlined',
      'FontSizeOutlined',
      'BoldOutlined',
      'ItalicOutlined',
      'UnderlineOutlined',
      'StrikethroughOutlined',
      'RedoOutlined',
      'UndoOutlined',
      'ZoomInOutlined',
      'ZoomOutOutlined',
      'FullscreenOutlined',
      'FullscreenExitOutlined',
      'CompressOutlined',
      'ExpandOutlined',
      'ArrowsAltOutlined',
      'ShrinkOutlined',
      'AsideOutlined',
      'MenuFoldOutlined',
      'MenuUnfoldOutlined',
      'ColumnWidthOutlined',
      'ColumnHeightOutlined',
      'AreaChartOutlined',
      'ProjectOutlined',
      'ProjectFilled',
      'ProjectTwoTone',
      'FundOutlined',
      'FundFilled',
      'FundTwoTone',
      'FundViewOutlined',
      'MoneyCollectFilled',
      'MoneyCollectOutlined',
      'CreditCardFilled',
      'CreditCardOutlined',
      'BankFilled',
      'BankOutlined',
      'WalletFilled',
      'WalletOutlined',
      'PayCircleFilled',
      'PayCircleOutlined',
      'DollarOutlined',
      'EuroOutlined',
      'PoundOutlined',
      'GiftFilled',
      'GiftOutlined',
      'GiftTwoTone',
      'RedEnvelopeFilled',
      'RedEnvelopeOutlined',
      'ShoppingCartOutlined',
      'ShoppingCartFilled',
      'ShoppingTwoTone',
      'ShoppingFilled',
      'ShopTwoTone',
      'GiftFilledTwoTone',
      'CustomerServiceFilled',
      'CustomerServiceOutlined',
      'CustomerServiceTwoTone',
      'QqOutlined',
      'QqCircleFilled',
      'QqSquareFilled',
      'WechatOutlined',
      'WechatFilled',
      'WeiboCircleOutlined',
      'WeiboCircleFilled',
      'WeiboSquareOutlined',
      'WeiboSquareFilled',
      'TwitterOutlined',
      'TwitterCircleFilled',
      'TwitterSquareFilled',
      'GooglePlusOutlined',
      'GooglePlusCircleFilled',
      'GooglePlusSquareFilled',
      'GoogleOutlined',
      'GoogleCircleFilled',
      'GoogleSquareFilled',
      'FacebookFilled',
      'FacebookOutlined',
      'SkypeOutlined',
      'SkypeFilled',
      'LinkedinOutlined',
      'LinkedinFilled',
      'GithubOutlined',
      'GithubFilled',
      'AntDesignOutlined',
      'AntDesignFilled',
      'AntDesignTwoTone',
      'AliwangwangOutlined',
      'AliwangwangFilled',
      'AlipayCircleFilled',
      'AlipayOutlined',
      'AlipaySquareFilled',
      'TaobaoCircleOutlined',
      'TaobaoCircleFilled',
      'TaobaoSquareOutlined',
      'TaobaoSquareFilled',
      'Html5Outlined',
      'Html5Filled',
      'WeiboCircleTwoTone',
      'TwitterSquareTwoTone',
      'WeiboSquareTwoTone',
      'BehanceSquareOutlined',
      'BehanceSquareFilled',
      'BehanceSquareTwoTone',
      'DribbbleSquareOutlined',
      'DribbbleSquareFilled',
      'DribbbleSquareTwoTone',
      'InstagramOutlined',
      'InstagramFilled',
      'AntDesign',
      'AntDesignSquareFilled',
      'CodepenCircleFilled',
      'CodepenSquareFilled',
      'CodeSandboxSquareFilled',
      'CodeSandboxCircleFilled',
      'CodeSandboxOutlined',
      'ContainerOutlined',
      'DropboxOutlined',
      'DropboxCircleFilled',
      'DropboxSquareFilled',
      'GooglePlusSquareTwoTone',
      'GooglePlusCircleTwoTone',
      'GooglePlusTwoTone',
      'FacebookTwoTone',
      'DribbbleOutlined',
      'DribbbleFilled',
      'DribbbleCircleOutlined',
      'DribbbleCircleFilled',
      'InstagramTwoTone',
      'BehanceCircleOutlined',
      'BehanceCircleFilled',
      'BehanceTwoTone',
      'GitlabOutlined',
      'GitlabFilled',
      'GithubTwoTone',
      'TaobaoCircleTwoTone',
      'TwitterCircleTwoTone',
      'WeiboOutlined',
      'WeiboFilled',
      'YoutubeOutlined',
      'YoutubeFilled',
      'YoutubeTwoTone',
      'ZhihuOutlined',
      'ZhihuSquareFilled',
      'ZhihuCircleFilled',
      'MediumOutlined',
      'MediumSquareFilled',
      'MediumCircleFilled',
      'MediumTwoTone',
      'QqOutlinedTwoTone',
      'WeiboSquareTwoTone',
      'QqSquareFilled',
      'QqCircleFilled',
      'LinkedinTwoTone',
      'FacebookSquareFilled',
      'TwitterSquareFilled',
      'TwitterCircleFilled',
      'SkypeSquareFilled',
      'SkypeCircleFilled',
      'BehanceSquareTwoTone',
      'InstagramSquareFilled',
      'InstagramCircleFilled',
      'InstagramTwoTone',
      'WechatOutlinedTwoTone',
      'AppleOutlined',
      'AppleFilled',
      'AppleTwoTone',
      'AndroidOutlined',
      'AndroidFilled',
      'AndroidTwoTone',
      'WindowsOutlined',
      'WindowsFilled',
      'WindowsTwoTone',
      'IeOutlined',
      'IeFilled',
      'IeSquareFilled',
      'ChromeOutlined',
      'ChromeFilled',
      'ChromeSquareFilled',
      'GithubSquareFilled',
      'ChromeTwoTone',
      'SafariOutlined',
      'SafariFilled',
      'SafariSquareFilled',
      'SafariTwoTone',
      'EdgeOutlined',
      'EdgeFilled',
      'EdgeSquareFilled',
      'EdgeTwoTone',
      'AndroidSquareFilled',
      'AppleSquareFilled',
      'WindowsSquareFilled',
      'YandexOutlined',
      'YandexFilled',
      'YandexSquareFilled',
      'YandexTwoTone',
      'IeSquareTwoTone',
      'FacebookSquareTwoTone',
      'FacebookTwoTone',
      'AppleTwoTone',
      'AndroidTwoTone',
      'WindowsTwoTone',
      'WeiboSquareTwoTone',
      'TwitterSquareTwoTone',
      'YoutubeSquareTwoTone',
      'SkypeSquareTwoTone',
      'SkypeCircleTwoTone',
      'ChromeSquareTwoTone',
      'ChromeCircleTwoTone',
      'ChromeTwoTone',
      'SafariSquareTwoTone',
      'SafariCircleTwoTone',
      'SafariTwoTone',
      'EdgeSquareTwoTone',
      'EdgeCircleTwoTone',
      'EdgeTwoTone',
      'GooglePlusSquareTwoTone',
      'GooglePlusCircleTwoTone',
      'GooglePlusTwoTone',
      'WeiboCircleTwoTone',
      'TwitterCircleTwoTone',
      'YoutubeCircleTwoTone',
      'SkypeCircleTwoTone',
      'ChromeCircleTwoTone',
      'SafariCircleTwoTone',
      'EdgeCircleTwoTone',
      'FacebookCircleTwoTone',
      'InstagramCircleTwoTone',
      'GithubCircleTwoTone',
      'GoogleCircleTwoTone',
      'GooglePlusCircleTwoTone',
      'WeiboCircleTwoTone',
      'TwitterCircleTwoTone',
      'YoutubeCircleTwoTone',
      'SkypeCircleTwoTone',
      'ChromeCircleTwoTone',
      'SafariCircleTwoTone',
      'EdgeCircleTwoTone',
      'FacebookCircleTwoTone',
      'InstagramCircleTwoTone',
      'GithubCircleTwoTone',
      'GoogleCircleTwoTone',
      'GooglePlusCircleTwoTone',
    ];
  } catch (error) {
    console.error('获取可用图标列表失败:', error);
    return [];
  }
}
