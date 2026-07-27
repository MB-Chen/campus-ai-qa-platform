<template>
  <div class="chunking-settings">
    <!-- QA 切分（仅 Excel/CSV） -->
    <div v-if="hasSpreadsheet" class="setting-row setting-row--toggle">
      <div class="setting-info">
        <label>QA 切分（仅 Excel/CSV）</label>
        <p class="desc">
          表格文件默认按行打包切分（列字母标记、按分块大小累加）；开启此项后改为 QA 切分——每行一个切片，第一列作标题（问题）、第二列作内容（答案），首行表头自动跳过
        </p>
      </div>
      <div class="setting-control">
        <n-switch v-model:value="local.qaMode" />
      </div>
    </div>

    <!-- 分块策略 -->
    <div class="setting-row">
      <div class="setting-info">
        <label>分块策略</label>
        <p class="desc">文档分析器根据内容结构自动选择切分方式</p>
      </div>
      <div class="setting-control">
        <n-select
          v-model:value="local.strategy"
          :options="strategyOptions"
          placeholder="选择策略"
          clearable
          style="width: 100%"
        />
      </div>
    </div>

    <!-- 策略释义面板 -->
    <div v-if="currentStrategyInfo" class="strategy-info">
      <strong>{{ currentStrategyInfo.label }}：</strong>{{ currentStrategyInfo.tooltip }}
    </div>

    <!-- 分块大小（开启父子分块后由父/子块大小接管，此项隐藏） -->
    <div v-if="!local.enableParentChild" class="setting-row">
      <div class="setting-info">
        <label>分块大小</label>
        <p class="desc">每个切片的目标字符数</p>
      </div>
      <div class="setting-control">
        <div class="slider-wrap">
          <n-slider
            v-model:value="local.chunkSize"
            :min="100"
            :max="4000"
            :step="50"
            :marks="{ 100: '100', 1000: '1k', 2000: '2k', 4000: '4k' }"
          />
          <span class="value-label">{{ local.chunkSize }} 字符</span>
        </div>
      </div>
    </div>

    <!-- 重叠字符（开启父子分块后仅子块使用，此项隐藏，子块重叠沿用此值） -->
    <div v-if="!local.enableParentChild" class="setting-row">
      <div class="setting-info">
        <label>重叠字符</label>
        <p class="desc">相邻切片之间重叠的字符数</p>
      </div>
      <div class="setting-control">
        <div class="slider-wrap">
          <n-slider
            v-model:value="local.chunkOverlap"
            :min="0"
            :max="500"
            :step="20"
            :marks="{ 0: '0', 250: '250', 500: '500' }"
          />
          <span class="value-label">{{ local.chunkOverlap }} 字符</span>
        </div>
      </div>
    </div>

    <!-- overlap 过大警告 -->
    <div v-if="overlapTooHigh" class="setting-warn">
      <n-icon size="14"><WarningOutlined /></n-icon>
      重叠相对于分块大小较大——切片之间会共享大部分内容
    </div>

    <!-- 分隔符（仅 legacy 策略生效） -->
    <div v-if="local.strategy === 'legacy'" class="setting-row">
      <div class="setting-info">
        <label>自定义分隔符</label>
        <p class="desc">选择或输入用于切分的分隔符（仅在「按长度切分」策略下生效）</p>
      </div>
      <div class="setting-control">
        <n-select
          v-model:value="local.separators"
          :options="separatorOptions"
          multiple
          filterable
          tag
          placeholder="选择或输入分隔符"
          style="width: 100%"
        />
      </div>
    </div>

    <!-- 父子分块开关 -->
    <div class="setting-row setting-row--toggle">
      <div class="setting-info">
        <label>父子分块</label>
        <p class="desc">
          大窗口切父块、小窗口切子块，检索命中子块后展开父块上下文。开启后由下方父/子块大小接管切分，「分块大小」与「重叠字符」不再生效
        </p>
      </div>
      <div class="setting-control">
        <n-switch v-model:value="local.enableParentChild" />
      </div>
    </div>

    <!-- 父块大小 -->
    <div v-if="local.enableParentChild" class="setting-row">
      <div class="setting-info">
        <label>父块大小</label>
        <p class="desc">父块的目标字符数（大窗口，用于检索时展开上下文）</p>
      </div>
      <div class="setting-control">
        <div class="slider-wrap">
          <n-slider
            v-model:value="local.parentChunkSize"
            :min="512"
            :max="8192"
            :step="64"
            :marks="{ 512: '512', 2048: '2k', 4096: '4k', 8192: '8k' }"
          />
          <span class="value-label">{{ local.parentChunkSize }} 字符</span>
        </div>
      </div>
    </div>

    <!-- 子块大小 -->
    <div v-if="local.enableParentChild" class="setting-row">
      <div class="setting-info">
        <label>子块大小</label>
        <p class="desc">子块的目标字符数（小窗口，实际用于向量检索）</p>
      </div>
      <div class="setting-control">
        <div class="slider-wrap">
          <n-slider
            v-model:value="local.childChunkSize"
            :min="64"
            :max="2048"
            :step="32"
            :marks="{ 64: '64', 384: '384', 1024: '1k', 2048: '2k' }"
          />
          <span class="value-label">{{ local.childChunkSize }} 字符</span>
        </div>
      </div>
    </div>

    <!-- 高级选项折叠 -->
    <div class="advanced-toggle" @click="advancedOpen = !advancedOpen">
      <n-icon size="14" :style="{ transform: advancedOpen ? 'rotate(90deg)' : 'rotate(0)' }">
        <RightOutlined />
      </n-icon>
      <span>高级选项</span>
    </div>

    <template v-if="advancedOpen">
      <!-- Token 限制 -->
      <div class="setting-row" :class="{ 'setting-disabled': advancedDisabled }">
        <div class="setting-info">
          <label>Token 限制</label>
          <p class="desc">以 token 为单位的上限（0 = 仅按字符）</p>
        </div>
        <div class="setting-control">
          <n-input-number
            v-model:value="local.tokenLimit"
            :min="0"
            :max="8192"
            :step="64"
            :disabled="advancedDisabled"
            placeholder="0"
            style="width: 100%"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, watch } from 'vue';
  import { RightOutlined, WarningOutlined } from '@vicons/antd';
  import type { ChunkingConfig } from './chunkingConfig';

  const props = defineProps<{
    config: ChunkingConfig;
    /** 是否存在表格类文件（xls/xlsx/csv），控制「一行一块」开关是否展示 */
    hasSpreadsheet?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:config', value: ChunkingConfig): void;
  }>();

  const local = ref<ChunkingConfig>({ ...props.config });
  const advancedOpen = ref(false);

  // 防止 props.config ↔ local 双向 watch 死循环：当本次 local 变更
  // 是由 props 同步回来引起时，用这个标志位阻断 emit，避免
  // 「props 变 → 写 local → local 变 → emit → props 变 → …」无限递归。
  let syncingFromProps = false;

  watch(
    () => props.config,
    (c) => {
      // 逐字段比较，完全一致就不重赋值（避免无意义触发 local 的 watcher）
      const cur = local.value;
      if (
        cur.chunkSize === c.chunkSize &&
        cur.chunkOverlap === c.chunkOverlap &&
        cur.enableParentChild === c.enableParentChild &&
        cur.parentChunkSize === c.parentChunkSize &&
        cur.childChunkSize === c.childChunkSize &&
        cur.strategy === c.strategy &&
        cur.tokenLimit === c.tokenLimit &&
        cur.qaMode === c.qaMode &&
        Array.isArray(cur.separators) &&
        Array.isArray(c.separators) &&
        cur.separators.length === c.separators.length &&
        cur.separators.every((v, i) => v === c.separators[i])
      ) {
        return;
      }
      syncingFromProps = true;
      local.value = {
        ...c,
        separators: [...c.separators],
        languages: [...(c.languages || [])],
        qaMode: !!c.qaMode,
      };
    },
    { deep: true }
  );

  const advancedDisabled = computed(() => local.value.strategy === 'legacy');

  const overlapTooHigh = computed(
    () => local.value.chunkOverlap > 0 && local.value.chunkOverlap >= local.value.chunkSize / 2
  );

  const strategyOptions = [
    {
      label: '自动 (auto)',
      value: 'auto',
      tooltip: '文档分析器根据内容结构自动在「按标题切分」「结构感知」「按长度切分」之间选择',
    },
    {
      label: '按标题切分 (heading)',
      value: 'heading',
      tooltip:
        '在 Markdown 标题（#、##、###）边界切分，每块带上所在标题路径。适合结构清晰的 Markdown',
    },
    {
      label: '结构感知 (heuristic)',
      value: 'heuristic',
      tooltip: '识别分页符、编号章节等结构信号。适合无 Markdown 标题的 PDF / 扫描件',
    },
    {
      label: '按长度切分 (legacy)',
      value: 'legacy',
      tooltip: '忽略结构，仅按字符数和分隔符递归切分。当上述策略效果不佳时使用',
    },
  ];

  const currentStrategyInfo = computed(() => {
    if (!local.value.strategy) return null;
    return strategyOptions.find((o) => o.value === local.value.strategy) ?? null;
  });

  const separatorOptions = [
    { label: '双换行 (\\n\\n)', value: '\n\n' },
    { label: '单换行 (\\n)', value: '\n' },
    { label: '中文句号 (。)', value: '。' },
    { label: '中文感叹号 (！)', value: '！' },
    { label: '中文问号 (？)', value: '？' },
    { label: '中文分号 (；)', value: '；' },
    { label: '英文分号 (;)', value: ';' },
    { label: '空格', value: ' ' },
  ];

  // 任何字段变化都 emit 完整 config
  function emitUpdate() {
    // 本次变更是 props → local 同步引起的，不要回传，否则会形成
    // props → local → emit → props 的递归环路。
    if (syncingFromProps) {
      syncingFromProps = false;
      return;
    }
    emit('update:config', {
      ...local.value,
      separators: [...local.value.separators],
      languages: [...(local.value.languages || [])],
    });
  }

  // 深度监听 local 变化自动 emit
  watch(local, emitUpdate, { deep: true });
</script>

<style lang="less" scoped>
  .chunking-settings {
    display: flex;
    flex-direction: column;
    gap: 0;
  }
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
  .setting-disabled {
    opacity: 0.5;
    pointer-events: none;
  }
  .setting-info {
    flex: 0 0 38%;
    max-width: 38%;
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
    min-width: 0;
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }
  .slider-wrap {
    display: flex;
    align-items: center;
    gap: 14px;
    width: 100%;
    justify-content: flex-end;
  }
  .value-label {
    flex-shrink: 0;
    font-size: 13px;
    font-weight: 600;
    color: #07c05f;
    min-width: 70px;
    text-align: right;
  }
  .strategy-info {
    margin: -4px 0 10px;
    padding: 8px 12px;
    background: #f0fdf4;
    border-left: 3px solid #07c05f;
    border-radius: 0 4px 4px 0;
    font-size: 12px;
    color: #555;
    line-height: 1.5;
    strong {
      color: #333;
    }
  }
  .setting-warn {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: -4px 0 10px;
    padding: 6px 10px;
    background: #fff7ed;
    border-left: 3px solid #f0a020;
    border-radius: 0 4px 4px 0;
    font-size: 12px;
    color: #b45309;
  }
  .advanced-toggle {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 12px 0 6px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    color: #888;
    user-select: none;
    &:hover {
      color: #333;
    }
    .n-icon {
      transition: transform 0.15s ease;
    }
  }
</style>
