<template>
  <n-drawer v-model:show="show" :width="720" placement="right" :mask-closable="false">
    <n-drawer-content
      :title="form.id ? '编辑配置' : '新增配置'"
      closable
      :native-scrollbar="false"
    >
    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="left"
      label-width="100px"
      autocomplete="off"
    >
      <!-- 假输入框：吸收浏览器对密码/账号的自动回填（apiKey/password 字段会触发密码管理器） -->
      <input type="text" name="fake-user" style="display: none" autocomplete="off" />
      <input type="password" name="fake-pass" style="display: none" autocomplete="new-password" />

      <n-form-item label="配置名称" path="name">
        <n-input
          v-model:value="form.name"
          placeholder="如 自建MinerU、云端MinerU"
          :maxlength="100"
          autocomplete="off"
        />
      </n-form-item>
      <n-form-item label="服务类别" path="category">
        <n-select
          v-model:value="form.category"
          :options="categoryOptions"
          :disabled="!!form.id"
          @update:value="onCategoryChange"
        />
      </n-form-item>

      <template v-if="form.category === 'mineru_self'">
        <n-divider title-placement="left" style="margin-top: 8px">自建 MinerU 配置</n-divider>
        <n-form-item label="服务地址" path="cfg.endpoint">
          <n-input
            v-model:value="cfg.endpoint"
            placeholder="http://127.0.0.1:8600"
            autocomplete="off"
          />
        </n-form-item>
        <n-form-item label="后端模型">
          <n-select
            v-model:value="cfg.model"
            :options="MINERU_SELF_MODEL_OPTIONS"
          />
        </n-form-item>
        <n-form-item v-if="needVlmServerUrl(cfg.model)" label="VLM 地址">
          <n-input
            v-model:value="cfg.vlmServerUrl"
            placeholder="仅 vlm-http-client / hybrid-http-client 后端需要"
            autocomplete="off"
          />
        </n-form-item>
        <n-form-item label="识别公式">
          <n-switch v-model:value="cfg.enableFormula" />
        </n-form-item>
        <n-form-item label="识别表格">
          <n-switch v-model:value="cfg.enableTable" />
        </n-form-item>
        <n-form-item label="OCR">
          <n-switch v-model:value="cfg.enableOcr">
            <template #checked>开启（parse_method=ocr）</template>
            <template #unchecked>关闭（parse_method=txt）</template>
          </n-switch>
        </n-form-item>
        <n-form-item label="OCR 语言">
          <n-select v-model:value="cfg.language" :options="MINERU_LANGUAGE_OPTIONS" />
        </n-form-item>
        <n-form-item label="超时秒数">
          <n-input-number v-model:value="cfg.timeoutSec" :min="10" :step="60" style="width: 200px" />
          <span style="margin-left: 8px; color: #aaa; font-size: 12px">同步解析读超时</span>
        </n-form-item>
      </template>

      <template v-else-if="form.category === 'mineru_cloud'">
        <n-divider title-placement="left" style="margin-top: 8px">云端 MinerU 配置</n-divider>
        <n-form-item label="API Key" path="cfg.apiKey">
          <n-input
            v-model:value="cfg.apiKey"
            type="password"
            show-password-on="click"
            placeholder="mineru.net 令牌（apiManage/token 获取）"
            autocomplete="new-password"
          />
        </n-form-item>
        <n-form-item label="模型版本">
          <n-select
            v-model:value="cfg.model"
            :options="MINERU_CLOUD_MODEL_OPTIONS"
          />
        </n-form-item>
        <n-form-item label="识别公式">
          <n-switch v-model:value="cfg.enableFormula" />
        </n-form-item>
        <n-form-item label="识别表格">
          <n-switch v-model:value="cfg.enableTable" />
        </n-form-item>
        <n-form-item label="OCR">
          <n-switch v-model:value="cfg.enableOcr">
            <template #checked>开启</template>
            <template #unchecked>关闭</template>
          </n-switch>
        </n-form-item>
        <n-form-item label="OCR 语言">
          <n-select v-model:value="cfg.language" :options="MINERU_LANGUAGE_OPTIONS" />
        </n-form-item>
        <n-form-item label="轮询间隔">
          <n-input-number v-model:value="cfg.pollIntervalSec" :min="1" :step="1" style="width: 200px" />
          <span style="margin-left: 8px; color: #aaa; font-size: 12px">秒</span>
        </n-form-item>
        <n-form-item label="超时秒数">
          <n-input-number v-model:value="cfg.timeoutSec" :min="30" :step="30" style="width: 200px" />
          <span style="margin-left: 8px; color: #aaa; font-size: 12px">单任务总超时</span>
        </n-form-item>
      </template>

      <template v-else-if="form.category === 'neo4j_self'">
        <n-divider title-placement="left" style="margin-top: 8px">Neo4j 配置</n-divider>
        <n-form-item label="连接地址" path="cfg.uri">
          <n-input
            v-model:value="cfg.uri"
            placeholder="bolt://127.0.0.1:7687"
            autocomplete="off"
          />
        </n-form-item>
        <n-form-item label="用户名">
          <n-input
            v-model:value="cfg.username"
            placeholder="neo4j"
            autocomplete="off"
          />
        </n-form-item>
        <n-form-item label="密码">
          <n-input
            v-model:value="cfg.password"
            type="password"
            show-password-on="click"
            placeholder="数据库密码"
            autocomplete="new-password"
          />
        </n-form-item>
      </template>

      <n-divider title-placement="left">其他</n-divider>
      <n-form-item label="备注">
        <n-input v-model:value="form.remark" type="textarea" :rows="2" :maxlength="255" />
      </n-form-item>
      <n-form-item label="状态">
        <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="2">
          <template #checked>启用</template>
          <template #unchecked>禁用</template>
        </n-switch>
      </n-form-item>
      <n-form-item label="排序">
        <n-input-number v-model:value="form.sort" :min="0" style="width: 200px" />
        <span style="margin-left: 8px; color: #aaa; font-size: 12px">数值小者靠前（同类别多条时）</span>
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
  import { ApiOutlined } from '@vicons/antd';
  import {
    addService,
    editService,
    testService,
    testServiceConnect,
    SERVICE_CATEGORY_TABS,
    MINERU_SELF_MODEL_OPTIONS,
    MINERU_CLOUD_MODEL_OPTIONS,
    MINERU_LANGUAGE_OPTIONS,
    needVlmServerUrl,
    type ExtServiceConfig,
  } from '@/api/system/aiService';

  const emit = defineEmits<{
    (e: 'saved'): void;
  }>();

  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);
  const testing = ref(false);
  const formRef = ref<FormInst | null>(null);

  /** 类别下拉选项（与 tab 一致） */
  const categoryOptions = SERVICE_CATEGORY_TABS.map((t) => ({ label: t.label, value: t.name }));

  const form = reactive({
    id: undefined as number | undefined,
    name: '',
    category: 'mineru_self' as string,
    remark: '',
    status: 1,
    sort: 100,
  });

  /** config 动态字段（用 reactive 对象承载所有 category 的字段，按 category 切换时重建） */
  const cfg = reactive<any>({});

  const rules: FormRules = {
    name: [{ required: true, message: '请输入配置名称', trigger: ['blur', 'input'] }],
    category: [{ required: true, message: '请选择服务类别' }],
  };

  /** 各类别默认 config */
  function defaultConfig(category: string): any {
    if (category === 'mineru_self') {
      return {
        endpoint: 'http://127.0.0.1:8600',
        model: 'pipeline',
        vlmServerUrl: '',
        enableFormula: true,
        enableTable: true,
        enableOcr: true,
        language: 'ch',
        timeoutSec: 1000,
      };
    }
    if (category === 'mineru_cloud') {
      return {
        apiKey: '',
        model: 'pipeline',
        enableFormula: true,
        enableTable: true,
        enableOcr: true,
        language: 'ch',
        pollIntervalSec: 3,
        timeoutSec: 600,
      };
    }
    if (category === 'neo4j_self') {
      return {
        uri: 'bolt://127.0.0.1:7687',
        username: 'neo4j',
        password: '',
      };
    }
    return {};
  }

  /** 切换类别：重建 config 默认值（保留用户已填的同名字段值意义不大，直接重建更清晰） */
  function onCategoryChange(next: string) {
    resetConfig(defaultConfig(next));
  }

  /** 把 cfg 对象整体替换为 defaults（清掉旧字段，避免残留） */
  function resetConfig(defaults: any) {
    Object.keys(cfg).forEach((k) => delete cfg[k]);
    Object.assign(cfg, defaults);
  }

  /**
   * 测试连接前置条件：
   *  - mineru_self：必填 endpoint
   *  - mineru_cloud：必填 apiKey
   *  - neo4j_self：必填 uri
   * 不满足时禁用「测试连接」按钮。
   */
  const testDisabledReason = computed(() => {
    if (form.category === 'mineru_self') {
      if (!cfg.endpoint || !String(cfg.endpoint).trim()) return '请先填写「服务地址」';
    } else if (form.category === 'mineru_cloud') {
      if (!cfg.apiKey || !String(cfg.apiKey).trim()) return '请先填写「API Key」';
    } else if (form.category === 'neo4j_self') {
      if (!cfg.uri || !String(cfg.uri).trim()) return '请先填写「连接地址」';
    } else {
      return '暂不支持该类别的测试';
    }
    return '';
  });

  const canTest = computed(() => testDisabledReason.value === '');

  function openCreate(defaultCategory = 'mineru_self') {
    // 重置操作态：组件常驻（不会随抽屉关闭销毁），上次测试/保存若卡 loading 会遗留
    testing.value = false;
    saving.value = false;
    Object.assign(form, {
      id: undefined,
      name: '',
      category: defaultCategory,
      remark: '',
      status: 1,
      sort: 100,
    });
    resetConfig(defaultConfig(defaultCategory));
    show.value = true;
  }

  function openEdit(config: ExtServiceConfig) {
    testing.value = false;
    saving.value = false;
    Object.assign(form, {
      id: config.id,
      name: config.name || '',
      category: config.category,
      remark: config.remark || '',
      status: config.status ?? 1,
      sort: config.sort ?? 100,
    });
    // 反序列化 config JSON 回填；解析失败用默认值兜底
    let parsed: any = {};
    try {
      parsed = config.config ? JSON.parse(config.config) : {};
    } catch (e) {
      parsed = {};
    }
    const merged = { ...defaultConfig(config.category), ...parsed };
    resetConfig(merged);
    show.value = true;
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    if (!form.name.trim()) {
      message.warning('请输入配置名称');
      return;
    }
    saving.value = true;
    try {
      const payload: Partial<ExtServiceConfig> = {
        id: form.id,
        name: form.name.trim(),
        category: form.category,
        remark: form.remark || undefined,
        status: form.status,
        sort: form.sort,
        // config 序列化为 JSON 文本提交
        config: JSON.stringify(cleanConfig(form.category, cfg)),
      };
      const res: any = form.id ? await editService(payload) : await addService(payload);
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
      // 已保存（编辑态）按 id 测试；未保存（新建态）按当前表单参数直接测试，无需先保存
      const res: any = form.id
        ? await testService(form.id)
        : await testServiceConnect({
            name: form.name.trim() || 'test',
            category: form.category,
            config: JSON.stringify(cleanConfig(form.category, cfg)),
          });
      if (res && res.code === 0 && res.data) {
        const r = res.data;
        if (r.success) {
          message.success(`${r.message || '连接成功'}（${r.latencyMs ?? 0} ms）`);
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

  /** 仅保留该 category 相关字段，丢弃切换类别时可能残留的其他字段 */
  function cleanConfig(category: string, src: any): any {
    const out: any = {};
    let allowed: string[];
    if (category === 'mineru_self') {
      allowed = ['endpoint', 'model', 'vlmServerUrl', 'enableFormula', 'enableTable', 'enableOcr', 'language', 'timeoutSec'];
    } else if (category === 'mineru_cloud') {
      allowed = ['apiKey', 'model', 'enableFormula', 'enableTable', 'enableOcr', 'language', 'pollIntervalSec', 'timeoutSec'];
    } else if (category === 'neo4j_self') {
      allowed = ['uri', 'username', 'password'];
    } else {
      allowed = Object.keys(src);
    }
    allowed.forEach((k) => {
      if (src[k] !== undefined) out[k] = src[k];
    });
    return out;
  }

  defineExpose({ openCreate, openEdit });
</script>
