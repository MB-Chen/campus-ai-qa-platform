<template>
  <n-drawer v-model:show="show" :width="660" placement="right" :mask-closable="false">
    <n-drawer-content
      :title="mode === 'create' ? '新建意图节点' : '编辑意图节点'"
      closable
      :native-scrollbar="false"
    >
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120px">
        <n-form-item label="名称" path="name">
          <n-input v-model:value="form.name" placeholder="如：售后咨询" />
        </n-form-item>

        <n-form-item label="类型" path="kind">
          <n-radio-group v-model:value="form.kind">
            <n-radio value="KB">知识库 KB</n-radio>
            <n-radio value="SYSTEM">系统交互 SYSTEM</n-radio>
            <n-radio value="MCP">工具调用 MCP</n-radio>
          </n-radio-group>
        </n-form-item>

        <!-- KB 类型字段 -->
        <template v-if="form.kind === 'KB'">
          <n-form-item label="关联知识库" path="kbId">
            <n-select
              v-model:value="form.kbId"
              :options="kbOptions"
              clearable
              filterable
              :loading="kbLoading"
              placeholder="选择要检索的知识库"
              @update:value="onKbChange"
            />
          </n-form-item>
          <n-form-item label="限定文档">
            <n-select
              v-model:value="form.docIds"
              :options="docOptions"
              multiple
              clearable
              filterable
              :loading="docLoading"
              :disabled="!form.kbId"
              :placeholder="form.kbId ? '不选则检索整库' : '请先选择知识库'"
            />
          </n-form-item>
          <n-form-item label="Collection">
            <n-input v-model:value="form.collectionName" placeholder="留空则自动用知识库ID" />
          </n-form-item>
          <n-form-item label="TopK">
            <n-input-number v-model:value="form.topK" :min="1" :max="50" placeholder="留空用默认" style="width: 100%" />
          </n-form-item>
        </template>

        <!-- MCP 类型字段 -->
        <template v-if="form.kind === 'MCP'">
          <n-form-item label="MCP工具" path="mcpToolId">
            <n-select
              v-model:value="form.mcpToolId"
              :options="mcpToolOptions"
              :loading="mcpToolLoading"
              filterable
              clearable
              placeholder="选择已启用的 MCP 服务工具"
            />
          </n-form-item>
          <n-form-item label="参数提取模板">
            <n-input v-model:value="form.paramPromptTemplate" type="textarea" :rows="2" placeholder="MCP 参数提取提示词（可选）" />
          </n-form-item>
          <n-form-item label=" ">
            <n-text depth="3" style="font-size: 12px">
              工具来自「知识库 → MCP服务」中已启用服务的工具快照。如列表为空，请先到 MCP服务 页配置并刷新工具。
            </n-text>
          </n-form-item>
        </template>

        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" :rows="2" placeholder="语义说明，喂给向量/LLM" />
        </n-form-item>

        <n-form-item label="典型问法">
          <n-input
            v-model:value="examplesText"
            type="textarea"
            :rows="3"
            placeholder="每行一个问法，如：&#10;请假怎么请&#10;报销流程是什么"
          />
        </n-form-item>

        <n-form-item label="回答模板覆盖">
          <n-input v-model:value="form.promptTemplate" type="textarea" :rows="2" placeholder="意图级 Prompt 片段（可选）" />
        </n-form-item>

        <n-form-item label="启用">
          <n-switch v-model:value="form.enabled" />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="show = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="handleSubmit">确定</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, reactive, watch } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import { addIntentNode, editIntentNode, getKbList, getDocumentList, type IntentNodeSave, type IntentNodeTree } from '@/api/system/knowledge';
  import { getEnabledMcpTools } from '@/api/system/aiMcp';

  interface KnowledgeBaseOption {
    id: string;
    name: string;
  }

  const emit = defineEmits<{ saved: [] }>();

  const message = useMessage();
  const show = ref(false);
  const submitting = ref(false);
  const mode = ref<'create' | 'edit'>('create');
  const formRef = ref<FormInst | null>(null);
  const examplesText = ref('');

  const kbOptions = ref<{ label: string; value: string }[]>([]);
  const kbLoading = ref(false);
  const docOptions = ref<{ label: string; value: string }[]>([]);
  const docLoading = ref(false);

  // MCP 工具下拉（从已启用 MCP 服务的工具快照加载）
  const mcpToolOptions = ref<{ label: string; value: string }[]>([]);
  const mcpToolLoading = ref(false);

  async function loadMcpTools() {
    mcpToolLoading.value = true;
    try {
      const res: any = await getEnabledMcpTools();
      if (res && res.code === 0 && Array.isArray(res.data)) {
        mcpToolOptions.value = res.data
          .filter((t: any) => t && t.fullId)
          .map((t: any) => ({
            label: t.toolName ? `${t.toolName}${t.description ? ' - ' + t.description : ''}` : t.fullId,
            value: t.fullId,
          }));
      } else {
        mcpToolOptions.value = [];
      }
    } catch {
      mcpToolOptions.value = [];
    } finally {
      mcpToolLoading.value = false;
    }
  }

  /** 加载知识库列表（修复下拉为空：组件主动加载，不再依赖父组件传参） */
  async function loadKbList() {
    kbLoading.value = true;
    try {
      const res: any = await getKbList({ page: 1, size: 200 });
      const list = res?.code === 0 && res.data ? (res.data.data || res.data || []) : [];
      kbOptions.value = (Array.isArray(list) ? list : [])
        .filter((k: any) => k && k.id)
        .map((k: any) => ({ label: k.name || k.id, value: k.id }));
    } catch {
      kbOptions.value = [];
    } finally {
      kbLoading.value = false;
    }
  }

  /** 按知识库加载文档列表（用于文档多选联动） */
  async function loadDocList(kbId: string) {
    if (!kbId) {
      docOptions.value = [];
      return;
    }
    docLoading.value = true;
    try {
      const res: any = await getDocumentList({ kbId, page: 1, size: 500 });
      const list = res?.code === 0 && res.data ? (res.data.data || res.data || []) : [];
      docOptions.value = (Array.isArray(list) ? list : [])
        .filter((d: any) => d && d.id)
        .map((d: any) => ({ label: d.fileName || d.name || d.id, value: d.id }));
    } catch {
      docOptions.value = [];
    } finally {
      docLoading.value = false;
    }
  }

  /** 切换知识库时清空文档选择并重新加载文档列表 */
  function onKbChange(kbId: string | null) {
    form.docIds = [];
    if (kbId) {
      loadDocList(kbId);
    } else {
      docOptions.value = [];
    }
  }

  const form = reactive<IntentNodeSave>({
    id: undefined,
    parentId: undefined,
    level: 0,
    kind: 'KB',
    name: '',
    description: '',
    examples: [],
    collectionName: '',
    docIds: [],
    mcpToolId: '',
    promptTemplate: '',
    paramPromptTemplate: '',
    topK: undefined,
    enabled: true,
    kbId: '',
  });

  // 切换到 MCP 类型时懒加载工具列表（首次进入 MCP 块）
  watch(
    () => form.kind,
    (k) => {
      if (k === 'MCP' && mcpToolOptions.value.length === 0) {
        loadMcpTools();
      }
    }
  );

  const rules: FormRules = {
    name: { required: true, message: '请输入名称', trigger: 'blur' },
    kind: { required: true, message: '请选择类型', trigger: 'change' },
    mcpToolId: {
      required: true,
      trigger: 'blur',
      validator: (_r, v) => {
        if (form.kind === 'MCP' && !v) return new Error('MCP 类型必须填写工具ID');
        return true;
      },
    },
  };

  /** 打开新建 */
  function openCreate(kind?: string) {
    mode.value = 'create';
    docOptions.value = [];
    // KB 类型时主动加载知识库列表（修复下拉为空）
    if ((kind || 'KB') === 'KB') {
      loadKbList();
    }
    // MCP 类型时预加载工具列表
    if (kind === 'MCP') {
      loadMcpTools();
    }

    Object.assign(form, {
      id: undefined,
      parentId: undefined,
      level: 0,
      kind: kind || 'KB',
      name: '',
      description: '',
      examples: [],
      collectionName: '',
      docIds: [],
      mcpToolId: '',
      promptTemplate: '',
      paramPromptTemplate: '',
      topK: undefined,
      enabled: true,
      kbId: '',
    });
    examplesText.value = '';
    show.value = true;
  }

  /** 打开编辑 */
  function openEdit(node: IntentNodeTree) {
    mode.value = 'edit';
    // KB 类型：加载知识库列表 + 回填，并加载该库文档列表（用于文档多选回显）
    const kbId = node.collectionName || '';
    if (node.kind === 'KB') {
      loadKbList();
      if (kbId) {
        loadDocList(kbId);
      } else {
        docOptions.value = [];
      }
    } else {
      docOptions.value = [];
    }
    // MCP 类型时预加载工具列表（编辑态需确保当前值在选项里）
    if (node.kind === 'MCP') {
      loadMcpTools();
    }

    Object.assign(form, {
      id: node.id,
      parentId: undefined,
      level: 0,
      kind: node.kind,
      name: node.name,
      description: node.description || '',
      examples: [],
      collectionName: node.collectionName || '',
      docIds: node.docIds ? [...node.docIds] : [],
      mcpToolId: node.mcpToolId || '',
      promptTemplate: node.promptTemplate || '',
      paramPromptTemplate: node.paramPromptTemplate || '',
      topK: node.topK,
      enabled: node.enabled ?? true,
      kbId,
    });
    examplesText.value = (node.examples || []).join('\n');
    show.value = true;
  }

  async function handleSubmit() {
    try {
      await formRef.value?.validate();
    } catch {
      return;
    }
    submitting.value = true;
    try {
      // 组装 examples
      form.examples = examplesText.value
        .split('\n')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);

      const res = mode.value === 'create' ? await addIntentNode(form) : await editIntentNode(form);
      if (res?.code === 0) {
        message.success(mode.value === 'create' ? '已创建' : '已保存');
        show.value = false;
        emit('saved');
      } else {
        message.error(res?.message || '操作失败');
      }
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    } finally {
      submitting.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>
