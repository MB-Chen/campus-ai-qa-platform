<template>
  <n-drawer v-model:show="show" :width="1200" placement="right" :mask-closable="false">
    <n-drawer-content title="上传文档" closable>
      <n-steps :current="current" size="small" style="margin-bottom: 20px">
        <n-step title="选择文件" />
        <n-step title="解析与切片配置" />
        <n-step title="预览切片" />
        <n-step title="入库进度" />
      </n-steps>

      <!-- Step 1: 选择文件（多文件） -->
      <div v-show="current === 1">
        <n-upload
          v-model:file-list="fileList"
          :default-upload="false"
          :max="50"
          multiple
          accept=".txt,.md,.pdf,.docx,.html,.xls,.xlsx,.csv"
          @change="onFileChange"
        >
          <n-upload-dragger>
            <div style="margin-bottom: 8px">
              <n-icon size="44" color="#07c05f"><CloudUploadOutlined /></n-icon>
            </div>
            <div style="font-size: 14px">点击或拖拽文件到此处，支持多文件批量上传</div>
            <n-text depth="3" style="font-size: 12px">
              支持 txt / md / pdf / docx / html / xls / xlsx / csv，单文件 ≤ 50MB，最多 50 个
            </n-text>
          </n-upload-dragger>
        </n-upload>

        <div v-if="fileList.length > 0" class="file-list">
          <div v-for="(item, index) in fileList" :key="item.id || index" class="file-item">
            <n-icon size="22" :color="extColor(getExt(item.name))">
              <component :is="extIcon(getExt(item.name))" />
            </n-icon>
            <div class="file-data">
              <div class="file-name">{{ item.name }}</div>
              <div class="file-size">{{ formatSize(item.file?.size) }}</div>
            </div>
            <n-button size="tiny" quaternary type="error" @click="delFile(index)">
              <template #icon
                ><n-icon><DeleteOutlined /></n-icon
              ></template>
            </n-button>
          </div>
        </div>
      </div>

      <!-- Step 2: 解析与切片配置 -->
      <div v-show="current === 2" class="step2-wrap">
        <!-- 解析引擎（按文件类型分组） -->
        <div class="section-block">
          <div class="section-title">解析引擎</div>
          <div class="section-desc">不同格式的文件可指定不同解析引擎</div>
          <div v-if="fileTypeGroups.length > 0" class="engine-groups">
            <div v-for="g in fileTypeGroups" :key="g.label" class="engine-row">
              <div class="engine-label">
                <span class="engine-ext">{{ g.label }}</span>
                <n-space :size="4">
                  <n-tag v-for="ext in g.extensions" :key="ext" size="tiny" round>{{
                    '.' + ext
                  }}</n-tag>
                </n-space>
              </div>
              <n-select
                :value="getEngineForGroup(g.extensions)"
                :options="getEngineOptions(g.extensions)"
                placeholder="选择引擎"
                style="width: 300px"
                @update:value="(val: string) => setEngineForGroup(g.extensions, val)"
              />
            </div>
          </div>
          <n-text v-else depth="3" style="font-size: 12px">暂无文件，请返回上一步选择文件</n-text>

          <!-- MinerU 未配置提示横幅：仅当选用了 mineru 系列且未配置时显示 -->
          <div v-if="unconfiguredMineruEngine()" class="mineru-warn">
            <n-icon size="16" color="#d03050"><WarningOutlined /></n-icon>
            <span class="mineru-warn-text">
              所选 MinerU 引擎尚未配置或未启用，解析将失败。请先完成配置后再试切。
            </span>
            <n-button size="small" type="primary" ghost @click="gotoServiceConfig">
              去配置
            </n-button>
          </div>

          <!-- MinerU 图片处理策略说明：选用 mineru 系列时展示 -->
          <div v-if="anyMineruSelected" class="mineru-image-hint">
            <n-icon size="16" color="#2080f0"><InfoCircleOutlined /></n-icon>
            <div class="mineru-image-hint-body">
              <div class="mineru-image-hint-title">MinerU 图片处理策略</div>
              <div class="mineru-image-hint-text">
                PDF 中的图片、表格截图、公式截图会交由<b>视觉模型（VLM）</b>理解，转写成 「内容描述
                + OCR 文字」二合一文本<b>原地替换</b>原图片，<b>不会保留图片本身</b>。
                过小的图标（&lt;64px）会被直接忽略。若视觉模型不可用或解析失败，对应图片将被<b
                  >丢弃</b
                >
                ——即宁可丢失该图信息，也不让无效图片链接（URL）污染知识库切片。
              </div>
            </div>
          </div>
        </div>

        <!-- 分块配置（含父子分块开关+大小滑块） -->
        <div class="section-block">
          <div class="section-title">分块配置</div>
          <div class="section-desc">父子分块开关在下方，开启后可设置父块和子块的大小</div>
          <ChunkingSettings
            :config="chunkingCfg"
            :has-spreadsheet="hasSpreadsheet"
            @update:config="chunkingCfg = $event"
          />
        </div>

        <!-- 预览切片开关：大文件/切片过多时建议关闭，避免 DOM 卡顿 -->
        <div class="section-block">
          <div class="setting-row setting-row--toggle">
            <div class="setting-info">
              <label>预览切片</label>
              <p class="desc">
                开启后点击「试切预览」可查看/编辑每个切片；关闭则点击「直接入库」静默解析后自动入库。
                大文件或切片过多时建议关闭，避免页面渲染卡顿
              </p>
            </div>
            <div class="setting-control">
              <n-switch
                :value="enablePreview"
                @update:value="(val: boolean) => onPreviewToggle(val)"
              />
            </div>
          </div>
          <!-- 自动关闭提示横幅：系统判定建议关闭，用户可手动强行打开 -->
          <div v-if="previewAutoOff" class="preview-autooff-warn">
            <n-icon size="16" color="#d03050"><WarningOutlined /></n-icon>
            <span class="preview-autooff-warn-text">
              已自动关闭预览：{{ previewAutoOffReason }}。强制开启可能导致页面卡顿。
            </span>
          </div>
        </div>
      </div>

      <!-- Step 3: 预览切片 -->
      <div v-show="current === 3">
        <!-- mineru 异步预览：圆盘 + 进度条 + 已完成文件列表 -->
        <ProcessingStatus
          v-if="previewing && previewProgress"
          :elapsed-ms="previewElapsedMs"
          label="解析中"
          :text="
            previewProgress.status === 'failed'
              ? '解析失败'
              : previewSilentMode
              ? '正在解析并切分文档，完成后将自动入库…'
              : '正在解析并切分文档，请稍候…'
          "
          :failed="previewProgress.status === 'failed'"
          show-progress
          :percent="previewPercent"
          :done="previewProgress.done || 0"
          :total="previewProgress.total || 0"
          :success="previewProgress.success || 0"
          :failed-count="previewProgress.failed || 0"
          :message="previewProgress.message"
          :completed-items="previewCompletedItems"
        >
          <template #stages>
            <StageTimingCard
              v-if="previewProgress.stages && previewProgress.stages.length > 0"
              :stages="previewProgress.stages"
            />
          </template>
        </ProcessingStatus>
        <!-- 非 mineru 同步预览：圆盘（无进度条） -->
        <ProcessingStatus
          v-else-if="previewing"
          :elapsed-ms="previewElapsedMs"
          label="解析中"
          :text="
            previewSilentMode
              ? '正在解析并切分文档，完成后将自动入库…'
              : '正在解析并切分文档，请稍候…'
          "
        />
        <template v-else>
          <!-- 多文档切换 tab -->
          <div v-if="previewDocs.length > 1" class="doc-tabs scrollbar-thin">
            <div
              v-for="(d, i) in previewDocs"
              :key="i"
              class="doc-tab"
              :class="{ active: activeDoc === i }"
              @click="activeDoc = i"
            >
              <n-icon size="14"><FileTextOutlined /></n-icon>
              <span class="doc-tab-name">{{ d.fileName }}</span>
              <n-tag size="tiny" round>{{ d.chunks.length }}</n-tag>
            </div>
          </div>

          <!-- 当前文档头部 -->
          <div v-if="currentDoc" class="preview-head">
            <div class="preview-head-info">
              <span class="preview-file">{{ currentDoc.fileName }}</span>
              <span class="preview-meta">{{ formatSize(currentDoc.fileSize) }}</span>
              <n-tag size="small" type="success" round
                >共 {{ currentDoc.chunks.length }} 个切片</n-tag
              >
            </div>
            <n-space>
              <n-button size="small" secondary @click="addChunk">
                <template #icon
                  ><n-icon><PlusOutlined /></n-icon
                ></template>
                新增切片
              </n-button>
              <n-button size="small" secondary @click="rePreview" :loading="previewing">
                <template #icon
                  ><n-icon><ReloadOutlined /></n-icon
                ></template>
                重新预览
              </n-button>
              <!-- 阶段耗时统计：弹出柱状图 + 明细表（预览阶段的 parse/chunk） -->
              <n-button
                v-if="currentDocStages.length > 0"
                size="small"
                secondary
                @click="openStageTimeline"
              >
                <template #icon
                  ><n-icon><BarChartOutlined /></n-icon
                ></template>
                耗时统计
              </n-button>
            </n-space>
          </div>

          <!-- 切片列表 -->
          <div class="chunk-list">
            <div
              v-for="(rc, idx) in renderedChunks"
              :key="idx"
              v-memo="[rc.contentHtml, rc.parentHtml, expandedFlags[idx]]"
              class="chunk-item"
            >
              <div class="chunk-header">
                <span class="chunk-index">切片 {{ idx + 1 }}</span>
                <div class="chunk-header-right">
                  <n-tag v-if="rc.original.parentIndex != null" size="small" round type="info">
                    父块 #{{ rc.original.parentIndex + 1 }}
                  </n-tag>
                  <span class="chunk-meta">{{ (rc.original.content || '').length }} 字符</span>
                  <n-button size="tiny" quaternary @click="editChunk(idx)">
                    <template #icon
                      ><n-icon><EditOutlined /></n-icon
                    ></template>
                    编辑
                  </n-button>
                  <n-button size="tiny" quaternary type="error" @click="delChunk(idx)">
                    <template #icon
                      ><n-icon><DeleteOutlined /></n-icon
                    ></template>
                  </n-button>
                </div>
              </div>

              <!-- 内容（渲染缓存） -->
              <div class="chunk-content md-body" v-html="rc.contentHtml"></div>

              <!-- 父块上下文（折叠） -->
              <div v-if="rc.original.parentContext" class="parent-context-section">
                <div class="parent-context-toggle" @click="toggleParent(idx)">
                  <n-icon size="12">
                    <component :is="expandedFlags[idx] ? DownOutlined : RightOutlined" />
                  </n-icon>
                  <span>查看父块上下文</span>
                  <span class="parent-context-hint"
                    >{{ (rc.original.parentContext || '').length }} 字符</span
                  >
                </div>
                <div
                  v-show="expandedFlags[idx]"
                  class="parent-context-body md-body"
                  v-html="rc.parentHtml"
                ></div>
              </div>
            </div>

            <n-empty
              v-if="currentChunks.length === 0"
              description="该文档无切片，可调整参数重新预览或手动新增"
            />
          </div>
        </template>
      </div>

      <!-- Step 4: 入库进度 -->
      <div v-show="current === 4" class="step4-wrap">
        <ProcessingStatus
          :elapsed-ms="saveInProgress ? saveElapsedMs : 0"
          :label="saveProgress?.status === 'failed' ? '失败' : '入库中'"
          :text="saveProgress?.status === 'failed' ? '入库失败' : '正在入库，请勿关闭…'"
          :failed="saveProgress?.status === 'failed'"
          show-progress
          :percent="savePercent"
          :done="saveProgress?.done || 0"
          :total="saveProgress?.total || 0"
          :success="saveProgress?.success || 0"
          :failed-count="saveProgress?.failed || 0"
          :message="saveProgress?.message"
        >
          <template #stages>
            <StageTimingCard
              v-if="saveProgress && saveProgress.stages && saveProgress.stages.length > 0"
              :stages="saveProgress.stages"
            />
          </template>
        </ProcessingStatus>
      </div>

      <!-- 编辑切片弹窗 -->
      <n-modal
        v-model:show="editVisible"
        preset="card"
        title="编辑切片"
        style="width: 720px"
        :bordered="false"
      >
        <n-form label-placement="top">
          <n-form-item label="切片标题（可选）">
            <n-input
              v-model:value="editForm.title"
              placeholder="可为空"
              maxlength="255"
              show-count
            />
          </n-form-item>
          <n-form-item label="切片内容">
            <n-input
              v-model:value="editForm.content"
              type="textarea"
              :rows="10"
              maxlength="8000"
              show-count
            />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="editVisible = false">取消</n-button>
            <n-button type="primary" secondary @click="saveEdit">保存</n-button>
          </n-space>
        </template>
      </n-modal>

      <!-- 阶段耗时统计弹窗（预览阶段 parse/chunk） -->
      <IngestionTimelineModal ref="timelineModalRef" />

      <template #footer>
        <n-space justify="space-between" style="width: 100%">
          <n-text depth="3" style="font-size: 12px">
            <template v-if="current === 3 && previewSilentMode">解析中，即将自动入库…</template>
            <template v-else-if="current === 3">已选 {{ previewDocs.length }} 个文件</template>
            <template v-else-if="current === 4">入库中…</template>
            <template v-else>步骤 {{ current }} / 3</template>
          </n-text>
          <n-space>
            <n-button @click="show = false">取消</n-button>
            <n-button secondary v-if="current > 1" @click="current--">上一步</n-button>
            <n-button
              secondary
              v-if="current === 1"
              type="primary"
              :disabled="fileList.length === 0"
              @click="goStep2"
            >
              下一步
            </n-button>
            <!-- Step 2：预览开 → 试切预览；预览关 → 直接入库（静默解析后自动入库） -->
            <n-button
              v-if="current === 2 && enablePreview"
              type="primary"
              secondary
              :loading="previewing"
              @click="doPreview"
            >
              试切预览
            </n-button>
            <n-button
              v-if="current === 2 && !enablePreview"
              type="primary"
              secondary
              :loading="previewing"
              @click="doDirectSave"
            >
              直接入库
            </n-button>
            <!-- Step 3：静默解析期间不显示「确认入库」（会自动转 Step 4）；解析完成后的正常预览态才显示 -->
            <n-button
              v-if="current === 3 && !previewing"
              type="primary"
              secondary
              :loading="saving"
              @click="doSave"
            >
              确认入库
            </n-button>
          </n-space>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, computed, watch, onUnmounted } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { UploadFileInfo } from 'naive-ui';
  import { marked } from 'marked';
  import {
    CloudUploadOutlined,
    DeleteOutlined,
    FileTextOutlined,
    PlusOutlined,
    ReloadOutlined,
    EditOutlined,
    FilePdfOutlined,
    FileWordOutlined,
    FileExcelOutlined,
    FileTextFilled,
    Html5Filled,
    DownOutlined,
    RightOutlined,
    WarningOutlined,
    InfoCircleOutlined,
    BarChartOutlined,
  } from '@vicons/antd';

  marked.use({ breaks: true, gfm: true });

  /** 简单 markdown → HTML（预览切片用，不需要流式/消毒） */
  function renderMd(text: string): string {
    if (!text) return '';
    return marked.parse(text) as string;
  }
  import {
    previewDocument,
    saveDocument,
    getSaveProgress,
    getPreviewProgress,
    type PreviewChunkItem,
    type PreviewDocItem,
    type SaveDocItem,
    type SaveProgress,
    type PreviewProgress,
    type ParserEngineRule,
  } from '@/api/system/knowledge';
  import { getServiceList, SERVICE_CATEGORY } from '@/api/system/aiService';
  import { useRouter } from 'vue-router';
  import ChunkingSettings from './ChunkingSettings.vue';
  import StageTimingCard from './StageTimingCard.vue';
  import ProcessingStatus from './ProcessingStatus.vue';
  import IngestionTimelineModal from './IngestionTimelineModal.vue';
  import type { ChunkingConfig } from './chunkingConfig';
  const props = defineProps<{ kbId: string }>();
  const emit = defineEmits<{ (e: 'uploaded'): void }>();
  const message = useMessage();
  const router = useRouter();
  const show = ref(false);
  const current = ref(1);
  const fileList = ref<UploadFileInfo[]>([]);
  const previewing = ref(false);
  const saving = ref(false);

  // 用户开关：默认开。关闭后 Step 2 点「直接入库」走静默解析→不入库，不渲染切片 DOM。
  const enablePreview = ref(true);
  // 当前是否被系统自动关闭（仅驱动红色警告横幅显示）。
  // 与 enablePreview 解耦：enablePreview 表「当前是否开」，autoOff 表「当前关是不是系统干的」。
  const previewAutoOff = ref(false);
  // 进入 Step 2 后用户是否手动改过开关。一旦手动改过，后续就不再被自动判定覆盖。
  const previewUserTouched = ref(false);
  // 静默模式：调 preview 拿切片但不渲染 DOM，完成后立即转 save。
  // 仅 doDirectSave 时置 true；doPreview（试切预览）始终为 false。
  const previewSilentMode = ref(false);
  // 自动关闭阈值（命中任一即自动关预览）
  const AUTO_CLOSE = {
    EST_CHUNKS: 500, // 估算切片数（每个含 markdown 渲染，>500 个 DOM 节点明显卡）
    SINGLE_FILE_MB: 20, // 任一单文件体积
    FILE_COUNT: 10, // 文件总数
    TOTAL_MB: 50, // 整批总体积
  };

  // 预览转圈期间一直跑的计时器（实时展示已耗时）。
  // previewing=true 时启动（每 100ms 刷新），false 时停止。
  const previewElapsedMs = ref(0);
  let previewElapsedTimer: ReturnType<typeof setInterval> | null = null;
  let previewStartedAt = 0;
  function startPreviewElapsed() {
    stopPreviewElapsed();
    previewStartedAt = Date.now();
    previewElapsedMs.value = 0;
    previewElapsedTimer = setInterval(() => {
      previewElapsedMs.value = Date.now() - previewStartedAt;
    }, 100);
  }
  function stopPreviewElapsed() {
    if (previewElapsedTimer) {
      clearInterval(previewElapsedTimer);
      previewElapsedTimer = null;
    }
  }
  watch(previewing, (val) => {
    if (val) startPreviewElapsed();
    else stopPreviewElapsed();
  });

  // 入库进度（Step 4）：后端 saveDocument 立即返回 taskId，前端轮询 getSaveProgress
  const saveProgress = ref<SaveProgress | null>(null);
  let savePollTimer: ReturnType<typeof setTimeout> | null = null;

  // 预览进度（Step 3，仅 mineru 场景）：后端 preview 返回 taskId，前端轮询 getPreviewProgress
  // 非 mineru 引擎解析很快，后端直接返回切片数组，不走轮询
  const previewProgress = ref<PreviewProgress | null>(null);
  let previewPollTimer: ReturnType<typeof setTimeout> | null = null;

  // 入库转圈期间一直跑的计时器（与预览计时器同款，绑定 saving 状态）。
  // saving=true 时启动，false 时停止。Step4 跳进去后就开始计时。
  // 注意：必须放在 saveProgress 声明之后（watch 引用了它），否则 TDZ 报错。
  const saveElapsedMs = ref(0);
  let saveElapsedTimer: ReturnType<typeof setInterval> | null = null;
  let saveStartedAt = 0;
  function startSaveElapsed() {
    stopSaveElapsed();
    saveStartedAt = Date.now();
    saveElapsedMs.value = 0;
    saveElapsedTimer = setInterval(() => {
      saveElapsedMs.value = Date.now() - saveStartedAt;
    }, 100);
  }
  function stopSaveElapsed() {
    if (saveElapsedTimer) {
      clearInterval(saveElapsedTimer);
      saveElapsedTimer = null;
    }
  }
  // 计时器是否正在跑：saving 为 true 且进度未到终态（done/failed）
  const saveInProgress = computed(() => {
    if (!saving.value) return false;
    const s = saveProgress.value?.status;
    return s !== 'done' && s !== 'failed';
  });
  watch(saving, (val) => {
    if (val) startSaveElapsed();
    else stopSaveElapsed();
  });
  // 进度切到终态时也停（saving 可能因轮询路径在 status=done 后才置 false，这里提前停更自然）
  watch(
    () => saveProgress.value?.status,
    (s) => {
      if (s === 'done' || s === 'failed') stopSaveElapsed();
    }
  );

  // 分块配置
  const chunkingCfg = ref<ChunkingConfig>({
    chunkSize: 512,
    chunkOverlap: 80,
    separators: ['\n\n', '\n', '。', '！', '？', '；', ';'],
    enableParentChild: false,
    parentChunkSize: 1536,
    childChunkSize: 384,
    strategy: 'auto',
    tokenLimit: 0,
    languages: [],
    qaMode: false,
  });

  // 解析引擎规则（按文件类型分组）
  const engineRules = ref<ParserEngineRule[]>([]);

  // ===== 父块大小动态推荐 =====
  // 父块现在会真正注入 LLM（后端 ParentExpansionPostProcessor 展开），过大会灌噪声、
  // 过小会切碎上下文。按文件总体积估算纯文本量，给一个聚焦的推荐默认值。
  // 用户手动拖过滑块后置 touched，之后不再覆盖，避免抢用户操作（与 previewUserTouched 同款思路）。
  const parentChunkUserTouched = ref(false);

  /** 按 totalFileSize 估算纯文本字符数（÷10：覆盖富文本被解析为纯文本的体积放大，保守估计） */
  const estimatedTextChars = computed(() => {
    // totalFileSize 在下方定义（computed）；此处 ref 安全——computed 是惰性的，调用时已就绪
    return Math.floor(totalFileSize.value / 10);
  });

  /**
   * 按估算文本量推荐父块大小（ChunkingSettings 滑块 min=512/max=8192/step=64，推荐值均合法）：
   *  - 小文档（<2000 字符）：1024，接近全文级，避免切碎丢失上下文
   *  - 中等（2000~10000）：1536（默认值），4 个子块聚焦一段
   *  - 较大（>10000）：2048，更多上下文容纳长文段落
   */
  const recommendedParentChunkSize = computed(() => {
    const chars = estimatedTextChars.value;
    if (chars > 0 && chars < 2000) return 1024;
    if (chars <= 10000) return 1536;
    return 2048;
  });

  /** 应用推荐父块大小（仅用户未手动改过时生效） */
  // applyingRecommended 防止下方 watch 把「推荐写入」误判成「用户手动改」而提前锁定 touched
  let applyingRecommended = false;
  function applyRecommendedParentChunkSize() {
    if (parentChunkUserTouched.value) return;
    applyingRecommended = true;
    chunkingCfg.value.parentChunkSize = recommendedParentChunkSize.value;
    applyingRecommended = false;
  }

  // 监听父块大小变化：用户手动拖动滑块（非推荐写入）时置 touched，之后推荐不再覆盖
  watch(
    () => chunkingCfg.value.parentChunkSize,
    () => {
      if (applyingRecommended) return; // 推荐自身的写入，不算用户操作
      parentChunkUserTouched.value = true;
    }
  );

  // MinerU 是否已在「外部服务配置」正确配置并启用
  // null=未加载 / true=已配置 / false=未配置（引导用户去配置）
  // engine 值（mineru / mineru_cloud）→ category 映射与后端 ExtServiceConfigServiceImpl 一致
  const mineruConfigured = ref<Record<string, boolean | null>>({
    [SERVICE_CATEGORY.MINERU_SELF]: null,
    [SERVICE_CATEGORY.MINERU_CLOUD]: null,
  });

  // 引擎值 → category（与后端 getMineruConfig 的映射保持一致）
  function engineToCategory(engine: string): string | null {
    if (engine === 'mineru') return SERVICE_CATEGORY.MINERU_SELF;
    if (engine === 'mineru_cloud') return SERVICE_CATEGORY.MINERU_CLOUD;
    return null;
  }

  // 拉某 category 下启用配置是否存在（status=1）
  async function loadMineruStatus(category: string) {
    try {
      const res: any = await getServiceList({ category, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        mineruConfigured.value[category] = res.data.some((r: any) => r && r.id != null);
      } else {
        mineruConfigured.value[category] = false;
      }
    } catch {
      // 拉取失败按「未知」处理，不阻断主流程，预览时后端会再校验
      mineruConfigured.value[category] = false;
    }
  }

  // 判断当前选用的引擎里是否有 mineru 系列且未配置
  function unconfiguredMineruEngine(): string | null {
    for (const g of fileTypeGroups.value) {
      const eng = getEngineForGroup(g.extensions);
      const cat = engineToCategory(eng);
      if (cat && mineruConfigured.value[cat] === false) return eng;
    }
    return null;
  }

  // 当前是否选用了任意 MinerU 引擎（自建/云端），用于展示图片处理策略说明
  const anyMineruSelected = computed(() =>
    fileTypeGroups.value.some((g) => {
      const eng = getEngineForGroup(g.extensions);
      return eng === 'mineru' || eng === 'mineru_cloud';
    })
  );

  function gotoServiceConfig() {
    show.value = false;
    router.push('/knowledge/aiservice');
  }

  // 预览结果
  const previewDocs = ref<PreviewDocItem[]>([]);
  const activeDoc = ref(0);
  const currentDoc = computed(() => previewDocs.value[activeDoc.value]);
  const currentChunks = computed(() => currentDoc.value?.chunks || []);

  // 当前文档的阶段耗时（预览阶段已计 parse/chunk），用于「耗时统计」按钮弹窗
  const currentDocStages = computed(() => currentDoc.value?.stages || []);
  const timelineModalRef = ref<InstanceType<typeof IngestionTimelineModal> | null>(null);
  function openStageTimeline() {
    if (currentDocStages.value.length === 0) {
      message.info('当前文档暂无耗时数据');
      return;
    }
    // 引擎优先用后端透传的（最准确），缺失时按扩展名本地推导兜底
    const doc = currentDoc.value;
    const engine = doc?.engine || (doc ? getEngineForGroup([getExt(doc.fileName)]) : 'tika');
    // 预览阶段只有 parse/chunk，构造一个简易 summary 直接弹（不必调后端）
    const totalMs = currentDocStages.value.reduce(
      (sum, s) => sum + (s.durationMs > 0 ? s.durationMs : 0),
      0
    );
    timelineModalRef.value?.openWithSummary({
      engine,
      totalMs,
      stages: currentDocStages.value,
    });
  }

  // 父块上下文展开状态：用普通布尔数组代替 Set。
  // v-memo 以 expandedFlags[idx] 为依赖，toggle 时只有被点击的那一项
  // 重新 patch，其余切片连同 v-html 完全跳过，彻底解决性能卡顿和
  // 「Maximum recursive updates exceeded」。
  const expandedFlags = ref<boolean[]>([]);
  function toggleParent(idx: number) {
    const arr = expandedFlags.value.slice();
    arr[idx] = !arr[idx];
    expandedFlags.value = arr;
  }

  // 渲染后的切片（contentHtml / parentHtml 在 doPreview / saveEdit 时
  // 同步预算好挂到对象上，模板只读字符串，不在 computed 里调 marked.parse，
  // 彻底避开 Vue「递归更新」告警）。
  interface RenderedChunk {
    original: PreviewChunkItem;
    contentHtml: string;
    parentHtml: string;
  }
  const renderedChunks = ref<RenderedChunk[]>([]);
  function rebuildRenderedChunks() {
    const chunks = currentChunks.value;
    renderedChunks.value = chunks.map((c) => ({
      original: c,
      contentHtml: renderMd(c.content),
      parentHtml: c.parentContext ? renderMd(c.parentContext) : '',
    }));
    // 同步展开标记数组长度，新数据默认全部收起
    expandedFlags.value = new Array(chunks.length).fill(false);
  }

  // 切换文档 tab 时重建渲染缓存，并重置展开状态
  watch(activeDoc, () => {
    rebuildRenderedChunks();
  });

  // 编辑弹窗
  const editVisible = ref(false);
  const editIndex = ref(-1);
  const editForm = ref<PreviewChunkItem>({ title: '', content: '' });
  interface FileTypeGroup {
    label: string;
    extensions: string[];
    defaultEngine: string;
  }
  const fileTypeGroupsBase: FileTypeGroup[] = [
    { label: 'PDF 文档', extensions: ['pdf'], defaultEngine: 'tika' },
    { label: 'Word 文档', extensions: ['docx', 'doc'], defaultEngine: 'poi' },
    { label: 'Excel 表格', extensions: ['xls', 'xlsx', 'csv'], defaultEngine: 'poi' },
    { label: 'Markdown', extensions: ['md'], defaultEngine: 'tika' },
    { label: '纯文本', extensions: ['txt'], defaultEngine: 'tika' },
    { label: 'HTML', extensions: ['html', 'htm'], defaultEngine: 'tika' },
  ];

  const allEngines = [
    { label: 'Tika（通用解析）', value: 'tika' },
    { label: 'PdfBox（PDF 专用）', value: 'pdfbox' },
    { label: 'POI（Office 专用）', value: 'poi' },
    { label: 'MinerU 自建（复杂版式）', value: 'mineru' },
    { label: 'MinerU 云端（mineru.net）', value: 'mineru_cloud' },
  ];

  const fileTypeGroups = computed(() => {
    const exts = new Set(fileList.value.map((f) => getExt(f.name)).filter(Boolean));
    if (exts.size === 0) return [];
    return fileTypeGroupsBase.filter((g) => g.extensions.some((e) => exts.has(e)));
  });

  // 是否存在表格类文件，控制「一行一块」开关是否展示
  const hasSpreadsheet = computed(() =>
    fileList.value.some((f) => ['xls', 'xlsx', 'csv'].includes(getExt(f.name)))
  );

  // 估算思路：总字节 ÷ 10（二进制/纯文本混合经验折算系数）÷ 生效分块大小。
  // 父子分块时用子块大小（子块才是真正渲染到 DOM 的切片粒度）。
  const totalFileSize = computed(() =>
    fileList.value.reduce((sum, f) => sum + (f.file?.size || 0), 0)
  );
  const maxSingleFileSize = computed(() =>
    fileList.value.reduce((m, f) => Math.max(m, f.file?.size || 0), 0)
  );
  const estimatedChunks = computed(() => {
    const size = chunkingCfg.value.enableParentChild
      ? chunkingCfg.value.childChunkSize
      : chunkingCfg.value.chunkSize;
    if (!size || size <= 0 || totalFileSize.value <= 0) return 0;
    // 10x 折算：覆盖 PDF/DOCX 等富文本被解析为纯文本后的体积放大，保守高估切片数
    return Math.ceil(totalFileSize.value / 10 / size);
  });

  /**
   * 自动关闭原因：依次判断阈值，命中任一即返回中文原因；未命中返回空串。
   * 顺序固定（先切片数、再单文件、再总数、再总大小），保证提示文案稳定可读。
   */
  const previewAutoOffReason = computed(() => {
    if (estimatedChunks.value > AUTO_CLOSE.EST_CHUNKS) {
      return `估算切片约 ${estimatedChunks.value} 个（>${AUTO_CLOSE.EST_CHUNKS}）`;
    }
    if (maxSingleFileSize.value > AUTO_CLOSE.SINGLE_FILE_MB * 1024 * 1024) {
      return `存在大文件（>${AUTO_CLOSE.SINGLE_FILE_MB}MB）`;
    }
    if (fileList.value.length > AUTO_CLOSE.FILE_COUNT) {
      return `文件数 ${fileList.value.length}（>${AUTO_CLOSE.FILE_COUNT}）`;
    }
    if (totalFileSize.value > AUTO_CLOSE.TOTAL_MB * 1024 * 1024) {
      return `总大小 ${(totalFileSize.value / 1024 / 1024).toFixed(1)}MB（>${
        AUTO_CLOSE.TOTAL_MB
      }MB）`;
    }
    return '';
  });

  /**
   * 重新评估是否自动关闭预览。仅在 Step 2 且用户没手动改过开关时执行。
   * 命中阈值就关（enablePreview=false + previewAutoOff=true），
   * 阈值不再命中就开（enablePreview=true + previewAutoOff=false）。
   * 由 watch([fileList, chunkingCfg, ...]) 在 Step 2 增删文件/调分块大小时触发。
   */
  function reevaluateAutoPreview() {
    if (current.value !== 2) return;
    if (previewUserTouched.value) return;
    const reason = previewAutoOffReason.value;
    if (reason) {
      enablePreview.value = false;
      previewAutoOff.value = true;
    } else {
      enablePreview.value = true;
      previewAutoOff.value = false;
    }
  }

  /**
   * 用户手动切换开关的回调：标记 touched，之后不再被自动判定覆盖；
   * 若用户手动打开，清掉 autoOff 标记（红色横幅消失）。
   */
  function onPreviewToggle(val: boolean) {
    previewUserTouched.value = true;
    enablePreview.value = val;
    if (val) previewAutoOff.value = false;
  }

  // Step 2 自动判定预览开关：增删文件 / 调分块大小（父子模式含子块）/ 切父子模式时重新评估。
  // 只读依赖（不写回被监听值），不会形成循环；用户手动改过后 previewUserTouched 兜底停止覆盖。
  // 注意：必须放在 totalFileSize/maxSingleFileSize/reevaluateAutoPreview 声明之后，
  // 否则 watch 的即时依赖收集会触发 TDZ（访问未初始化的 const）。
  watch(
    [
      () => fileList.value.length,
      () => totalFileSize.value,
      () => maxSingleFileSize.value,
      () => chunkingCfg.value.chunkSize,
      () => chunkingCfg.value.childChunkSize,
      () => chunkingCfg.value.enableParentChild,
    ],
    () => {
      reevaluateAutoPreview();
      // 文件增删/规模变化时刷新父块大小推荐（用户已手动改过则 applyRecommended 内部跳过）
      applyRecommendedParentChunkSize();
    }
  );

  function getEngineForGroup(extensions: string[]): string {
    for (const rule of engineRules.value) {
      if (rule.fileTypes?.some((ft) => extensions.includes(ft))) return rule.engine;
    }
    return (
      fileTypeGroupsBase.find((g) => g.extensions.some((e) => extensions.includes(e)))
        ?.defaultEngine || 'tika'
    );
  }

  /**
   * 用户没改下拉框时，按当前文件扩展名的默认引擎构造规则列表（发给后端 preview）。
   * 保证后端 extEngineMap 始终有正确映射，避免 docx→poi 被误记成 tika。
   * 同扩展名去重，避免重复规则。
   */
  function buildDefaultEngineRules(files: File[]): ParserEngineRule[] {
    const extEngine = new Map<string, string>();
    for (const f of files) {
      const ext = getExt(f.name);
      if (!ext) continue;
      if (extEngine.has(ext)) continue;
      extEngine.set(ext, getEngineForGroup([ext]));
    }
    // 按 engine 分组（同引擎的扩展名合并成一条规则），减少规则数量
    const byEngine = new Map<string, string[]>();
    extEngine.forEach((eng, ext) => {
      const arr = byEngine.get(eng) || [];
      arr.push(ext);
      byEngine.set(eng, arr);
    });
    const rules: ParserEngineRule[] = [];
    byEngine.forEach((exts, eng) => rules.push({ fileTypes: exts, engine: eng }));
    return rules;
  }

  function getEngineOptions(extensions: string[]) {
    if (extensions.includes('pdf')) return allEngines;
    if (extensions.some((e) => ['docx', 'doc', 'xls', 'xlsx', 'csv'].includes(e))) {
      // MinerU 系列（自建/云端）仅用于复杂版式 PDF，Office 类文件不暴露
      return allEngines.filter((eng) => eng.value !== 'pdfbox' && !eng.value.startsWith('mineru'));
    }
    return allEngines.filter((eng) => eng.value === 'tika');
  }

  function setEngineForGroup(extensions: string[], engine: string) {
    engineRules.value = engineRules.value.filter(
      (r) => !r.fileTypes?.some((ft) => extensions.includes(ft))
    );
    engineRules.value.push({ fileTypes: [...extensions], engine });
    // 选中 MinerU 系列时：若未配置立即提示（预览时还有兜底拦截）
    const cat = engineToCategory(engine);
    if (cat && mineruConfigured.value[cat] === false) {
      message.warning(
        `${
          engine === 'mineru' ? '自建 MinerU' : '云端 MinerU'
        } 尚未配置，试切前请先到「外部服务配置」完成配置`,
        { duration: 4000 }
      );
    }
  }

  function formatSize(size?: number) {
    if (!size) return '-';
    if (size < 1024) return size + ' B';
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
    return (size / 1024 / 1024).toFixed(2) + ' MB';
  }

  function getExt(name?: string) {
    if (!name) return '';
    const idx = name.lastIndexOf('.');
    return idx < 0 ? '' : name.slice(idx + 1).toLowerCase();
  }

  function extIcon(ext: string) {
    if (['pdf'].includes(ext)) return FilePdfOutlined;
    if (['doc', 'docx'].includes(ext)) return FileWordOutlined;
    if (['xls', 'xlsx', 'csv'].includes(ext)) return FileExcelOutlined;
    if (['html', 'htm'].includes(ext)) return Html5Filled;
    return FileTextFilled;
  }

  function extColor(ext: string) {
    if (['pdf'].includes(ext)) return '#d03050';
    if (['doc', 'docx'].includes(ext)) return '#2080f0';
    if (['xls', 'xlsx', 'csv'].includes(ext)) return '#18a058';
    if (['html', 'htm'].includes(ext)) return '#f0a020';
    return '#888';
  }

  function reset() {
    current.value = 1;
    fileList.value = [];
    chunkingCfg.value = {
      chunkSize: 512,
      chunkOverlap: 80,
      separators: ['\n\n', '\n', '。', '！', '？', '；', ';'],
      enableParentChild: false,
      parentChunkSize: 1536,
      childChunkSize: 384,
      strategy: 'auto',
      tokenLimit: 0,
      languages: [],
      qaMode: false,
    };
    engineRules.value = [];
    previewDocs.value = [];
    activeDoc.value = 0;
    previewing.value = false;
    saving.value = false;
    editVisible.value = false;
    expandedFlags.value = [];
    renderedChunks.value = [];
    // 清预览进度态 + 停轮询 + 停计时器
    stopPreviewPoll();
    stopPreviewElapsed();
    previewProgress.value = null;
    // 清入库进度态 + 停轮询 + 停计时器（防止上次提交的残留）
    stopSavePoll();
    stopSaveElapsed();
    saveProgress.value = null;
    // 重置预览开关相关状态：默认开、未自动关、未手动改、非静默模式
    enablePreview.value = true;
    previewAutoOff.value = false;
    previewUserTouched.value = false;
    previewSilentMode.value = false;
    // 重置父块大小推荐 touched 标记，下次进入重新推荐
    parentChunkUserTouched.value = false;
  }

  function open() {
    if (!props.kbId) {
      message.warning('缺少知识库');
      return;
    }
    reset();
    show.value = true;
    // 预拉 MinerU 配置状态，供引擎选择时做引导提示（失败不阻断）
    loadMineruStatus(SERVICE_CATEGORY.MINERU_SELF);
    loadMineruStatus(SERVICE_CATEGORY.MINERU_CLOUD);
  }

  function onFileChange(options: { fileList: UploadFileInfo[] }) {
    fileList.value = options.fileList;
  }

  function delFile(index: number) {
    fileList.value.splice(index, 1);
  }

  function goStep2() {
    if (fileList.value.length === 0) {
      message.warning('请先选择文件');
      return;
    }
    current.value = 2;
    // 进入 Step 2 时重置手动改过标记，并立即按当前文件规模做一次自动判定
    // （从 Step 1 带大文件进来时能立即触发自动关闭预览）
    previewUserTouched.value = false;
    reevaluateAutoPreview();
    // 进入 Step 2 时按文件规模推荐一次父块大小（用户未手动改过才覆盖）
    applyRecommendedParentChunkSize();
  }

  /** 预览进度百分比（mineru 异步轮询时用） */
  const previewPercent = computed(() => {
    const p = previewProgress.value;
    if (!p || !p.total) return 0;
    return Math.min(100, Math.round((p.done / p.total) * 100));
  });

  /**
   * 已完成文件列表（文件级实时进度）：把后端 previewProgress.result 映射成 ProcessingStatus 的 completedItems。
   * 后端在 doPreviewAsync 的 whenComplete 回调里每完成一个文件就刷新一次进度桶的 result（保序快照），
   * 前端轮询拿到后实时展示「哪个文件已完成 + 切片数」，让并发效果可见，避免「卡着不动」错觉。
   * 后端 result 里失败文件返回空 chunks VO，这里按 chunks 判定 success/failed。
   */
  const previewCompletedItems = computed(() => {
    const p = previewProgress.value;
    if (!p || !Array.isArray(p.result)) return [];
    return p.result.map((d: any) => ({
      fileName: d.fileName || '未知文件',
      // 空 chunks（或失败占位）标记 failed，与后端 continue-on-failure 语义一致
      status: Array.isArray(d.chunks) && d.chunks.length > 0 ? 'success' : 'failed',
      chunkCount: Array.isArray(d.chunks) ? d.chunks.length : 0,
    }));
  });

  function stopPreviewPoll() {
    if (previewPollTimer) {
      clearTimeout(previewPollTimer);
      previewPollTimer = null;
    }
  }

  /**
   * mineru 异步预览轮询（链式 setTimeout，1.5s 间隔）。
   * status=done：从 result 取切片渲染（与非 mineru 同步路径用同一个 applyPreviewResult）；
   * status=failed：弹错误提示，停轮询。
   */
  function startPreviewPoll(taskId: string) {
    stopPreviewPoll();
    const poll = async () => {
      try {
        const res: any = await getPreviewProgress(taskId);
        if (res && res.code === 0 && res.data) {
          previewProgress.value = res.data;
          if (res.data.status === 'done') {
            previewing.value = false;
            applyPreviewResult(res.data.result || []);
            // 静默模式（直接入库）：mineru 异步解析完成后自动转入库
            if (previewSilentMode.value) {
              await runSaveFromPreview();
            }
            return; // 终止轮询
          }
          if (res.data.status === 'failed') {
            previewing.value = false;
            message.error(res.data.message || '解析失败');
            // 静默模式失败：回退 Step 2，让用户能重试或改回预览模式
            if (previewSilentMode.value) {
              previewSilentMode.value = false;
              current.value = 2;
            }
            return;
          }
        }
      } catch (e) {
        // 单次轮询失败不中断
      }
      previewPollTimer = setTimeout(poll, 1500);
    };
    poll();
  }

  /** 把后端返回的切片数组映射成 previewDocs 并渲染（同步/异步两路径共用） */
  function applyPreviewResult(docs: any[]) {
    previewDocs.value = docs
      .filter((d: any) => d && d.fileName != null)
      .map((d: any) => ({
        fileName: d.fileName,
        fileSize: Number(d.fileSize) || 0,
        // 透传原文件 MinIO key + 阶段耗时 + 实际引擎，给 save 用
        storageUrl: d.storageUrl || undefined,
        stages: Array.isArray(d.stages) ? d.stages : undefined,
        engine: d.engine || undefined,
        chunks: Array.isArray(d.chunks)
          ? d.chunks
              .filter((c: any) => c && c.content != null)
              .map((c: any) => ({
                title: c.title || '',
                content: String(c.content),
                parentContext: c.parentContext || undefined,
                parentIndex: c.parentIndex ?? undefined,
                questions: Array.isArray(c.questions) ? c.questions : undefined,
              }))
          : [],
      }));
    activeDoc.value = 0;
    if (previewDocs.value.length === 0) message.warning('未解析到任何切片');
    // 静默模式（直接入库）：不渲染切片 DOM，仅填好 previewDocs 供 save 读取，避免大文件卡顿
    if (!previewSilentMode.value) rebuildRenderedChunks();
  }

  /**
   * 构造 previewDocument 的入参（试切预览 / 直接入库共用）。
   * 抽出来避免两条路径参数构造重复，改分块配置时同步生效。
   */
  function buildPreviewRequest(files: File[]) {
    return {
      files,
      engine: 'tika' as const,
      // 引擎规则：用户改过下拉框就用 engineRules；没改过则按文件扩展名的默认引擎
      // 自动构造（doc/docx/xls/xlsx/csv→poi，其余→tika），保证后端 extEngineMap 始终
      // 有正确映射，避免统计图把 POI 误记成 tika。
      parserEngineRules:
        engineRules.value.length > 0 ? engineRules.value : buildDefaultEngineRules(files),
      chunkSize: chunkingCfg.value.chunkSize,
      overlap: chunkingCfg.value.chunkOverlap,
      strategy: chunkingCfg.value.strategy || undefined,
      enableParentChild: chunkingCfg.value.enableParentChild,
      parentChunkSize: chunkingCfg.value.enableParentChild
        ? chunkingCfg.value.parentChunkSize
        : undefined,
      childChunkSize: chunkingCfg.value.enableParentChild
        ? chunkingCfg.value.childChunkSize
        : undefined,
      qaMode: chunkingCfg.value.qaMode,
      separators:
        chunkingCfg.value.strategy === 'legacy' ? chunkingCfg.value.separators : undefined,
    };
  }

  async function doPreview() {
    const files = fileList.value.map((f) => f.file).filter((f): f is File => !!f);
    if (files.length === 0) {
      message.warning('文件不可用，请重新选择');
      return;
    }
    // 兜底拦截：选用了 MinerU 但未在「外部服务配置」启用，直接 block 并引导
    const badEngine = unconfiguredMineruEngine();
    if (badEngine) {
      message.error(
        `所选解析引擎「${
          badEngine === 'mineru' ? 'MinerU（复杂版式）' : 'MinerU（云端）'
        }」尚未配置或未启用，请先到「知识库 → 外部服务配置」完成配置`,
        { duration: 5000 }
      );
      return;
    }
    current.value = 3;
    expandedFlags.value = [];
    previewing.value = true;
    previewProgress.value = null; // 清上次的进度态（非 mineru 不用，mineru 轮询时填）
    previewSilentMode.value = false; // 试切预览：解析后正常渲染切片 DOM
    try {
      const res: any = await previewDocument(buildPreviewRequest(files));
      if (res && res.code === 0) {
        // mineru 场景：后端返回 {taskId}，启动进度轮询
        if (res.data && typeof res.data === 'object' && res.data.taskId) {
          previewProgress.value = {
            status: 'processing',
            total: files.length,
            done: 0,
            success: 0,
            failed: 0,
          };
          startPreviewPoll(res.data.taskId);
          return;
        }
        // 非 mineru 场景：后端直接返回切片数组
        if (Array.isArray(res.data)) {
          previewing.value = false;
          applyPreviewResult(res.data);
          return;
        }
        message.error(res?.message || '预览失败');
        previewing.value = false;
      } else {
        message.error(res?.message || '预览失败');
        previewing.value = false;
      }
    } catch (e: any) {
      message.error(e?.message || '预览失败');
      previewing.value = false;
    }
  }

  function rePreview() {
    doPreview();
  }

  /**
   * 直接入库：关闭预览时点击「直接入库」走此路径。
   * 与 doPreview 共用解析请求，但 previewSilentMode=true → 解析完成后不渲染切片 DOM，
   * 直接调 doSave 入库。对 mineru 异步预览，轮询完成后由 startPreviewPoll 自动转入库。
   */
  async function doDirectSave() {
    const files = fileList.value.map((f) => f.file).filter((f): f is File => !!f);
    if (files.length === 0) {
      message.warning('文件不可用，请重新选择');
      return;
    }
    const badEngine = unconfiguredMineruEngine();
    if (badEngine) {
      message.error(
        `所选解析引擎「${
          badEngine === 'mineru' ? 'MinerU（复杂版式）' : 'MinerU（云端）'
        }」尚未配置或未启用，请先到「知识库 → 外部服务配置」完成配置`,
        { duration: 5000 }
      );
      return;
    }
    current.value = 3;
    expandedFlags.value = [];
    previewing.value = true;
    previewProgress.value = null;
    previewSilentMode.value = true; // 静默模式：解析后不渲染 DOM，直接转 save
    try {
      const res: any = await previewDocument(buildPreviewRequest(files));
      if (res && res.code === 0) {
        // mineru 场景：异步轮询，done 后由 startPreviewPoll 自动调 runSaveFromPreview
        if (res.data && typeof res.data === 'object' && res.data.taskId) {
          previewProgress.value = {
            status: 'processing',
            total: files.length,
            done: 0,
            success: 0,
            failed: 0,
          };
          startPreviewPoll(res.data.taskId);
          return;
        }
        // 非 mineru 场景：同步拿到切片 → 不渲染 DOM → 直接转 save
        if (Array.isArray(res.data)) {
          previewing.value = false;
          applyPreviewResult(res.data);
          await runSaveFromPreview();
          return;
        }
        message.error(res?.message || '解析失败');
        previewing.value = false;
        previewSilentMode.value = false;
        current.value = 2;
      } else {
        message.error(res?.message || '解析失败');
        previewing.value = false;
        previewSilentMode.value = false;
        current.value = 2;
      }
    } catch (e: any) {
      message.error(e?.message || '解析失败');
      previewing.value = false;
      previewSilentMode.value = false;
      current.value = 2;
    }
  }

  /**
   * 静默解析完成后转入库（doDirectSave / mineru 轮询 done 共用）。
   * - 若解析得到的有效切片为空：回退正常预览态（渲染 DOM + warning），让用户能调整参数重试。
   * - 否则复用 doSave 入库（内部跳 Step 4 + 启动 savePoll）。
   * - finally 清 previewSilentMode，避免污染下一次试切预览。
   */
  async function runSaveFromPreview() {
    const valid = previewDocs.value.filter(
      (d) => d.chunks.length > 0 && d.chunks.some((c) => (c.content || '').trim())
    );
    if (valid.length === 0) {
      // 无有效切片：回退正常预览态，渲染出来让用户排查（参数/引擎问题）
      previewSilentMode.value = false;
      rebuildRenderedChunks();
      message.warning('未解析到任何有效切片，已切换为预览模式，请检查文件或调整参数');
      return;
    }
    try {
      await doSave();
    } finally {
      previewSilentMode.value = false;
    }
  }

  function editChunk(idx: number) {
    const c = currentChunks.value[idx];
    if (!c) return;
    editIndex.value = idx;
    editForm.value = { title: c.title || '', content: c.content };
    editVisible.value = true;
  }

  function saveEdit() {
    const content = (editForm.value.content || '').trim();
    if (!content) {
      message.warning('内容不能为空');
      return;
    }
    const chunks = currentChunks.value;
    const idx = editIndex.value;
    if (idx >= 0 && idx < chunks.length) {
      chunks[idx] = { ...chunks[idx], title: editForm.value.title || '', content };
      rebuildRenderedChunks();
    }
    editVisible.value = false;
  }

  function delChunk(idx: number) {
    currentChunks.value.splice(idx, 1);
    rebuildRenderedChunks();
  }

  function addChunk() {
    currentChunks.value.unshift({ title: '', content: '' });
    editIndex.value = 0;
    editForm.value = { title: '', content: '' };
    editVisible.value = true;
    rebuildRenderedChunks();
  }

  /** 入库进度百分比（n-progress 用） */
  const savePercent = computed(() => {
    const p = saveProgress.value;
    if (!p || !p.total) return 0;
    return Math.min(100, Math.round((p.done / p.total) * 100));
  });

  function stopSavePoll() {
    if (savePollTimer) {
      clearTimeout(savePollTimer);
      savePollTimer = null;
    }
  }

  /**
   * 轮询入库进度（链式 setTimeout，1.5s 间隔，单次失败不中断）。
   * status=done：通知父组件刷新列表 + 关弹窗；
   * status=failed：弹错误提示，用户可手动关弹窗。
   */
  function startSavePoll(taskId: string) {
    stopSavePoll();
    const poll = async () => {
      try {
        const res: any = await getSaveProgress(taskId);
        if (res && res.code === 0 && res.data) {
          saveProgress.value = res.data;
          if (res.data.status === 'done') {
            saving.value = false;
            message.success(
              `入库完成（成功 ${res.data.success} 个）${
                res.data.failed > 0 ? `，失败 ${res.data.failed} 个` : ''
              }，待向量化`
            );
            emit('uploaded');
            show.value = false;
            return; // 终止轮询
          }
          if (res.data.status === 'failed') {
            saving.value = false;
            message.error(res.data.message || '入库失败');
            return;
          }
        }
      } catch (e) {
        // 单次轮询失败（网络抖动等）不中断，继续下次轮询
      }
      savePollTimer = setTimeout(poll, 1500);
    };
    poll();
  }

  async function doSave() {
    const valid = previewDocs.value.filter(
      (d) => d.chunks.length > 0 && d.chunks.some((c) => (c.content || '').trim())
    );
    if (valid.length === 0) {
      message.warning('没有可保存的切片');
      return;
    }
    saving.value = true;
    try {
      const documentList: SaveDocItem[] = valid.map((d) => ({
        fileName: d.fileName,
        fileSize: d.fileSize,
        // 透传预览阶段已存的原文件 key + parse/chunk 阶段耗时 + 实际引擎
        storageUrl: d.storageUrl,
        stages: d.stages,
        engine: d.engine,
        chunks: d.chunks
          .filter((c) => (c.content || '').trim())
          .map((c) => ({
            title: c.title || '',
            content: c.content.trim(),
            parentContext: (c as any).parentContext || undefined,
          })),
      }));
      const res: any = await saveDocument({
        kbId: props.kbId,
        enableParentChild: chunkingCfg.value.enableParentChild,
        documentList,
      });
      if (res && res.code === 0 && res.data && res.data.taskId) {
        // 后端已派发异步入库任务，跳进步度步轮询
        current.value = 4;
        saveProgress.value = {
          status: 'processing',
          total: documentList.length,
          done: 0,
          success: 0,
          failed: 0,
        };
        startSavePoll(res.data.taskId);
      } else {
        saving.value = false;
        message.error(res?.message || '提交入库失败');
      }
    } catch (e: any) {
      saving.value = false;
      message.error(e?.message || '提交入库失败');
    }
  }

  // 组件常驻不销毁，但保险起见：卸载时清轮询 + 计时器，避免内存泄漏
  onUnmounted(() => {
    stopPreviewPoll();
    stopSavePoll();
    stopPreviewElapsed();
    stopSaveElapsed();
  });

  defineExpose({ open });
</script>

<style lang="less" scoped>
  .file-list {
    margin-top: 12px;
    max-height: 280px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .file-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    border: 1px solid #eee;
    border-radius: 6px;
    &:hover {
      border-color: #07c05f;
    }
  }
  .file-data {
    flex: 1;
    min-width: 0;
  }
  .file-name {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .file-size {
    font-size: 12px;
    color: #aaa;
    margin-top: 2px;
  }

  /* Step 2 */
  .step2-wrap {
    padding-bottom: 20px;
  }
  .section-block {
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #333;
    margin-bottom: 4px;
  }
  .section-desc {
    font-size: 12px;
    color: #888;
    margin-bottom: 12px;
  }

  /* 预览切片开关行（复用 ChunkingSettings 的 setting-row 布局） */
  .setting-row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 0;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  .setting-row--toggle {
    align-items: center;
  }
  .setting-info {
    flex: 0 0 60%;
    max-width: 60%;
    padding-right: 12px;
    label {
      font-size: 14px;
      font-weight: 500;
      color: #333;
      display: block;
      margin-bottom: 2px;
    }
    .desc {
      font-size: 12px;
      color: #888;
      margin: 0;
      line-height: 1.5;
    }
  }
  .setting-control {
    flex: 1;
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }

  /* 预览自动关闭警告横幅（红色，区别于 mineru 未配置警告） */
  .preview-autooff-warn {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
    padding: 8px 12px;
    background: #fff5f5;
    border: 1px solid #ffb3b3;
    border-radius: 6px;
  }
  .preview-autooff-warn-text {
    flex: 1;
    font-size: 12px;
    color: #d03050;
    line-height: 1.5;
  }

  /* 解析引擎分组 */
  .engine-groups {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .engine-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 12px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
  }
  .engine-label {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }
  .engine-ext {
    font-size: 13px;
    font-weight: 500;
    color: #333;
    white-space: nowrap;
  }
  /* MinerU 未配置提示横幅 */
  .mineru-warn {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
    padding: 8px 12px;
    background: #fff5f5;
    border: 1px solid #ffb3b3;
    border-radius: 6px;
  }
  .mineru-warn-text {
    flex: 1;
    font-size: 12px;
    color: #d03050;
    line-height: 1.5;
  }
  /* MinerU 图片处理策略说明（info 风格，区别于红色未配置警告） */
  .mineru-image-hint {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-top: 12px;
    padding: 10px 12px;
    background: #f0f7ff;
    border: 1px solid #a3d0ff;
    border-radius: 6px;
  }
  .mineru-image-hint-body {
    flex: 1;
    min-width: 0;
  }
  .mineru-image-hint-title {
    font-size: 12px;
    font-weight: 600;
    color: #2080f0;
    margin-bottom: 4px;
  }
  .mineru-image-hint-text {
    font-size: 12px;
    color: #555;
    line-height: 1.6;
  }
  .mineru-image-hint-text b {
    color: #2080f0;
    font-weight: 600;
  }

  /* Step 3 / Step 4 的转圈+计时器布局已抽到 ProcessingStatus 组件，这里仅保留页面级样式 */
  .doc-tabs {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 8px;
    margin-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }
  .doc-tab {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border: 1px solid #eee;
    border-radius: 16px;
    cursor: pointer;
    font-size: 13px;
    color: #666;
    transition: all 0.2s;
    &:hover {
      border-color: #07c05f;
      color: #07c05f;
    }
    &.active {
      border-color: #07c05f;
      color: #07c05f;
      background: rgba(7, 192, 95, 0.08);
    }
  }
  .doc-tab-name {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .preview-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .preview-head-info {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .preview-file {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  .preview-meta {
    font-size: 12px;
    color: #aaa;
  }

  /* 切片列表 */
  .chunk-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .chunk-item {
    border-radius: 4px;
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #eee;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    /* 兜底：即便 flex gap 因浏览器/容器场景未生效，margin-bottom 也能撑开间距 */
    margin-bottom: 16px;
  }
  .chunk-item:last-child {
    margin-bottom: 0;
  }
  .chunk-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid #f0f0f0;
  }
  .chunk-index {
    color: #aaa;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.5px;
  }
  .chunk-header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .chunk-meta {
    color: #ccc;
    font-size: 11px;
  }
  .chunk-content {
    font-size: 13px;
    line-height: 1.7;
    color: #555;
    word-break: break-word;
  }

  /* 父块上下文 */
  .parent-context-section {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed #eee;
  }
  .parent-context-toggle {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 12px;
    font-weight: 500;
    color: #07c05f;
    margin-bottom: 6px;
    padding: 4px 0;
    user-select: none;
  }
  .parent-context-hint {
    font-size: 11px;
    color: #aaa;
    font-weight: 400;
  }
  .parent-context-body {
    padding: 10px 12px;
    background: #f0fdf4;
    border-left: 3px solid #07c05f;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: #555;
  }
  /* markdown 渲染内容 */
  .md-body {
    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 12px 0 6px;
      /* 标题更黑更醒目（对齐 WeKnora：#000000e6 = rgba(0,0,0,0.9)） */
      color: #000000e6;
      font-weight: 700;
      line-height: 1.4;
      &:first-child {
        margin-top: 0;
      }
    }
    :deep(h1) {
      font-size: 21px;
    }
    :deep(h2) {
      font-size: 18px;
    }
    :deep(h3) {
      font-size: 16px;
    }
    :deep(h4) {
      font-size: 15px;
    }
    :deep(p) {
      margin: 0 0 8px;
      &:last-child {
        margin-bottom: 0;
      }
    }
    :deep(ul),
    :deep(ol) {
      margin: 4px 0 8px;
      padding-left: 20px;
    }
    :deep(li) {
      margin: 2px 0;
    }
    :deep(blockquote) {
      margin: 8px 0;
      padding: 4px 12px;
      border-left: 3px solid #ddd;
      color: #666;
    }
    :deep(code) {
      background: #f5f5f5;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
    :deep(pre) {
      background: #f5f5f5;
      padding: 10px 12px;
      border-radius: 4px;
      overflow-x: auto;
      margin: 8px 0;
      code {
        background: none;
        padding: 0;
      }
    }
    :deep(table) {
      border-collapse: collapse;
      margin: 8px 0;
      width: 100%;
      th,
      td {
        border: 1px solid #e0e0e0;
        padding: 6px 8px;
        font-size: 12px;
      }
      th {
        background: #f9f9f9;
        font-weight: 600;
      }
    }
    :deep(hr) {
      border: none;
      border-top: 1px solid #eee;
      margin: 12px 0;
    }
    :deep(img) {
      max-width: 100%;
      border-radius: 4px;
    }
  }

  /* 滚动条 */
  .scrollbar-thin {
    scrollbar-width: thin;
    &::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background: #ddd;
      border-radius: 3px;
    }
  }
</style>
