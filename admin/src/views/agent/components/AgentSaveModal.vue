<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="mode === 'create' ? '新建智能体' : '编辑智能体'"
    style="width: 820px"
    :mask-closable="false"
  >
    <div class="agent-editor">
      <!-- 左侧菜单 -->
      <div class="editor-menu">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="menu-item"
          :class="{ active: menuKey === item.key }"
          @click="menuKey = item.key"
        >
          <n-icon size="18" class="menu-icon"><component :is="item.icon" /></n-icon>
          <span class="menu-label">{{ item.label }}</span>
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="editor-content">
        <n-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-placement="left"
          label-width="110px"
        >
          <template v-if="menuKey === 'basic'">
            <n-form-item label="名称" path="name">
              <n-input
                v-model:value="form.name"
                placeholder="请输入智能体名称"
                :maxlength="25"
                show-count
              />
            </n-form-item>
            <n-form-item label="描述">
              <n-input
                v-model:value="form.description"
                type="textarea"
                :rows="2"
                placeholder="可选"
                :maxlength="255"
              />
            </n-form-item>
            <n-form-item label="知识库">
              <n-radio-group v-model:value="form.kbMode">
                <n-radio-button v-for="o in KB_MODE_OPTIONS" :key="o.value" :value="o.value">{{
                  o.label
                }}</n-radio-button>
              </n-radio-group>
            </n-form-item>
            <template v-if="form.kbMode === 'selected'">
              <n-form-item label="关联知识库" path="knowledgeBaseIds">
                <n-select
                  v-model:value="form.knowledgeBaseIds"
                  :options="kbOptions"
                  multiple
                  filterable
                  placeholder="选择知识库（可多选）"
                  :loading="kbLoading"
                  @update:value="onKbChange"
                />
              </n-form-item>
              <n-form-item label="限定文档">
                <n-select
                  v-model:value="form.documentIds"
                  :options="docOptions"
                  multiple
                  filterable
                  placeholder="留空=检索整库；可选具体文档"
                  :loading="docLoading"
                  max-tag-count="8"
                />
                <n-text depth="3" style="font-size: 12px; margin-left: 8px; white-space: nowrap">
                  不选则检索所选知识库全部文档
                </n-text>
              </n-form-item>
            </template>
            <n-form-item v-if="form.kbMode === 'all'" label=" " style="margin-top: -16px">
              <n-text depth="3" style="font-size: 12px">将检索系统中全部知识库</n-text>
            </n-form-item>
            <n-form-item v-if="form.kbMode === 'none'" label=" " style="margin-top: -16px">
              <n-text depth="3" style="font-size: 12px"
                >不检索知识库，纯 LLM 对话（需自定义提示词约束回答）</n-text
              >
            </n-form-item>
            <n-form-item label="对话模型">
              <n-select
                v-model:value="form.chatModelKey"
                :options="modelOptions"
                filterable
                clearable
                placeholder="留空则走系统默认模型"
                :loading="modelLoading"
                @update:value="(val) => handleChatModelChange(val)"
              />
              <n-text depth="3" style="margin-left: 8px; font-size: 12px; white-space: nowrap">
                {{ form.chatModelName || '未选择' }}
              </n-text>
            </n-form-item>
            <n-form-item label="状态">
              <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="2">
                <template #checked>正常</template>
                <template #unchecked>禁用</template>
              </n-switch>
            </n-form-item>
          </template>

          <template v-if="menuKey === 'prompt'">
            <n-form-item label="系统提示词">
              <n-input
                v-model:value="form.systemPrompt"
                type="textarea"
                :rows="10"
                placeholder="自定义系统提示词（人设/约束）。留空则使用知识库默认回答模板。"
              />
            </n-form-item>
            <n-form-item label="开场白">
              <n-input
                v-model:value="form.welcome"
                type="textarea"
                :rows="2"
                placeholder="可选，对话开始时展示"
              />
            </n-form-item>
            <n-form-item label="推荐问题">
              <div style="width: 100%">
                <n-space>
                  <n-tag
                    v-for="(q, i) in form.suggestedQuestions"
                    :key="i"
                    closable
                    closable-size="14"
                    @close="form.suggestedQuestions!.splice(i, 1)"
                    >{{ q }}</n-tag
                  >
                </n-space>
                <n-input-group style="margin-top: 8px">
                  <n-input
                    v-model:value="newQuestion"
                    placeholder="输入推荐问题回车添加"
                    style="width: 320px"
                    @keyup.enter="addQuestion"
                  />
                  <n-button @click="addQuestion">添加</n-button>
                </n-input-group>
              </div>
            </n-form-item>
          </template>

          <template v-if="menuKey === 'model'">
            <n-form-item :label="`温度  ${form.temperature?.toFixed(2)}`">
              <n-slider
                v-model:value="form.temperature"
                :min="0"
                :max="2"
                :step="0.05"
                :marks="{ 0: '0', 1: '1', 2: '2' }"
              />
            </n-form-item>
            <n-form-item label="最大生成 token" :label-width="130">
              <n-input-number v-model:value="form.maxTokens" :min="128" :max="8192" :step="128" />
            </n-form-item>
            <n-form-item :label="`记忆轮数  ${form.historyTurns}`">
              <n-slider v-model:value="form.historyTurns" :min="0" :max="20" :step="1" />
              <n-text depth="3" style="font-size: 12px; margin-left: 8px; white-space: nowrap"
                >0=不带历史</n-text
              >
            </n-form-item>
            <n-form-item label="意图/改写模型" :label-width="130">
              <n-select
                v-model:value="form.rewriteModelKey"
                :options="modelOptions"
                filterable
                clearable
                placeholder="留空则用对话默认模型"
                :loading="modelLoading"
                @update:value="(val) => handleRewriteModelChange(val)"
              />
              <n-text depth="3" style="margin-left: 8px; font-size: 12px; white-space: nowrap">
                {{ form.rewriteModelName || '未选择' }}
              </n-text>
            </n-form-item>
            <n-text depth="3" style="font-size: 12px; display: block; margin-top: -8px">
              可选一个小快模型（如 qwen-turbo ）用于意图分类与查询改写，降本提速；主回答仍用对话默认模型。
            </n-text>
          </template>

          <template v-if="menuKey === 'retrieval'">
            <n-form-item label="样例查询">
              <n-switch v-model:value="form.sampleQueryEnabled" :checked-value="1" :unchecked-value="2">
                <template #checked>启用</template>
                <template #unchecked>禁用</template>
              </n-switch>
              <n-text depth="3" style="margin-left: 8px; font-size: 12px; white-space: nowrap">
                用户消息优先到样例库向量匹配
              </n-text>
            </n-form-item>
            <n-form-item label="样例匹配阈值">
              <n-input-number
                v-model:value="form.sampleQueryThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :disabled="form.sampleQueryEnabled === 2"
              />
              <n-text depth="3" style="margin-left: 8px; font-size: 12px; white-space: nowrap">
                达到阈值直接返回样例答案，LLM 不参与
              </n-text>
            </n-form-item>
            <n-form-item label=" " style="margin-top: -16px">
              <n-text depth="3" style="font-size: 12px"
                >留空阈值则用全局配置（样例查询管理页）。样例需先在「样例查询」中向量化后才可被匹配。</n-text
              >
            </n-form-item>
            <n-divider style="margin: 8px 0 16px" />
            <n-form-item label="向量召回 topK">
              <n-input-number v-model:value="form.embeddingTopK" :min="1" :max="50" />
            </n-form-item>
            <n-form-item label="向量相似度阈值">
              <n-input-number v-model:value="form.vectorThreshold" :min="0" :max="1" :step="0.05" />
            </n-form-item>
            <n-form-item label="关键词阈值">
              <n-input-number
                v-model:value="form.keywordThreshold"
                :min="0"
                :max="1"
                :step="0.05"
              />
            </n-form-item>
            <n-form-item label="启用重排">
              <n-switch v-model:value="form.rerankEnabled" :checked-value="1" :unchecked-value="2">
                <template #checked>启用</template>
                <template #unchecked>禁用</template>
              </n-switch>
            </n-form-item>
            <n-form-item label="重排模型">
              <n-select
                v-model:value="form.rerankModelKey"
                :options="rerankModelOptions"
                filterable
                clearable
                placeholder="留空则用系统默认重排（基于向量相似度）"
                :loading="rerankModelLoading"
                :disabled="form.rerankEnabled === 2"
              />
            </n-form-item>
            <n-form-item label="重排 topK">
              <n-input-number
                v-model:value="form.rerankTopK"
                :min="1"
                :max="30"
                :disabled="form.rerankEnabled === 2"
              />
            </n-form-item>
            <n-form-item label="重排阈值">
              <n-input-number
                v-model:value="form.rerankThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :disabled="form.rerankEnabled === 2"
              />
            </n-form-item>
          </template>

          <template v-if="menuKey === 'fallback'">
            <n-form-item label="兜底策略">
              <n-radio-group v-model:value="form.fallbackStrategy">
                <n-radio-button v-for="o in FALLBACK_OPTIONS" :key="o.value" :value="o.value">{{
                  o.label
                }}</n-radio-button>
              </n-radio-group>
            </n-form-item>
            <n-form-item v-if="form.fallbackStrategy === 'fixed'" label="兜底话术">
              <n-input
                v-model:value="form.fallbackResponse"
                type="textarea"
                :rows="3"
                placeholder="当知识库无法召回时的固定回复话术"
              />
            </n-form-item>
          </template>
        </n-form>
      </div>
    </div>

    <template #footer>
      <n-space justify="end">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" strong secondary :loading="saving" @click="handleSave"
          >确定</n-button
        >
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import {
    addAgent,
    editAgent,
    FALLBACK_OPTIONS,
    KB_MODE_OPTIONS,
    type Agent,
    type AgentSave,
  } from '@/api/system/agent';
  import { getKbList, getDocumentList, type KnowledgeBase } from '@/api/system/knowledge';
  import { getModelList, getRerankModelList, type AiModel } from '@/api/system/aiModel';
  import {
    SettingOutlined,
    CommentOutlined,
    DeploymentUnitOutlined,
    SearchOutlined,
    SafetyCertificateOutlined,
  } from '@vicons/antd';

  const emit = defineEmits<{ (e: 'saved'): void }>();
  const message = useMessage();

  const show = ref(false);
  const mode = ref<'create' | 'edit'>('create');
  const saving = ref(false);
  const formRef = ref<FormInst | null>(null);
  const menuKey = ref('basic');

  const kbLoading = ref(false);
  const kbOptions = ref<{ label: string; value: string }[]>([]);
  const modelLoading = ref(false);
  // ★ value 为 `${modelId}::${modelName}`（一个 ai_model 多模型名时拉平成多条），与 rerankModelOptions 一致
  const modelOptions = ref<{ label: string; value: string }[]>([]);
  const rerankModelLoading = ref(false);
  // ★ value 为 `${modelId}::${modelName}`（一个 ai_model 多模型名时拉平成多条），参照 KbSaveModal
  const rerankModelOptions = ref<{ label: string; value: string }[]>([]);
  const docLoading = ref(false);
  const docOptions = ref<{ label: string; value: string }[]>([]);
  const newQuestion = ref('');

  const menuItems = [
    { key: 'basic', icon: SettingOutlined, label: '基础' },
    { key: 'prompt', icon: CommentOutlined, label: '提示词' },
    { key: 'model', icon: DeploymentUnitOutlined, label: '模型参数' },
    { key: 'retrieval', icon: SearchOutlined, label: '检索' },
    { key: 'fallback', icon: SafetyCertificateOutlined, label: '兜底' },
  ];

  const form = reactive<AgentSave>(defaultForm());

  function defaultForm(): AgentSave {
    return {
      id: '',
      name: '',
      description: '',
      avatar: '🤖',
      kbMode: 'selected',
      knowledgeBaseIds: [],
      documentIds: [],
      // ★ 对话模型组合 key：`${modelId}::${modelName}`（一对多拉平，提交时拆成 id+name）
      chatModelKey: null as string | null,
      chatModelName: '',
      systemPrompt: '',
      temperature: 0.3,
      maxTokens: 2048,
      historyTurns: 4,
      embeddingTopK: 10,
      vectorThreshold: 0.2,
      keywordThreshold: 0.3,
      rerankModelId: undefined,
      rerankModelName: '',
      // ★ 重排模型组合 key：`${modelId}::${modelName}`（一对多拉平，提交时拆成 id+name）
      rerankModelKey: null as string | null,
      rerankEnabled: 1,
      rerankTopK: 5,
      rerankThreshold: 0.3,
      // ★ 改写模型组合 key：`${modelId}::${modelName}`（一对多拉平，提交时拆成 id+name）
      rewriteModelKey: null as string | null,
      rewriteModelName: '',
      fallbackStrategy: 'model',
      fallbackResponse: '',
      // ★ 样例查询优先匹配：默认关闭（2），阈值默认 0.85（与全局配置一致）
      sampleQueryEnabled: 2,
      sampleQueryThreshold: 0.85,
      welcome: '',
      suggestedQuestions: [],
      status: 1,
    };
  }

  const rules: FormRules = {
    name: [{ required: true, message: '请输入智能体名称', trigger: ['blur', 'input'] }],
  };

  async function loadKbOptions() {
    kbLoading.value = true;
    try {
      const res: any = await getKbList({ size: 500 });
      if (res && res.code === 0 && res.data && Array.isArray(res.data.data)) {
        kbOptions.value = (res.data.data as KnowledgeBase[])
          .filter((x) => x && x.id)
          .map((kb) => ({ label: kb.name || '未命名', value: kb.id as string }));
      } else {
        kbOptions.value = [];
      }
    } catch {
      kbOptions.value = [];
    } finally {
      kbLoading.value = false;
    }
  }

  /**
   * ★ 对话模型拉平：一个 ai_model 的 models 逗号分隔时，拆成每个具体模型一条 option。
   *   value 编码 `${modelId}::${modelName}`，与 rerankModelOptions / KbSaveModal 一致，
   *   让用户能选到具体子模型（如 gpt-4o-mini,gpt-4o 拆成两条）。
   */
  function toModelOptions(models: AiModel[]): { label: string; value: string }[] {
    const opts: { label: string; value: string }[] = [];
    for (const m of models) {
      const names = (m.models || '')
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (names.length === 0) {
        opts.push({ label: `${m.name || ''}（未配置模型名）`, value: `${m.id}::` });
        continue;
      }
      for (const n of names) {
        opts.push({ label: `${m.name || ''} / ${n}`, value: `${m.id}::${n}` });
      }
    }
    return opts;
  }

  async function loadModelOptions() {
    modelLoading.value = true;
    try {
      const res: any = await getModelList({ type: 1, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        modelOptions.value = toModelOptions((res.data as AiModel[]).filter((x) => x && x.id != null));
      } else {
        modelOptions.value = [];
      }
    } catch {
      modelOptions.value = [];
    } finally {
      modelLoading.value = false;
    }
  }

  /**
   * ★ 重排模型拉平：一个 ai_model 的 models 逗号分隔时，拆成每个具体模型一条 option。
   *   value 编码 `${modelId}::${modelName}`，与 KbSaveModal 嵌入模型选择一致。
   *   这样百度千帆配 bce-reranker-base,qwen3-reranker-0.6b 时，下拉显示两条可选。
   */
  function toRerankModelOptions(models: AiModel[]): { label: string; value: string }[] {
    const opts: { label: string; value: string }[] = [];
    for (const m of models) {
      const names = (m.models || '')
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (names.length === 0) {
        // 没配模型名也展示一条（后端会取首项/报错），避免下拉空
        opts.push({ label: `${m.name || ''}（未配置模型名）`, value: `${m.id}::` });
        continue;
      }
      for (const n of names) {
        opts.push({ label: `${m.name || ''} / ${n}`, value: `${m.id}::${n}` });
      }
    }
    return opts;
  }

  async function loadRerankModelOptions() {
    rerankModelLoading.value = true;
    try {
      const res: any = await getRerankModelList();
      if (res && res.code === 0 && Array.isArray(res.data)) {
        rerankModelOptions.value = toRerankModelOptions(
          (res.data as AiModel[]).filter((x) => x && x.id != null),
        );
      } else {
        rerankModelOptions.value = [];
      }
    } catch {
      rerankModelOptions.value = [];
    } finally {
      rerankModelLoading.value = false;
    }
  }

  /** 选中对话模型时，同步回填 chatModelName 快照（从组合 key 拆出具体子模型名） */
  function handleChatModelChange(key: string | null) {
    form.chatModelName = key ? decodeModelName(key) : '';
  }

  /** 选中意图/改写模型时，同步回填 rewriteModelName 快照（从组合 key 拆出具体子模型名） */
  function handleRewriteModelChange(key: string | null) {
    form.rewriteModelName = key ? decodeModelName(key) : '';
  }

  /** 从 `${modelId}::${modelName}` 组合 key 拆出具体子模型名 */
  function decodeModelName(key: string): string {
    const sepIdx = key.indexOf('::');
    return sepIdx >= 0 ? key.substring(sepIdx + 2) : '';
  }

  /** 知识库选择变化：清空已失效的文档选择 + 重新加载文档列表 */
  function onKbChange(kbIds: string[]) {
    if (form.documentIds && form.documentIds.length) {
      form.documentIds = [];
    }
    loadDocOptions(new Set(kbIds || []));
  }

  /** 加载选中知识库下的文档列表（多库合并，文档名前缀知识库名） */
  async function loadDocOptions(kbIds: Set<string>) {
    if (kbIds.size === 0) {
      docOptions.value = [];
      return;
    }
    docLoading.value = true;
    try {
      const allDocs: { label: string; value: string }[] = [];
      const tasks = Array.from(kbIds).map(async (kbId) => {
        const kbName = kbOptions.value.find((k) => k.value === kbId)?.label || kbId;
        try {
          const res: any = await getDocumentList({ kbId, size: 200 });
          if (res && res.code === 0 && res.data && Array.isArray(res.data.data)) {
            for (const d of res.data.data) {
              if (d && d.id) {
                allDocs.push({ label: `[${kbName}] ${d.fileName || d.id}`, value: d.id });
              }
            }
          }
        } catch {
          // 单库加载失败跳过
        }
      });
      await Promise.all(tasks);
      docOptions.value = allDocs;
    } finally {
      docLoading.value = false;
    }
  }

  function openCreate() {
    mode.value = 'create';
    menuKey.value = 'basic';
    Object.assign(form, defaultForm());
    show.value = true;
    loadKbOptions();
    loadModelOptions();
    loadRerankModelOptions();
  }

  function openEdit(ag: Agent) {
    mode.value = 'edit';
    menuKey.value = 'basic';
    Object.assign(form, {
      id: ag.id || '',
      name: ag.name || '',
      description: ag.description || '',
      avatar: ag.avatar || '🤖',
      kbMode: ag.kbMode || 'selected',
      knowledgeBaseIds: Array.isArray(ag.knowledgeBaseIds) ? [...ag.knowledgeBaseIds] : [],
      documentIds: Array.isArray(ag.documentIds) ? [...ag.documentIds] : [],
      // ★ 回显：把 id + name 拼成组合 key（与 option value 格式一致）
      chatModelKey: ag.chatModelId != null ? `${ag.chatModelId}::${ag.chatModelName || ''}` : null,
      chatModelName: ag.chatModelName || '',
      systemPrompt: ag.systemPrompt || '',
      temperature: ag.temperature ?? 0.3,
      maxTokens: ag.maxTokens ?? 2048,
      historyTurns: ag.historyTurns ?? 4,
      embeddingTopK: ag.embeddingTopK ?? 10,
      vectorThreshold: ag.vectorThreshold ?? 0.2,
      keywordThreshold: ag.keywordThreshold ?? 0.3,
      rerankModelId: ag.rerankModelId,
      rerankModelName: ag.rerankModelName || '',
      // ★ 回显：把 id + name 拼成组合 key（与 option value 格式一致）
      rerankModelKey:
        ag.rerankModelId != null ? `${ag.rerankModelId}::${ag.rerankModelName || ''}` : null,
      rerankEnabled: ag.rerankEnabled ?? 1,
      rerankTopK: ag.rerankTopK ?? 5,
      rerankThreshold: ag.rerankThreshold ?? 0.3,
      // ★ 回显：把 id + name 拼成组合 key（与 option value 格式一致）
      rewriteModelKey: ag.rewriteModelId != null ? `${ag.rewriteModelId}::${ag.rewriteModelName || ''}` : null,
      rewriteModelName: ag.rewriteModelName || '',
      fallbackStrategy: ag.fallbackStrategy || 'model',
      fallbackResponse: ag.fallbackResponse || '',
      // ★ 回显：样例查询开关（默认关闭 2）+ 阈值（默认 0.85）
      sampleQueryEnabled: ag.sampleQueryEnabled ?? 2,
      sampleQueryThreshold: ag.sampleQueryThreshold ?? 0.85,
      welcome: ag.welcome || '',
      suggestedQuestions: Array.isArray(ag.suggestedQuestions) ? [...ag.suggestedQuestions] : [],
      status: ag.status || 1,
    });
    show.value = true;
    loadModelOptions();
    loadRerankModelOptions();
    loadKbOptions().then(() => {
      if (form.knowledgeBaseIds && form.knowledgeBaseIds.length) {
        loadDocOptions(new Set(form.knowledgeBaseIds));
      }
    });
  }

  function addQuestion() {
    const q = newQuestion.value.trim();
    if (!q) return;
    if (!form.suggestedQuestions) form.suggestedQuestions = [];
    form.suggestedQuestions.push(q);
    newQuestion.value = '';
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch {
      return;
    }
    if (
      form.kbMode === 'selected' &&
      (!form.knowledgeBaseIds || form.knowledgeBaseIds.length === 0)
    ) {
      message.warning('指定知识库模式下请至少选择一个知识库');
      return;
    }
    saving.value = true;
    try {
      // ★ 拆 chatModelKey / rewriteModelKey / rerankModelKey → id + name，与 KbSaveModal 一致
      const { chatModelKey, rewriteModelKey, rerankModelKey, ...rest } = form;
      const payload: AgentSave = { ...rest } as AgentSave;

      if (chatModelKey) {
        const sepIdx = chatModelKey.indexOf('::');
        payload.chatModelId = Number(chatModelKey.substring(0, sepIdx));
        payload.chatModelName = chatModelKey.substring(sepIdx + 2) || undefined;
      } else {
        payload.chatModelId = undefined;
        payload.chatModelName = undefined;
      }

      if (rewriteModelKey) {
        const sepIdx = rewriteModelKey.indexOf('::');
        payload.rewriteModelId = Number(rewriteModelKey.substring(0, sepIdx));
        payload.rewriteModelName = rewriteModelKey.substring(sepIdx + 2) || undefined;
      } else {
        payload.rewriteModelId = undefined;
        payload.rewriteModelName = undefined;
      }

      if (rerankModelKey) {
        const sepIdx = rerankModelKey.indexOf('::');
        payload.rerankModelId = Number(rerankModelKey.substring(0, sepIdx));
        payload.rerankModelName = rerankModelKey.substring(sepIdx + 2) || undefined;
      } else {
        payload.rerankModelId = undefined;
        payload.rerankModelName = undefined;
      }
      const res: any = mode.value === 'create' ? await addAgent(payload) : await editAgent(payload);
      if (res && res.code === 0) {
        message.success(mode.value === 'create' ? '智能体已创建' : '已保存');
        show.value = false;
        emit('saved');
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>

<style lang="less" scoped>
  .agent-editor {
    display: flex;
    height: 520px;
    border: 1px solid #eee;
    border-radius: 8px;
    overflow: hidden;
  }

  .editor-menu {
    width: 150px;
    flex-shrink: 0;
    background: #fafafa;
    border-right: 1px solid #eee;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    color: #555;
    transition: all 0.15s;
    &:hover {
      background: #f0f0f0;
    }
    &.active {
      background: #e8f5e9;
      color: #07c05f;
      font-weight: 600;
    }
  }
  .menu-icon {
    font-size: 16px;
    line-height: 1;
  }
  .menu-label {
    white-space: nowrap;
  }

  .editor-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px 24px;
  }
</style>
