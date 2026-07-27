<template>
  <n-drawer v-model:show="show" :width="640" placement="right" :mask-closable="false">
    <n-drawer-content :title="mode === 'create' ? '新增 MCP 服务' : '编辑 MCP 服务'" closable>
      <!-- 反自动填充：吸收浏览器密码自动填充 -->
      <input type="text" style="display: none" />
      <input type="password" style="display: none" />

      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100px">
        <!-- 基本信息 -->
        <n-divider title-placement="left" style="margin-top: 0">基本信息</n-divider>

        <n-form-item label="服务名称" path="name">
          <n-input v-model:value="form.name" placeholder="如：天气查询服务" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input
            v-model:value="form.description"
            type="textarea"
            :rows="2"
            placeholder="服务用途说明（可选）"
          />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="form.enabled" />
        </n-form-item>

        <!-- 连接配置 -->
        <n-divider title-placement="left">连接配置</n-divider>

        <n-form-item label="传输类型">
          <n-tag size="small" round type="info">HTTP Streamable（MCP 标准传输）</n-tag>
        </n-form-item>
        <n-form-item label="服务地址" path="url">
          <n-input
            v-model:value="form.url"
            placeholder="MCP Server 端点地址，如 http://localhost:3001/mcp"
          />
        </n-form-item>

        <!-- 认证配置 -->
        <n-divider title-placement="left">认证配置</n-divider>

        <n-form-item label="认证类型" path="authType">
          <n-radio-group v-model:value="form.authType" @update:value="onAuthTypeChange">
            <n-radio value="none">无认证</n-radio>
            <n-radio value="api_key">API Key</n-radio>
            <n-radio value="bearer">Bearer Token</n-radio>
          </n-radio-group>
        </n-form-item>

        <!-- api_key 类型字段 -->
        <template v-if="form.authType === 'api_key'">
          <n-form-item label="请求头名称">
            <n-input v-model:value="authCfg.apiKeyHeader" placeholder="默认 X-API-Key" />
          </n-form-item>
          <n-form-item label="API Key">
            <n-input
              v-model:value="authCfg.apiKey"
              type="password"
              show-password-on="click"
              :placeholder="
                mode === 'edit' && hasApiKey ? '已配置（留空保留原值）' : '请输入 API Key'
              "
            />
          </n-form-item>
        </template>

        <!-- bearer 类型字段 -->
        <template v-if="form.authType === 'bearer'">
          <n-form-item label="Token">
            <n-input
              v-model:value="authCfg.token"
              type="password"
              show-password-on="click"
              :placeholder="
                mode === 'edit' && hasToken ? '已配置（留空保留原值）' : '请输入 Bearer Token'
              "
            />
          </n-form-item>
        </template>

        <!-- 自定义请求头 -->
        <n-divider title-placement="left">自定义请求头（可选）</n-divider>
        <div class="header-list">
          <div v-for="(h, i) in headerRows" :key="i" class="header-row">
            <n-input v-model:value="h.key" placeholder="Header 名" size="small" style="flex: 1" />
            <n-input v-model:value="h.value" placeholder="Header 值" size="small" style="flex: 1" />
            <n-button size="small" quaternary type="error" @click="headerRows.splice(i, 1)"
              >✕</n-button
            >
          </div>
          <n-button size="small" dashed block @click="headerRows.push({ key: '', value: '' })"
            >+ 添加请求头</n-button
          >
        </div>

        <!-- 高级配置 -->
        <n-divider title-placement="left">高级配置</n-divider>
        <n-form-item label="超时(秒)">
          <n-input-number v-model:value="form.timeoutSec" :min="1" :max="300" style="width: 100%" />
        </n-form-item>
        <n-form-item label="重试次数">
          <n-input-number v-model:value="form.retryCount" :min="0" :max="10" style="width: 100%" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" placeholder="备注（可选）" />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number
            v-model:value="form.sort"
            :min="0"
            style="width: 100%"
            placeholder="数值小者靠前"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space>
          <n-button :loading="testing" @click="handleTestConnect"> 测试连接 </n-button>
          <n-button @click="show = false">取消</n-button>
          <n-button type="primary" secondary :loading="saving" @click="handleSave">
            {{ mode === 'create' ? '创建' : '保存' }}
          </n-button>
        </n-space>
      </template>

      <!-- 测试连接结果弹窗 -->
      <McpTestResultComp ref="testResultRef" />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, reactive } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import {
    addMcp,
    editMcp,
    testMcpConnect,
    type McpServer,
    type McpServerSave,
    type McpTestResult,
  } from '@/api/system/aiMcp';
  import McpTestResultComp from './McpTestResult.vue';

  const emit = defineEmits<{ saved: [] }>();
  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);
  const testing = ref(false);
  const mode = ref<'create' | 'edit'>('create');
  const formRef = ref<FormInst | null>(null);
  const testResultRef = ref<InstanceType<typeof McpTestResultComp> | null>(null);

  // 编辑态密钥标记（判断是否"已配置"，用于 placeholder 提示）
  const hasApiKey = ref(false);
  const hasToken = ref(false);

  const form = reactive<McpServerSave>({
    id: undefined,
    name: '',
    description: '',
    enabled: true,
    transportType: 'http_streamable',
    url: '',
    authType: 'none',
    authConfig: '',
    headers: '',
    timeoutSec: 30,
    retryCount: 1,
    remark: '',
    sort: 100,
  });

  // 认证字段（独立维护，保存时序列化成 authConfig JSON）
  const authCfg = reactive<{ apiKey: string; apiKeyHeader: string; token: string }>({
    apiKey: '',
    apiKeyHeader: 'X-API-Key',
    token: '',
  });

  // 自定义请求头行
  const headerRows = ref<{ key: string; value: string }[]>([]);

  const rules: FormRules = {
    name: { required: true, message: '请输入服务名称', trigger: 'blur' },
    url: { required: true, message: '请输入服务地址', trigger: 'blur' },
    authType: { required: true, message: '请选择认证类型', trigger: 'change' },
  };

  function onAuthTypeChange() {
    // 切换认证类型时不清空字段（用户可能来回切），保留已填值
  }

  /** 打开新增 */
  function openCreate() {
    mode.value = 'create';
    resetForm();
    show.value = true;
  }

  /** 打开编辑 */
  function openEdit(item: McpServer) {
    mode.value = 'edit';
    hasApiKey.value = !!item.hasApiKey;
    hasToken.value = !!item.hasToken;

    Object.assign(form, {
      id: item.id,
      name: item.name || '',
      description: item.description || '',
      enabled: item.enabled ?? true,
      transportType: (item.transportType as any) || 'http_streamable',
      url: item.url || '',
      authType: (item.authType as any) || 'none',
      authConfig: '',
      headers: '',
      timeoutSec: item.timeoutSec ?? 30,
      retryCount: item.retryCount ?? 1,
      remark: item.remark || '',
      sort: item.sort ?? 100,
    });

    // 认证字段：编辑态不回显密钥（后端脱敏），仅回显 apiKeyHeader
    authCfg.apiKey = '';
    authCfg.apiKeyHeader = item.apiKeyHeader || 'X-API-Key';
    authCfg.token = '';

    // 自定义请求头
    headerRows.value = [];
    if (item.headers) {
      try {
        const obj = JSON.parse(item.headers);
        if (obj && typeof obj === 'object') {
          headerRows.value = Object.entries(obj).map(([k, v]) => ({
            key: k,
            value: String(v ?? ''),
          }));
        }
      } catch {
        headerRows.value = [];
      }
    }

    show.value = true;
  }

  function resetForm() {
    hasApiKey.value = false;
    hasToken.value = false;
    Object.assign(form, {
      id: undefined,
      name: '',
      description: '',
      enabled: true,
      transportType: 'http_streamable',
      url: '',
      authType: 'none',
      authConfig: '',
      headers: '',
      timeoutSec: 30,
      retryCount: 1,
      remark: '',
      sort: 100,
    });
    authCfg.apiKey = '';
    authCfg.apiKeyHeader = 'X-API-Key';
    authCfg.token = '';
    headerRows.value = [];
  }

  /** 构造 authConfig JSON */
  function buildAuthConfig(): string {
    if (form.authType === 'api_key') {
      const obj: any = {};
      // 编辑态：apiKey 为空则不传（后端保留旧值）
      if (authCfg.apiKey) obj.apiKey = authCfg.apiKey;
      if (authCfg.apiKeyHeader) obj.apiKeyHeader = authCfg.apiKeyHeader;
      return JSON.stringify(obj);
    }
    if (form.authType === 'bearer') {
      const obj: any = {};
      if (authCfg.token) obj.token = authCfg.token;
      return JSON.stringify(obj);
    }
    return '';
  }

  /** 构造 headers JSON */
  function buildHeaders(): string {
    const valid = headerRows.value.filter((h) => h.key && h.key.trim());
    if (valid.length === 0) return '';
    const obj: any = {};
    for (const h of valid) {
      obj[h.key.trim()] = h.value || '';
    }
    return JSON.stringify(obj);
  }

  /** 构造提交体 */
  function buildPayload(): McpServerSave {
    return {
      id: form.id,
      name: form.name,
      description: form.description || undefined,
      enabled: form.enabled,
      transportType: form.transportType,
      url: form.url,
      authType: form.authType,
      authConfig: buildAuthConfig() || undefined,
      headers: buildHeaders() || undefined,
      timeoutSec: form.timeoutSec,
      retryCount: form.retryCount,
      remark: form.remark || undefined,
      sort: form.sort,
    };
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch {
      return;
    }
    saving.value = true;
    try {
      const payload = buildPayload();
      const res: any = mode.value === 'create' ? await addMcp(payload) : await editMcp(payload);
      if (res && res.code === 0) {
        message.success(mode.value === 'create' ? '已创建' : '已保存');
        show.value = false;
        emit('saved');
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e: any) {
      message.error(e?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  }

  async function handleTestConnect() {
    // 新建态用表单参数测试；编辑态也允许用表单参数测试（密钥留空时后端用 DB 旧值）
    testing.value = true;
    try {
      const payload = buildPayload();
      const res: any = await testMcpConnect(payload);
      if (res && res.code === 0 && res.data) {
        const r = res.data as McpTestResult;
        if (r.success) {
          message.success(r.message || '连接成功');
        } else {
          message.error(r.message || '连接失败');
        }
        testResultRef.value?.show(r);
      } else {
        message.error(res?.message || '测试失败');
      }
    } catch (e: any) {
      message.error(e?.message || '测试请求失败');
    } finally {
      testing.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>

<style lang="less" scoped>
  .header-list {
    margin-left: 100px;
    margin-bottom: 16px;
  }
  .header-row {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    align-items: center;
  }
</style>
