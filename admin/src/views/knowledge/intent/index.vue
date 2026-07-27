<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="意图路由">
        意图路由节点（KB 知识库检索 / SYSTEM 系统交互 / MCP
        工具调用），命中后精准路由到对应知识库或工具。「评估测试」可批量验证意图分类效果。
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <n-tabs type="segment" animated>
        <!-- 意图列表 -->
        <n-tab-pane name="list" :tab="`意图列表 (${flatList.length})`">
          <div class="intent-toolbar">
            <n-space align="center">
              <n-select
                v-model:value="createKind"
                :options="kindOptions"
                style="width: 200px"
                placeholder="选择类型新建"
              />
              <n-button type="primary" strong secondary @click="handleCreate">
                <template #icon
                  ><n-icon><PlusOutlined /></n-icon
                ></template>
                新建意图
              </n-button>
              <n-button secondary @click="loadList" :loading="loading">
                <template #icon
                  ><n-icon><ReloadOutlined /></n-icon
                ></template>
                刷新
              </n-button>
            </n-space>
          </div>

          <n-spin :show="loading">
            <!-- 按 kind 分 Tab -->
            <n-tabs type="line" animated size="small">
              <n-tab-pane name="KB" :tab="`知识库检索 (${kbList.length})`">
                <div class="intent-list">
                  <n-empty
                    v-if="!loading && kbList.length === 0"
                    description="暂无意图，点击右上角新建"
                    style="margin-top: 40px"
                  />
                  <div v-for="node in kbList" :key="node.id" class="intent-row">
                    <div class="row-main">
                      <div class="row-title">
                        <span class="row-name">{{ node.name }}</span>
                        <n-tag :type="node.enabled ? 'success' : 'default'" size="small">
                          {{ node.enabled ? '启用' : '禁用' }}
                        </n-tag>
                      </div>
                      <div v-if="node.description" class="row-desc">{{ node.description }}</div>
                      <n-space size="small" wrap>
                        <n-tag v-if="node.collectionName" type="info" size="small" round>
                          知识库: {{ node.collectionName }}
                        </n-tag>
                        <n-tag v-if="node.docIds && node.docIds.length" type="warning" size="small" round>
                          限定 {{ node.docIds.length }} 篇文档
                        </n-tag>
                        <n-tag v-else-if="node.collectionName" size="small" :bordered="false">
                          整库检索
                        </n-tag>
                        <n-tag v-if="node.topK" size="small">TopK: {{ node.topK }}</n-tag>
                        <n-tag
                          v-for="(ex, i) in node.examples || []"
                          :key="i"
                          size="small"
                          :bordered="false"
                        >
                          {{ ex }}
                        </n-tag>
                      </n-space>
                    </div>
                    <div class="row-actions">
                      <n-switch
                        size="small"
                        :value="node.enabled ?? false"
                        @update:value="(v: boolean) => handleToggle(node, v)"
                      />
                      <n-button size="small" secondary @click="openEdit(node)">编辑</n-button>
                      <n-button size="small" type="error" secondary @click="handleDelete(node)"
                        >删除</n-button
                      >
                    </div>
                  </div>
                </div>
              </n-tab-pane>

              <n-tab-pane name="SYSTEM" :tab="`系统交互 (${systemList.length})`">
                <div class="intent-list">
                  <n-empty
                    v-if="!loading && systemList.length === 0"
                    description="暂无意图，点击右上角新建"
                    style="margin-top: 40px"
                  />
                  <div v-for="node in systemList" :key="node.id" class="intent-row">
                    <div class="row-main">
                      <div class="row-title">
                        <span class="row-name">{{ node.name }}</span>
                        <n-tag :type="node.enabled ? 'success' : 'default'" size="small">
                          {{ node.enabled ? '启用' : '禁用' }}
                        </n-tag>
                      </div>
                      <div v-if="node.description" class="row-desc">{{ node.description }}</div>
                      <n-space size="small" wrap>
                        <n-tag
                          v-for="(ex, i) in node.examples || []"
                          :key="i"
                          size="small"
                          :bordered="false"
                        >
                          {{ ex }}
                        </n-tag>
                      </n-space>
                    </div>
                    <div class="row-actions">
                      <n-switch
                        size="small"
                        :value="node.enabled ?? false"
                        @update:value="(v: boolean) => handleToggle(node, v)"
                      />
                      <n-button size="small" secondary @click="openEdit(node)">编辑</n-button>
                      <n-button size="small" type="error" secondary @click="handleDelete(node)"
                        >删除</n-button
                      >
                    </div>
                  </div>
                </div>
              </n-tab-pane>

              <n-tab-pane name="MCP" :tab="`工具调用 (${mcpList.length})`">
                <div class="intent-list">
                  <n-empty
                    v-if="!loading && mcpList.length === 0"
                    description="暂无意图，点击右上角新建"
                    style="margin-top: 40px"
                  />
                  <div v-for="node in mcpList" :key="node.id" class="intent-row">
                    <div class="row-main">
                      <div class="row-title">
                        <span class="row-name">{{ node.name }}</span>
                        <n-tag :type="node.enabled ? 'success' : 'default'" size="small">
                          {{ node.enabled ? '启用' : '禁用' }}
                        </n-tag>
                      </div>
                      <div v-if="node.description" class="row-desc">{{ node.description }}</div>
                      <n-space size="small" wrap>
                        <n-tag v-if="node.mcpToolId" type="warning" size="small" round>
                          工具: {{ node.mcpToolId }}
                        </n-tag>
                        <n-tag
                          v-for="(ex, i) in node.examples || []"
                          :key="i"
                          size="small"
                          :bordered="false"
                        >
                          {{ ex }}
                        </n-tag>
                      </n-space>
                    </div>
                    <div class="row-actions">
                      <n-switch
                        size="small"
                        :value="node.enabled ?? false"
                        @update:value="(v: boolean) => handleToggle(node, v)"
                      />
                      <n-button size="small" secondary @click="openEdit(node)">编辑</n-button>
                      <n-button size="small" type="error" secondary @click="handleDelete(node)"
                        >删除</n-button
                      >
                    </div>
                  </div>
                </div>
              </n-tab-pane>
            </n-tabs>
          </n-spin>
        </n-tab-pane>

        <!-- 评估测试 -->
        <n-tab-pane name="eval" tab="评估测试">
          <EvalPanel :intent-tree="nodeList" />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <IntentNodeModal ref="modalRef" @saved="loadList" />
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { ReloadOutlined, PlusOutlined } from '@vicons/antd';
  import {
    getIntentTree,
    editIntentNode,
    delIntentNode,
    type IntentNodeTree,
  } from '@/api/system/knowledge';
  import IntentNodeModal from './IntentNodeModal.vue';
  import EvalPanel from './EvalPanel.vue';

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const nodeList = ref<IntentNodeTree[]>([]);
  const createKind = ref<string>('KB');
  const modalRef = ref<InstanceType<typeof IntentNodeModal> | null>(null);

  const kindOptions = [
    { label: '知识库检索 KB', value: 'KB' },
    { label: '系统交互 SYSTEM', value: 'SYSTEM' },
    { label: '工具调用 MCP', value: 'MCP' },
  ];

  // 后端返回的是树（含虚拟根），拍平成节点列表
  const flatList = computed<IntentNodeTree[]>(() => {
    const out: IntentNodeTree[] = [];
    const walk = (nodes: IntentNodeTree[]) => {
      for (const n of nodes) {
        // 跳过虚拟根
        if (n.id === '__virtual_root__') {
          if (n.children) walk(n.children);
          continue;
        }
        out.push(n);
        if (n.children) walk(n.children);
      }
    };
    walk(nodeList.value);
    return out;
  });

  const kbList = computed(() => flatList.value.filter((n) => n.kind === 'KB'));
  const systemList = computed(() => flatList.value.filter((n) => n.kind === 'SYSTEM'));
  const mcpList = computed(() => flatList.value.filter((n) => n.kind === 'MCP'));

  async function loadList() {
    loading.value = true;
    try {
      const res = await getIntentTree();
      if (res?.code === 0 && Array.isArray(res.data)) {
        nodeList.value = res.data.filter((n: any) => n && n.id);
      } else {
        nodeList.value = [];
      }
    } catch (e) {
      message.error('加载意图列表失败');
      nodeList.value = [];
    } finally {
      loading.value = false;
    }
  }

  function handleCreate() {
    modalRef.value?.openCreate(createKind.value);
  }

  function openEdit(node: IntentNodeTree) {
    modalRef.value?.openEdit(node);
  }

  function handleDelete(node: IntentNodeTree) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除意图「${node.name}」？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await delIntentNode(node.id);
        if (res?.code === 0) {
          message.success('已删除');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  async function handleToggle(node: IntentNodeTree, enabled: boolean) {
    const res = await editIntentNode({
      id: node.id,
      kind: node.kind,
      name: node.name,
      description: node.description,
      examples: node.examples,
      collectionName: node.collectionName,
      docIds: node.docIds,
      mcpToolId: node.mcpToolId,
      promptTemplate: node.promptTemplate,
      paramPromptTemplate: node.paramPromptTemplate,
      topK: node.topK,
      enabled,
      kbId: node.collectionName,
    });
    if (res?.code === 0) {
      message.success(enabled ? '已启用' : '已禁用');
      await loadList();
    } else {
      message.error(res?.message || '操作失败');
    }
  }

  onMounted(() => {
    loadList();
  });
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .intent-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }
  .intent-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .intent-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    gap: 12px;
    transition: box-shadow 0.2s;
    &:hover {
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    }
  }
  .row-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .row-title {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .row-name {
    font-size: 15px;
    font-weight: 600;
  }
  .row-desc {
    font-size: 13px;
    color: #666;
    word-break: break-all;
  }
  .row-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
</style>
