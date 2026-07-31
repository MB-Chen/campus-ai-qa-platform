<template>
  <n-drawer v-model:show="show" :width="720" placement="right" :mask-closable="false">
    <n-drawer-content :title="form.id ? '编辑模型' : '新增模型'" closable :native-scrollbar="false">
      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        label-width="100px"
        autocomplete="off"
      >
        <!-- 假输入框：吸收浏览器对密码/账号的自动回填 -->
        <input type="text" name="fake-user" style="display: none" autocomplete="off" />
        <input type="password" name="fake-pass" style="display: none" autocomplete="new-password" />

        <n-form-item label="模型名称" path="name">
          <n-input
            v-model:value="form.name"
            placeholder="如 OpenAI、通义千问"
            :maxlength="100"
            autocomplete="off"
          />
        </n-form-item>
        <n-form-item label="模型类型" path="type">
          <n-select
            v-model:value="form.type"
            :options="typeOptions"
            :disabled="!!form.id"
            @update:value="onTypeChange"
          />
        </n-form-item>
        <n-form-item label="接入方式" path="provider">
          <n-select
            v-model:value="form.provider"
            :options="providerOptionsByType"
            @update:value="onProviderChange"
          />
        </n-form-item>

        <!-- 凭证（动态字段） -->
        <n-divider title-placement="left" style="margin-top: 8px">凭证配置</n-divider>
        <n-space vertical>
          <div v-for="(c, idx) in credentialList" :key="'c' + idx" class="kv-row">
            <span v-if="isPresetCredentialField(c.field)" class="preset-field-name">{{
              fieldLabel(c.field)
            }}</span>
            <n-input
              v-else
              v-model:value="c.field"
              placeholder="字段名"
              size="small"
              style="width: 160px"
            />
            <n-input
              v-model:value="c.value"
              placeholder="字段值"
              size="small"
              style="flex: 1"
              :autocomplete="isPresetCredentialField(c.field) ? 'new-password' : 'off'"
            />
          </div>
        </n-space>

        <!-- 模型名 -->
        <n-divider title-placement="left">模型列表</n-divider>
        <n-form-item :label="form.type === 3 ? '重排模型名' : '可用模型名'">
          <n-input
            v-model:value="form.models"
            :placeholder="
              form.type === 3
                ? '重排模型只允许一个（如 rerank-v1）'
                : '逗号分隔（如 gpt-4o-mini,gpt-4o）'
            "
          />
        </n-form-item>

        <!-- options 动态字段：url / temperature / maxOutputTokens 等 -->
        <n-divider title-placement="left">调用选项</n-divider>
        <n-space vertical>
          <div v-for="(o, idx) in optionsList" :key="'o' + idx" class="kv-row">
            <!-- 预设字段名只读文本，自定义字段才用输入框 -->
            <span v-if="isPresetOptionField(o.field)" class="preset-field-name">{{ fieldLabel(o.field) }}</span>
            <n-input
              v-else
              v-model:value="o.field"
              placeholder="字段名"
              size="small"
              style="width: 160px"
            />
            <!-- 字段值：数字字段用滑块 + 数字输入框，其余用普通输入框 -->
            <template v-if="isNumberField(o.field)">
              <n-slider
                v-model:value="o.value"
                :min="o.range ? o.range[0] : 0"
                :max="o.range ? o.range[1] : 2"
                :step="o.field === 'temperature' ? 0.1 : 1"
                style="flex: 1"
              />
              <n-input-number
                v-model:value="o.value"
                size="small"
                style="width: 120px"
                :min="o.range ? o.range[0] : undefined"
                :max="o.range ? o.range[1] : undefined"
                :step="o.field === 'temperature' ? 0.1 : 1"
              />
            </template>
            <n-input
              v-else
              v-model:value="o.value"
              :placeholder="
                o.field === 'url'
                  ? '完整接口地址，含路径，如 https://.../chat/completions'
                  : '字段值'
              "
              size="small"
              style="flex: 1"
            />
          </div>
        </n-space>

        <!-- 能力配置（仅对话模型） -->
        <template v-if="form.type === 1">
          <n-divider title-placement="left">能力配置</n-divider>
          <n-form-item label="深度思考">
            <n-switch v-model:value="form.supportsThinking" :checked-value="1" :unchecked-value="0">
              <template #checked>支持</template>
              <template #unchecked>不支持</template>
            </n-switch>
          </n-form-item>
        </template>

        <n-divider title-placement="left">状态与优先级</n-divider>
        <n-form-item label="状态">
          <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="2">
            <template #checked>启用</template>
            <template #unchecked>禁用</template>
          </n-switch>
        </n-form-item>
        <n-form-item label="优先级">
          <n-input-number v-model:value="form.priority" :min="0" style="width: 200px" />
          <span style="margin-left: 8px; color: #aaa; font-size: 12px"
            >数值小者优先（多模型容错降级）</span
          >
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="space-between" style="width: 100%">
          <n-tooltip :disabled="canTest">
            <template #trigger>
              <n-button :loading="testing" :disabled="!canTest" @click="handleTest">
                <template #icon
                  ><n-icon><ApiOutlined /></n-icon
                ></template>
                测试连接
              </n-button>
            </template>
            <span>{{ testDisabledReason }}</span>
          </n-tooltip>
          <n-space>
            <n-button @click="show = false">取消</n-button>
            <n-button type="primary" strong secondary :loading="saving" @click="handleSave"
              >保存</n-button
            >
          </n-space>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, reactive, computed } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import { getField } from '@/api/system/aiModel';
  import { ApiOutlined } from '@vicons/antd';
  import {
    addModel,
    editModel,
    testModelConnect,
    parseFieldJson,
    stringifyFieldJson,
    getProviderOptionsByType,
    isProviderSupportedByType,
    type AiModel,
    type FieldValue,
  } from '@/api/system/aiModel';

  const emit = defineEmits<{
    (e: 'saved'): void;
  }>();

  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);
  const testing = ref(false);
  const formRef = ref<FormInst | null>(null);

  const form = reactive({
    id: undefined as number | undefined,
    name: '',
    type: 1,
    provider: 'openai',
    models: '',
    status: 1,
    priority: 100,
    supportsThinking: 0,
    // credential / options 以 JSON 字符串提交，这里用动态列表编辑
  });

  const credentialList = ref<FieldValue[]>([]);
  const optionsList = ref<FieldValue[]>([]);

  const typeOptions = [
    { label: '对话模型', value: 1 },
    { label: '向量模型', value: 2 },
    { label: '重排模型', value: 3 },
    { label: '视觉模型', value: 4 },
  ];

  const rules: FormRules = {
    name: [{ required: true, message: '请输入模型名称', trigger: ['blur', 'input'] }],
    type: [{ required: true, type: 'number', message: '请选择模型类型' }],
    provider: [{ required: true, message: '请选择供应商' }],
  };

  /** 按当前模型类型过滤可选的供应商（重排/视觉不允许 Ollama） */
  const providerOptionsByType = computed(() => getProviderOptionsByType(form.type));

  /** 预设选项字段：字段名只读，不允许编辑 */
  function isPresetOptionField(field: string) {
    return field === 'url' || field === 'temperature' || field === 'maxOutputTokens';
  }

  /** 预设凭证字段：字段名只读，不允许编辑 */
  function isPresetCredentialField(field: string) {
    return field === 'apiKey';
  }

  /** 数值型字段：用数字输入框 */
  function isNumberField(field: string) {
    return field === 'temperature' || field === 'maxOutputTokens';
  }

  /** 预设字段的展示名（中文）；底层 field 值仍为英文，用于序列化/传给后端 */
  const FIELD_LABELS: Record<string, string> = {
    apiKey: 'API 密钥',
    url: '接口地址',
    temperature: '温度',
    maxOutputTokens: '最大输出 Tokens',
  };
  function fieldLabel(field: string): string {
    return FIELD_LABELS[field] || field;
  }

  /** 默认凭证字段（按 provider / type） */
  function defaultCredential(provider: string): FieldValue[] {
    if (provider === 'ollama') {
      // ollama 通常不需要 key，仅 baseUrl
      return [];
    }
    return [{ field: 'apiKey', value: '' }];
  }

  /** 默认选项字段 */
  function defaultOptions(type: number, provider: string): FieldValue[] {
    const list: FieldValue[] = [];
    list.push({
      field: 'url',
      value: provider === 'ollama' ? 'http://localhost:11434/v1' : 'https://api.openai.com/v1',
    });
    if (type === 1) {
      list.push({ field: 'temperature', value: 0.1, range: [0, 2] });
      list.push({ field: 'maxOutputTokens', value: 2048, range: [1, 128000] });
    }
    return list;
  }

  /**
   * 测试连接前置条件：
   *  - 必须填「地址」(options.url)
   *  - 非 ollama 必须填「凭证 apiKey」（ollama 默认无需 key）
   * 不满足时禁用「测试连接」按钮，避免无意义请求。
   */
  const canTest = computed(() => testDisabledReason.value === '');

  const testDisabledReason = computed(() => {
    const url = getField(optionsList.value, 'url');
    if (!url || !String(url).trim()) {
      return '请先填写「地址」';
    }
    if (form.provider !== 'ollama') {
      const apiKey = getField(credentialList.value, 'apiKey');
      if (!apiKey || !String(apiKey).trim()) {
        return '请先填写「apiKey」';
      }
    }
    return '';
  });

  function onTypeChange() {
    // 切换类型后，若当前 provider 不被新类型支持（如重排/视觉下选过 ollama），
    // 回退到 openai 并重建凭证/默认 URL，避免选了不兼容组合导致连通性测试必然 404。
    if (!isProviderSupportedByType(form.provider, form.type)) {
      form.provider = 'openai';
      onProviderChange(form.provider);
    }
  }

  /**
   * 切换接入方式：按 provider 重建凭证字段并同步默认 URL。
   * - 凭证：openai 有 apiKey，ollama 无需凭证（清空）
   * - URL：仅当当前 URL 为空或仍是「上一个 provider 的默认值」时，才覆盖为新 provider 默认值，
   *   避免覆盖用户自定义地址
   * 注意：上一版移除了字段的新增/删除按钮，字段列表完全由此处 + defaultOptions 决定，
   * 所以切换 provider 必须主动重建，否则 ollama 仍会残留 openai 的 apiKey 字段。
   */
  const DEFAULT_URLS: Record<string, string> = {
    openai: 'https://api.openai.com/v1',
    ollama: 'http://localhost:11434/v1',
  };

  function onProviderChange(next: string) {
    // 重建凭证字段（保留用户已填的 apiKey 值仅对 openai 有意义，ollama 直接清空）
    credentialList.value = defaultCredential(next);

    // 同步默认 URL：仅在「空」或「任一 provider 默认值」时覆盖，自定义 URL 保留
    const curUrl = getField(optionsList.value, 'url');
    const isDefault =
      !curUrl ||
      !String(curUrl).trim() ||
      Object.values(DEFAULT_URLS).includes(String(curUrl).trim());
    if (isDefault) {
      const idx = optionsList.value.findIndex((o) => o.field === 'url');
      const nextUrl = DEFAULT_URLS[next] || DEFAULT_URLS.openai;
      if (idx >= 0) {
        optionsList.value[idx].value = nextUrl;
      } else {
        // 理论不会走到，url 始终在默认 options 里；兜底插入到首位
        optionsList.value.unshift({ field: 'url', value: nextUrl });
      }
    }
  }

  function openCreate(defaultType = 1) {
    // 重置操作态：组件常驻（不会随抽屉关闭销毁），上次测试/保存若卡在 loading 会遗留，
    // 导致本次打开「测试连接」「保存」按钮一直灰色不可点击
    testing.value = false;
    saving.value = false;
    Object.assign(form, {
      id: undefined,
      name: '',
      type: defaultType,
      provider: 'openai',
      models: '',
      status: 1,
      priority: 100,
      supportsThinking: 0,
    });
    credentialList.value = defaultCredential('openai');
    optionsList.value = defaultOptions(defaultType, 'openai');
    show.value = true;
  }

  function openEdit(model: AiModel) {
    // 同 openCreate：每次打开强制重置操作态，避免上一次遗留的 loading 卡住按钮
    testing.value = false;
    saving.value = false;
    Object.assign(form, {
      id: model.id,
      name: model.name || '',
      type: model.type,
      provider: model.provider || 'openai',
      models: model.models || '',
      status: model.status ?? 1,
      priority: model.priority ?? 100,
      supportsThinking: model.supportsThinking ?? 0,
    });
    credentialList.value = parseFieldJson(model.credential);
    optionsList.value = parseFieldJson(model.options);
    if (credentialList.value.length === 0) {
      credentialList.value = defaultCredential(form.provider);
    }
    if (optionsList.value.length === 0) {
      optionsList.value = defaultOptions(form.type, form.provider);
    }
    show.value = true;
  }

  /**
   * 收集当前表单为提交/测试用 payload。
   * <p>保存与测试共用同一份构造逻辑，确保「测试通过的配置」与「保存下去的配置」严格一致，
   * 避免两处各拼一份导致字段漏改、测试结果与实际生效配置不符。
   *
   * @param forTest 测试场景：name 允许兜底（后端 name 为必填，未填名称时不应阻塞连通性测试）
   */
  function buildPayload(forTest = false): Partial<AiModel> {
    return {
      id: form.id,
      name: form.name.trim() || (forTest ? 'test' : ''),
      type: form.type,
      provider: form.provider,
      models: form.models || undefined,
      status: form.status,
      priority: form.priority,
      supportsThinking: form.type === 1 ? form.supportsThinking : 0,
      // 序列化回 JSON 字符串（过滤掉 field 为空的项）
      credential: stringifyFieldJson(credentialList.value.filter((c) => c.field && c.field.trim())),
      options: stringifyFieldJson(optionsList.value.filter((o) => o.field && o.field.trim())),
    };
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    if (!form.name.trim()) {
      message.warning('请输入模型名称');
      return;
    }
    saving.value = true;
    try {
      const payload = buildPayload();
      const res: any = form.id ? await editModel(payload) : await addModel(payload);
      if (res && res.code === 0) {
        message.success('已保存');
        show.value = false;
        emit('saved');
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e) {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  async function handleTest() {
    testing.value = true;
    try {
      // 新建/编辑一律按「当前表单参数」测试，不走按 id 查库的 /ai/model/test。
      // 原因：编辑态改了 url / apiKey / 模型名但尚未保存时，按 id 测的是库里的旧配置，
      // 会出现「表单已填对却报 401 / 404」的假失败，或「表单填错却测通」的假成功——
      // 两者都会误导用户。所见即所测，测试结果必须对应屏幕上的配置。
      const res: any = await testModelConnect(buildPayload(true));
      if (res && res.code === 0 && res.data) {
        const r = res.data;
        if (r.success) {
          // 编辑态额外点明「测的是当前表单」，提醒改动仍需保存才会真正生效
          const tip = form.id ? '，当前表单配置有效，保存后生效' : '';
          message.success(`${r.message || '连接成功'}（${r.latencyMs ?? 0} ms）${tip}`);
        } else {
          message.error(`连接失败：${r.message || '未知错误'}（${r.latencyMs ?? 0} ms）`);
        }
      } else {
        message.error(res?.message || '测试失败');
      }
    } catch (e) {
      message.error('测试失败');
    } finally {
      testing.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>

<style lang="less" scoped>
  .kv-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .preset-field-name {
    display: inline-flex;
    align-items: center;
    width: 160px;
    flex-shrink: 0;
    font-size: 14px;
    color: #333;
  }
</style>
