<template>
  <div class="icon-selector">
    <n-input
      v-model:value="inputValue"
      placeholder="输入图标名称或点击左侧选择图标"
      clearable
      @update:value="handleInputChange"
    >
      <template #prefix>
        <n-button text size="small" @click="showModal = true" class="icon-trigger-btn">
          <component :is="currentIcon || AppstoreOutlined" />
        </n-button>
      </template>
    </n-input>

    <n-modal
      v-model:show="showModal"
      preset="card"
      style="width: 800px; max-height: 700px"
      title="选择图标"
      :bordered="false"
      :segmented="{ content: true }"
    >
      <div class="icon-selector-content">
        <div class="search-section">
          <n-input
            v-model:value="searchKeyword"
            placeholder="搜索图标..."
            clearable
            class="search-input"
          >
            <template #prefix>
              <n-icon :component="SearchOutlined" />
            </template>
          </n-input>
          <div class="icon-count"> 共 {{ filteredIcons.length }} 个图标 </div>
        </div>

        <div class="icon-grid">
          <div
            v-for="icon in filteredIcons"
            :key="icon.name"
            class="icon-item"
            :class="{ active: selectedIcon === icon.name }"
            @click="selectIcon(icon)"
          >
            <n-icon :component="icon.component" size="24" />
            <span class="icon-name">{{ icon.name }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="modal-footer">
          <n-button strong secondary @click="showModal = false">取消</n-button>
          <n-button strong secondary type="primary" @click="confirmIcon">确定</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, watch } from 'vue';
  import { NIcon, NInput, NModal, NButton } from 'naive-ui';
  import {
    AppstoreOutlined,
    SearchOutlined,
    // 基础图标
    HomeOutlined,
    UserOutlined,
    SettingOutlined,
    TeamOutlined,
    MenuOutlined,
    DashboardOutlined,
    FileTextOutlined,
    ShopOutlined,
    ShoppingCartOutlined,
    TagsOutlined,
    GiftOutlined,
    CrownOutlined,
    StarOutlined,
    HeartOutlined,
    LikeOutlined,
    DislikeOutlined,
    EyeOutlined,
    EyeInvisibleOutlined,
    EditOutlined,
    DeleteOutlined,
    PlusOutlined,
    MinusOutlined,
    CloseOutlined,
    CheckOutlined,
    ExclamationCircleOutlined,
    InfoCircleOutlined,
    QuestionCircleOutlined,
    LoadingOutlined,
    ReloadOutlined,
    SyncOutlined,
    DownloadOutlined,
    UploadOutlined,
    ShareAltOutlined,
    LinkOutlined,
    CopyOutlined,
    ScissorOutlined,
    SnippetsOutlined,
    FileOutlined,
    FolderOutlined,
    FolderOpenOutlined,
    CalendarOutlined,
    ClockCircleOutlined,
    BellOutlined,
    NotificationOutlined,
    MailOutlined,
    MessageOutlined,
    PhoneOutlined,
    MobileOutlined,
    TabletOutlined,
    DesktopOutlined,
    LaptopOutlined,
    CameraOutlined,
    PictureOutlined,
    VideoCameraOutlined,
    PlayCircleOutlined,
    PauseCircleOutlined,
    StepForwardOutlined,
    StepBackwardOutlined,
    FastForwardOutlined,
    FastBackwardOutlined,
    UpOutlined,
    DownOutlined,
    LeftOutlined,
    RightOutlined,
    UpCircleOutlined,
    DownCircleOutlined,
    LeftCircleOutlined,
    RightCircleOutlined,
    ArrowUpOutlined,
    ArrowDownOutlined,
    ArrowLeftOutlined,
    ArrowRightOutlined,
    CaretUpOutlined,
    CaretDownOutlined,
    CaretLeftOutlined,
    CaretRightOutlined,
    // 编辑器图标
    AlignLeftOutlined,
    AlignCenterOutlined,
    AlignRightOutlined,
    BoldOutlined,
    ItalicOutlined,
    UnderlineOutlined,
    ClearOutlined,
    UndoOutlined,
    RedoOutlined,
    ZoomInOutlined,
    ZoomOutOutlined,
    FullscreenOutlined,
    FullscreenExitOutlined,
    CompressOutlined,
    ExpandOutlined,
    ColumnWidthOutlined,
    ColumnHeightOutlined,
    OrderedListOutlined,
    UnorderedListOutlined,
    NumberOutlined,
    CodeOutlined,
    TableOutlined,
    // 状态图标
    CheckCircleOutlined,
    CloseCircleOutlined,
    WarningOutlined,
    IssuesCloseOutlined,
    // 媒体图标
    AudioOutlined,
    SoundOutlined,
    PlayCircleFilled,
    StepForwardFilled,
    StepBackwardFilled,
    FastForwardFilled,
    FastBackwardFilled,
    // 操作图标
    FilterOutlined,
    MoreOutlined,
    EllipsisOutlined,
    MenuUnfoldOutlined,
    MenuFoldOutlined,
    SwapOutlined,
    SwapLeftOutlined,
    SwapRightOutlined,
    RetweetOutlined,
    RollbackOutlined,
    ForkOutlined,
    ShrinkOutlined,
    ArrowsAltOutlined,
    ExpandAltOutlined,
    // 安全图标
    LockOutlined,
    UnlockOutlined,
    KeyOutlined,
    SafetyCertificateOutlined,
    SecurityScanOutlined,
    VerifiedOutlined,
    // 社交图标
    WechatOutlined,
    AlipayOutlined,
    TaobaoOutlined,
    DingdingOutlined,
    YoutubeOutlined,
    ZhihuOutlined,
    WeiboOutlined,
    GithubOutlined,
    GitlabOutlined,
    // 金融图标
    DollarOutlined,
    EuroOutlined,
    PoundOutlined,
    CreditCardOutlined,
    BankOutlined,
    WalletOutlined,
    PayCircleOutlined,
    // 设备图标
    PrinterOutlined,
    ScanOutlined,
    QrcodeOutlined,
    BarcodeOutlined,
    // 图表图标
    RadarChartOutlined,
    DashboardFilled,
    AreaChartOutlined,
    PieChartFilled,
    BarChartOutlined,
    LineChartOutlined,
    // 天气图标
    CloudOutlined,
    ThunderboltOutlined,
    // 地图图标
    EnvironmentOutlined,
    CompassOutlined,
    GlobalOutlined,
    // 其他常用图标
    AlertOutlined,
    ApiOutlined,
    AppstoreAddOutlined,
    AppstoreFilled,
    AuditOutlined,
    BookOutlined,
    BugOutlined,
    BulbOutlined,
    CalculatorOutlined,
    CarOutlined,
    CarryOutOutlined,
    CloudDownloadOutlined,
    CloudServerOutlined,
    CloudSyncOutlined,
    CloudUploadOutlined,
    ClusterOutlined,
    CoffeeOutlined,
    ContactsOutlined,
    ContainerOutlined,
    ControlOutlined,
    CopyrightOutlined,
    CustomerServiceOutlined,
    DeploymentUnitOutlined,
    DisconnectOutlined,
    ExportOutlined,
    FileAddOutlined,
    FileDoneOutlined,
    FileExcelOutlined,
    FileExclamationOutlined,
    FileImageOutlined,
    FilePdfOutlined,
    FileProtectOutlined,
    FileSearchOutlined,
    FileSyncOutlined,
    FileUnknownOutlined,
    FileWordOutlined,
    FileZipOutlined,
    FireOutlined,
    FlagOutlined,
    FolderAddOutlined,
    FolderFilled,
    FolderViewOutlined,
    FundOutlined,
    FundProjectionScreenOutlined,
    FundViewOutlined,
    FunnelPlotOutlined,
    FunnelPlotFilled,
    GatewayOutlined,
    GoldOutlined,
    GroupOutlined,
    HistoryOutlined,
    HolderOutlined,
    HourglassOutlined,
    IdcardOutlined,
    ImportOutlined,
    InboxOutlined,
    InsertRowAboveOutlined,
    InsertRowBelowOutlined,
    InsertRowLeftOutlined,
    InsertRowRightOutlined,
    InsuranceOutlined,
    InteractionOutlined,
    LayoutOutlined,
    MedicineBoxOutlined,
    MehOutlined,
    MergeCellsOutlined,
    MoneyCollectOutlined,
    PaperClipOutlined,
    PartitionOutlined,
    PercentageOutlined,
    ProfileOutlined,
    ProjectOutlined,
    PropertySafetyOutlined,
    PullRequestOutlined,
    RadiusSettingOutlined,
    ReadOutlined,
    ReconciliationOutlined,
    RedEnvelopeOutlined,
    RestOutlined,
    RobotOutlined,
    RocketOutlined,
    SaveOutlined,
    ScheduleOutlined,
    SelectOutlined,
    SendOutlined,
    SkinOutlined,
    SolutionOutlined,
    SplitCellsOutlined,
    SubnodeOutlined,
    SwitcherOutlined,
    TagOutlined,
    ThunderboltFilled,
    ToolOutlined,
    TrademarkCircleOutlined,
    TransactionOutlined,
    TrophyOutlined,
    UserAddOutlined,
    UserDeleteOutlined,
    UserSwitchOutlined,
    WifiOutlined,
    WindowsOutlined,
    WomanOutlined,
  } from '@vicons/antd';

  // Props
  interface Props {
    icon?: string;
  }

  const props = withDefaults(defineProps<Props>(), {
    icon: '',
  });

  // Emits
  interface Emits {
    (e: 'update:icon', value: string): void;
  }

  const emit = defineEmits<Emits>();

  // State
  const showModal = ref(false);
  const selectedIcon = ref(props.icon);
  const searchKeyword = ref('');
  const inputValue = ref(props.icon);
  const tempSelectedIcon = ref(null);

  // Icons data - 只包含确实存在的图标
  const availableIcons = [
    // 默认图标
    { name: 'AppstoreOutlined', component: AppstoreOutlined },
    // 基础图标
    { name: 'HomeOutlined', component: HomeOutlined },
    { name: 'UserOutlined', component: UserOutlined },
    { name: 'SettingOutlined', component: SettingOutlined },
    { name: 'TeamOutlined', component: TeamOutlined },
    { name: 'MenuOutlined', component: MenuOutlined },
    { name: 'DashboardOutlined', component: DashboardOutlined },
    { name: 'FileTextOutlined', component: FileTextOutlined },
    { name: 'ShopOutlined', component: ShopOutlined },
    { name: 'ShoppingCartOutlined', component: ShoppingCartOutlined },
    { name: 'TagsOutlined', component: TagsOutlined },
    { name: 'GiftOutlined', component: GiftOutlined },
    { name: 'CrownOutlined', component: CrownOutlined },
    { name: 'StarOutlined', component: StarOutlined },
    { name: 'HeartOutlined', component: HeartOutlined },
    { name: 'LikeOutlined', component: LikeOutlined },
    { name: 'DislikeOutlined', component: DislikeOutlined },
    { name: 'EyeOutlined', component: EyeOutlined },
    { name: 'EyeInvisibleOutlined', component: EyeInvisibleOutlined },
    { name: 'EditOutlined', component: EditOutlined },
    { name: 'DeleteOutlined', component: DeleteOutlined },
    { name: 'PlusOutlined', component: PlusOutlined },
    { name: 'MinusOutlined', component: MinusOutlined },
    { name: 'CloseOutlined', component: CloseOutlined },
    { name: 'CheckOutlined', component: CheckOutlined },
    { name: 'ExclamationCircleOutlined', component: ExclamationCircleOutlined },
    { name: 'InfoCircleOutlined', component: InfoCircleOutlined },
    { name: 'QuestionCircleOutlined', component: QuestionCircleOutlined },
    { name: 'LoadingOutlined', component: LoadingOutlined },
    { name: 'ReloadOutlined', component: ReloadOutlined },
    { name: 'SyncOutlined', component: SyncOutlined },
    { name: 'DownloadOutlined', component: DownloadOutlined },
    { name: 'UploadOutlined', component: UploadOutlined },
    { name: 'ShareAltOutlined', component: ShareAltOutlined },
    { name: 'LinkOutlined', component: LinkOutlined },
    { name: 'CopyOutlined', component: CopyOutlined },
    { name: 'ScissorOutlined', component: ScissorOutlined },
    { name: 'SnippetsOutlined', component: SnippetsOutlined },
    { name: 'FileOutlined', component: FileOutlined },
    { name: 'FolderOutlined', component: FolderOutlined },
    { name: 'FolderOpenOutlined', component: FolderOpenOutlined },
    { name: 'CalendarOutlined', component: CalendarOutlined },
    { name: 'ClockCircleOutlined', component: ClockCircleOutlined },
    { name: 'BellOutlined', component: BellOutlined },
    { name: 'NotificationOutlined', component: NotificationOutlined },
    { name: 'MailOutlined', component: MailOutlined },
    { name: 'MessageOutlined', component: MessageOutlined },
    { name: 'PhoneOutlined', component: PhoneOutlined },
    { name: 'MobileOutlined', component: MobileOutlined },
    { name: 'TabletOutlined', component: TabletOutlined },
    { name: 'DesktopOutlined', component: DesktopOutlined },
    { name: 'LaptopOutlined', component: LaptopOutlined },
    { name: 'CameraOutlined', component: CameraOutlined },
    { name: 'PictureOutlined', component: PictureOutlined },
    { name: 'VideoCameraOutlined', component: VideoCameraOutlined },
    { name: 'PlayCircleOutlined', component: PlayCircleOutlined },
    { name: 'PauseCircleOutlined', component: PauseCircleOutlined },
    { name: 'StepForwardOutlined', component: StepForwardOutlined },
    { name: 'StepBackwardOutlined', component: StepBackwardOutlined },
    { name: 'FastForwardOutlined', component: FastForwardOutlined },
    { name: 'FastBackwardOutlined', component: FastBackwardOutlined },
    { name: 'UpOutlined', component: UpOutlined },
    { name: 'DownOutlined', component: DownOutlined },
    { name: 'LeftOutlined', component: LeftOutlined },
    { name: 'RightOutlined', component: RightOutlined },
    { name: 'UpCircleOutlined', component: UpCircleOutlined },
    { name: 'DownCircleOutlined', component: DownCircleOutlined },
    { name: 'LeftCircleOutlined', component: LeftCircleOutlined },
    { name: 'RightCircleOutlined', component: RightCircleOutlined },
    { name: 'ArrowUpOutlined', component: ArrowUpOutlined },
    { name: 'ArrowDownOutlined', component: ArrowDownOutlined },
    { name: 'ArrowLeftOutlined', component: ArrowLeftOutlined },
    { name: 'ArrowRightOutlined', component: ArrowRightOutlined },
    { name: 'CaretUpOutlined', component: CaretUpOutlined },
    { name: 'CaretDownOutlined', component: CaretDownOutlined },
    { name: 'CaretLeftOutlined', component: CaretLeftOutlined },
    { name: 'CaretRightOutlined', component: CaretRightOutlined },

    // 编辑器图标
    { name: 'AlignLeftOutlined', component: AlignLeftOutlined },
    { name: 'AlignCenterOutlined', component: AlignCenterOutlined },
    { name: 'AlignRightOutlined', component: AlignRightOutlined },
    { name: 'BoldOutlined', component: BoldOutlined },
    { name: 'ItalicOutlined', component: ItalicOutlined },
    { name: 'UnderlineOutlined', component: UnderlineOutlined },
    { name: 'ClearOutlined', component: ClearOutlined },
    { name: 'UndoOutlined', component: UndoOutlined },
    { name: 'RedoOutlined', component: RedoOutlined },
    { name: 'ZoomInOutlined', component: ZoomInOutlined },
    { name: 'ZoomOutOutlined', component: ZoomOutOutlined },
    { name: 'FullscreenOutlined', component: FullscreenOutlined },
    { name: 'FullscreenExitOutlined', component: FullscreenExitOutlined },
    { name: 'CompressOutlined', component: CompressOutlined },
    { name: 'ExpandOutlined', component: ExpandOutlined },
    { name: 'ColumnWidthOutlined', component: ColumnWidthOutlined },
    { name: 'ColumnHeightOutlined', component: ColumnHeightOutlined },
    { name: 'OrderedListOutlined', component: OrderedListOutlined },
    { name: 'UnorderedListOutlined', component: UnorderedListOutlined },
    { name: 'NumberOutlined', component: NumberOutlined },
    { name: 'CodeOutlined', component: CodeOutlined },
    { name: 'TableOutlined', component: TableOutlined },

    // 状态图标
    { name: 'CheckCircleOutlined', component: CheckCircleOutlined },
    { name: 'CloseCircleOutlined', component: CloseCircleOutlined },
    { name: 'WarningOutlined', component: WarningOutlined },
    { name: 'IssuesCloseOutlined', component: IssuesCloseOutlined },

    // 媒体图标
    { name: 'AudioOutlined', component: AudioOutlined },
    { name: 'SoundOutlined', component: SoundOutlined },
    { name: 'PlayCircleFilled', component: PlayCircleFilled },
    { name: 'StepForwardFilled', component: StepForwardFilled },
    { name: 'StepBackwardFilled', component: StepBackwardFilled },
    { name: 'FastForwardFilled', component: FastForwardFilled },
    { name: 'FastBackwardFilled', component: FastBackwardFilled },

    // 操作图标
    { name: 'FilterOutlined', component: FilterOutlined },
    { name: 'MoreOutlined', component: MoreOutlined },
    { name: 'EllipsisOutlined', component: EllipsisOutlined },
    { name: 'MenuUnfoldOutlined', component: MenuUnfoldOutlined },
    { name: 'MenuFoldOutlined', component: MenuFoldOutlined },
    { name: 'SwapOutlined', component: SwapOutlined },
    { name: 'SwapLeftOutlined', component: SwapLeftOutlined },
    { name: 'SwapRightOutlined', component: SwapRightOutlined },
    { name: 'RetweetOutlined', component: RetweetOutlined },
    { name: 'RollbackOutlined', component: RollbackOutlined },
    { name: 'ForkOutlined', component: ForkOutlined },
    { name: 'ShrinkOutlined', component: ShrinkOutlined },
    { name: 'ArrowsAltOutlined', component: ArrowsAltOutlined },
    { name: 'ExpandAltOutlined', component: ExpandAltOutlined },

    // 安全图标
    { name: 'LockOutlined', component: LockOutlined },
    { name: 'UnlockOutlined', component: UnlockOutlined },
    { name: 'KeyOutlined', component: KeyOutlined },
    { name: 'SafetyCertificateOutlined', component: SafetyCertificateOutlined },
    { name: 'SecurityScanOutlined', component: SecurityScanOutlined },
    { name: 'VerifiedOutlined', component: VerifiedOutlined },

    // 社交图标
    { name: 'WechatOutlined', component: WechatOutlined },
    { name: 'AlipayOutlined', component: AlipayOutlined },
    { name: 'TaobaoOutlined', component: TaobaoOutlined },
    { name: 'DingdingOutlined', component: DingdingOutlined },
    { name: 'YoutubeOutlined', component: YoutubeOutlined },
    { name: 'ZhihuOutlined', component: ZhihuOutlined },
    { name: 'WeiboOutlined', component: WeiboOutlined },
    { name: 'GithubOutlined', component: GithubOutlined },
    { name: 'GitlabOutlined', component: GitlabOutlined },

    // 金融图标
    { name: 'DollarOutlined', component: DollarOutlined },
    { name: 'EuroOutlined', component: EuroOutlined },
    { name: 'PoundOutlined', component: PoundOutlined },
    { name: 'CreditCardOutlined', component: CreditCardOutlined },
    { name: 'BankOutlined', component: BankOutlined },
    { name: 'WalletOutlined', component: WalletOutlined },
    { name: 'PayCircleOutlined', component: PayCircleOutlined },

    // 设备图标
    { name: 'PrinterOutlined', component: PrinterOutlined },
    { name: 'ScanOutlined', component: ScanOutlined },
    { name: 'QrcodeOutlined', component: QrcodeOutlined },
    { name: 'BarcodeOutlined', component: BarcodeOutlined },

    // 图表图标
    { name: 'RadarChartOutlined', component: RadarChartOutlined },
    { name: 'DashboardFilled', component: DashboardFilled },
    { name: 'AreaChartOutlined', component: AreaChartOutlined },
    { name: 'PieChartFilled', component: PieChartFilled },
    { name: 'BarChartOutlined', component: BarChartOutlined },
    { name: 'LineChartOutlined', component: LineChartOutlined },

    // 天气图标
    { name: 'CloudOutlined', component: CloudOutlined },
    { name: 'ThunderboltOutlined', component: ThunderboltOutlined },
    // 地图图标
    { name: 'EnvironmentOutlined', component: EnvironmentOutlined },
    { name: 'CompassOutlined', component: CompassOutlined },
    { name: 'GlobalOutlined', component: GlobalOutlined },

    // 其他常用图标
    { name: 'AlertOutlined', component: AlertOutlined },
    { name: 'ApiOutlined', component: ApiOutlined },
    { name: 'AppstoreAddOutlined', component: AppstoreAddOutlined },
    { name: 'AppstoreFilled', component: AppstoreFilled },
    { name: 'AuditOutlined', component: AuditOutlined },
    { name: 'BookOutlined', component: BookOutlined },
    { name: 'BugOutlined', component: BugOutlined },
    { name: 'BulbOutlined', component: BulbOutlined },
    { name: 'CalculatorOutlined', component: CalculatorOutlined },
    { name: 'CarOutlined', component: CarOutlined },
    { name: 'CarryOutOutlined', component: CarryOutOutlined },
    { name: 'CloudDownloadOutlined', component: CloudDownloadOutlined },
    { name: 'CloudServerOutlined', component: CloudServerOutlined },
    { name: 'CloudSyncOutlined', component: CloudSyncOutlined },
    { name: 'CloudUploadOutlined', component: CloudUploadOutlined },
    { name: 'ClusterOutlined', component: ClusterOutlined },
    { name: 'CoffeeOutlined', component: CoffeeOutlined },
    { name: 'ContactsOutlined', component: ContactsOutlined },
    { name: 'ContainerOutlined', component: ContainerOutlined },
    { name: 'ControlOutlined', component: ControlOutlined },
    { name: 'CopyrightOutlined', component: CopyrightOutlined },
    { name: 'CustomerServiceOutlined', component: CustomerServiceOutlined },
    { name: 'DeploymentUnitOutlined', component: DeploymentUnitOutlined },
    { name: 'DisconnectOutlined', component: DisconnectOutlined },
    { name: 'ExportOutlined', component: ExportOutlined },
    { name: 'FileAddOutlined', component: FileAddOutlined },
    { name: 'FileDoneOutlined', component: FileDoneOutlined },
    { name: 'FileExcelOutlined', component: FileExcelOutlined },
    { name: 'FileExclamationOutlined', component: FileExclamationOutlined },
    { name: 'FileImageOutlined', component: FileImageOutlined },
    { name: 'FilePdfOutlined', component: FilePdfOutlined },
    { name: 'FileProtectOutlined', component: FileProtectOutlined },
    { name: 'FileSearchOutlined', component: FileSearchOutlined },
    { name: 'FileSyncOutlined', component: FileSyncOutlined },
    { name: 'FileUnknownOutlined', component: FileUnknownOutlined },
    { name: 'FileWordOutlined', component: FileWordOutlined },
    { name: 'FileZipOutlined', component: FileZipOutlined },
    { name: 'FireOutlined', component: FireOutlined },
    { name: 'FlagOutlined', component: FlagOutlined },
    { name: 'FolderAddOutlined', component: FolderAddOutlined },
    { name: 'FolderFilled', component: FolderFilled },
    { name: 'FolderViewOutlined', component: FolderViewOutlined },
    { name: 'FundOutlined', component: FundOutlined },
    { name: 'FundProjectionScreenOutlined', component: FundProjectionScreenOutlined },
    { name: 'FundViewOutlined', component: FundViewOutlined },
    { name: 'FunnelPlotOutlined', component: FunnelPlotOutlined },
    { name: 'FunnelPlotFilled', component: FunnelPlotFilled },
    { name: 'GatewayOutlined', component: GatewayOutlined },
    { name: 'GoldOutlined', component: GoldOutlined },
    { name: 'GroupOutlined', component: GroupOutlined },
    { name: 'HistoryOutlined', component: HistoryOutlined },
    { name: 'HolderOutlined', component: HolderOutlined },
    { name: 'HourglassOutlined', component: HourglassOutlined },
    { name: 'IdcardOutlined', component: IdcardOutlined },
    { name: 'ImportOutlined', component: ImportOutlined },
    { name: 'InboxOutlined', component: InboxOutlined },
    { name: 'InsertRowAboveOutlined', component: InsertRowAboveOutlined },
    { name: 'InsertRowBelowOutlined', component: InsertRowBelowOutlined },
    { name: 'InsertRowLeftOutlined', component: InsertRowLeftOutlined },
    { name: 'InsertRowRightOutlined', component: InsertRowRightOutlined },
    { name: 'InsuranceOutlined', component: InsuranceOutlined },
    { name: 'InteractionOutlined', component: InteractionOutlined },
    { name: 'LayoutOutlined', component: LayoutOutlined },
    { name: 'MedicineBoxOutlined', component: MedicineBoxOutlined },
    { name: 'MehOutlined', component: MehOutlined },
    { name: 'MergeCellsOutlined', component: MergeCellsOutlined },
    { name: 'MoneyCollectOutlined', component: MoneyCollectOutlined },
    { name: 'PaperClipOutlined', component: PaperClipOutlined },
    { name: 'PartitionOutlined', component: PartitionOutlined },
    { name: 'PercentageOutlined', component: PercentageOutlined },
    { name: 'ProfileOutlined', component: ProfileOutlined },
    { name: 'ProjectOutlined', component: ProjectOutlined },
    { name: 'PropertySafetyOutlined', component: PropertySafetyOutlined },
    { name: 'PullRequestOutlined', component: PullRequestOutlined },
    { name: 'RadiusSettingOutlined', component: RadiusSettingOutlined },
    { name: 'ReadOutlined', component: ReadOutlined },
    { name: 'ReconciliationOutlined', component: ReconciliationOutlined },
    { name: 'RedEnvelopeOutlined', component: RedEnvelopeOutlined },
    { name: 'RestOutlined', component: RestOutlined },
    { name: 'RobotOutlined', component: RobotOutlined },
    { name: 'RocketOutlined', component: RocketOutlined },
    { name: 'SaveOutlined', component: SaveOutlined },
    { name: 'ScheduleOutlined', component: ScheduleOutlined },
    { name: 'SelectOutlined', component: SelectOutlined },
    { name: 'SendOutlined', component: SendOutlined },
    { name: 'SkinOutlined', component: SkinOutlined },
    { name: 'SolutionOutlined', component: SolutionOutlined },
    { name: 'SplitCellsOutlined', component: SplitCellsOutlined },
    { name: 'SubnodeOutlined', component: SubnodeOutlined },
    { name: 'SwitcherOutlined', component: SwitcherOutlined },
    { name: 'TagOutlined', component: TagOutlined },
    { name: 'ThunderboltFilled', component: ThunderboltFilled },
    { name: 'ToolOutlined', component: ToolOutlined },
    { name: 'TrademarkCircleOutlined', component: TrademarkCircleOutlined },
    { name: 'TransactionOutlined', component: TransactionOutlined },
    { name: 'TrophyOutlined', component: TrophyOutlined },
    { name: 'UserAddOutlined', component: UserAddOutlined },
    { name: 'UserDeleteOutlined', component: UserDeleteOutlined },
    { name: 'UserSwitchOutlined', component: UserSwitchOutlined },
    { name: 'WifiOutlined', component: WifiOutlined },
    { name: 'WindowsOutlined', component: WindowsOutlined },
    { name: 'WomanOutlined', component: WomanOutlined },
  ];

  // Computed
  const filteredIcons = computed(() => {
    if (!searchKeyword.value) {
      return availableIcons;
    }
    return availableIcons.filter((icon) =>
      icon.name.toLowerCase().includes(searchKeyword.value.toLowerCase())
    );
  });

  const currentIcon = computed(() => {
    if (!inputValue.value) return null;
    const icon = availableIcons.find((item) => item.name === inputValue.value);
    return icon ? icon.component : null;
  });

  // Watch for prop changes
  watch(
    () => props.icon,
    (newVal) => {
      inputValue.value = newVal;
      selectedIcon.value = newVal;
    },
    { immediate: true }
  ); // 立即执行一次

  // Methods
  const handleInputChange = (value: string) => {
    inputValue.value = value;
    // 实时更新父组件的值
    emit('update:icon', value);
  };

  const selectIcon = (icon: { name: string; component: any }) => {
    tempSelectedIcon.value = icon;
    selectedIcon.value = icon.name;
  };

  const confirmIcon = () => {
    if (tempSelectedIcon.value) {
      inputValue.value = tempSelectedIcon.value.name;
      emit('update:icon', tempSelectedIcon.value.name);
    }
    showModal.value = false;
  };
</script>

<style scoped>
  .icon-selector {
    width: 100%;
  }

  .icon-trigger-btn {
    padding: 4px;
    margin-right: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 24px;
    min-height: 24px;
  }

  .icon-trigger-btn:hover {
    background-color: rgba(24, 144, 255, 0.1);
  }

  .icon-trigger-btn svg {
    width: 16px;
    height: 16px;
    color: #666;
  }

  .icon-selector-content {
    max-height: 600px;
    overflow-y: auto;
  }

  .search-section {
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .search-input {
    flex: 1;
  }

  .icon-count {
    color: #666;
    font-size: 14px;
    white-space: nowrap;
  }

  .icon-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 8px;
    max-height: 450px;
    overflow-y: auto;
    padding: 12px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    background-color: #fafafa;
  }

  .icon-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 12px 8px;
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s ease;
    min-height: 80px;
    background-color: white;
  }

  .icon-item:hover {
    border-color: #1890ff;
    background-color: #f6ffed;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  .icon-item.active {
    border-color: #1890ff;
    background-color: #e6f7ff;
    box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
  }

  .icon-name {
    font-size: 11px;
    color: #666;
    margin-top: 4px;
    text-align: center;
    word-break: break-all;
    line-height: 1.2;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>
