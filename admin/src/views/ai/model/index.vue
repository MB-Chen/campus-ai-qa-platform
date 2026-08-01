<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="AI 模型">
        管理对话 / 向量 / 重排模型，页面可操作凭证与调用选项，运行时按类型与优先级选取候选模型
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="model-toolbar">
        <n-button type="primary" secondary @click="openCreate">
          <template #icon
            ><n-icon><PlusOutlined /></n-icon
          ></template>
          新增模型
        </n-button>
      </div>
      <n-tabs v-model:value="activeType" type="line" animated @update:value="onTabChange">
        <n-tab-pane :name="1" tab="对话模型">
          <n-spin :show="loading">
            <n-empty v-if="list.length === 0" description="暂无模型，点击「新增模型」" />
            <div v-else class="model-grid">
              <div v-for="m in list" :key="m.id" class="model-card">
                <div class="mc-head">
                  <span class="mc-name">{{ m.name || '未命名' }}</span>
                  <n-switch size="small" :value="m.status === 1" @update:value="handleStatus(m)">
                    <template #checked>启用</template>
                    <template #unchecked>禁用</template>
                  </n-switch>
                </div>
                <div class="mc-row">
                  <span class="mc-label">模型名</span>
                  <span class="mc-value mc-ellipsis">{{ m.models || '-' }}</span>
                </div>
                <div class="mc-row">
                  <span class="mc-label">优先级</span>
                  <span class="mc-value">{{ m.priority ?? 100 }}</span>
                </div>
                <div v-if="activeType === 1" class="mc-row">
                  <span class="mc-label">深度思考</span>
                  <n-tag size="tiny" :type="m.supportsThinking === 1 ? 'info' : 'default'">
                    {{ m.supportsThinking === 1 ? '支持' : '不支持' }}
                  </n-tag>
                </div>
                <div class="mc-actions">
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="primary"
                    @click="openEdit(m)"
                  >
                    <template #icon><n-icon><EditOutlined /></n-icon></template>
                    编辑
                  </n-button>
                  <span class="mc-action-divider">|</span>
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="error"
                    @click="handleDelete(m)"
                  >
                    <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                    删除
                  </n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-tab-pane>
        <n-tab-pane :name="2" tab="向量模型">
          <n-spin :show="loading">
            <n-empty v-if="list.length === 0" description="暂无模型，点击「新增模型」" />
            <div v-else class="model-grid">
              <div v-for="m in list" :key="m.id" class="model-card">
                <div class="mc-head">
                  <span class="mc-name">{{ m.name || '未命名' }}</span>
                  <n-switch size="small" :value="m.status === 1" @update:value="handleStatus(m)">
                    <template #checked>启用</template>
                    <template #unchecked>禁用</template>
                  </n-switch>
                </div>
                <div class="mc-row">
                  <span class="mc-label">模型名</span>
                  <span class="mc-value mc-ellipsis">{{ m.models || '-' }}</span>
                </div>
                <div class="mc-row">
                  <span class="mc-label">优先级</span>
                  <span class="mc-value">{{ m.priority ?? 100 }}</span>
                </div>
                <div class="mc-actions">
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="primary"
                    @click="openEdit(m)"
                  >
                    <template #icon><n-icon><EditOutlined /></n-icon></template>
                    编辑
                  </n-button>
                  <span class="mc-action-divider">|</span>
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="error"
                    @click="handleDelete(m)"
                  >
                    <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                    删除
                  </n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-tab-pane>
        <n-tab-pane :name="3" tab="重排模型">
          <n-spin :show="loading">
            <n-empty v-if="list.length === 0" description="暂无模型，点击「新增模型」" />
            <div v-else class="model-grid">
              <div v-for="m in list" :key="m.id" class="model-card">
                <div class="mc-head">
                  <span class="mc-name">{{ m.name || '未命名' }}</span>
                  <n-switch size="small" :value="m.status === 1" @update:value="handleStatus(m)">
                    <template #checked>启用</template>
                    <template #unchecked>禁用</template>
                  </n-switch>
                </div>
                <div class="mc-row">
                  <span class="mc-label">模型名</span>
                  <span class="mc-value mc-ellipsis">{{ m.models || '-' }}</span>
                </div>
                <div class="mc-row">
                  <span class="mc-label">优先级</span>
                  <span class="mc-value">{{ m.priority ?? 100 }}</span>
                </div>
                <div class="mc-actions">
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="primary"
                    @click="openEdit(m)"
                  >
                    <template #icon><n-icon><EditOutlined /></n-icon></template>
                    编辑
                  </n-button>
                  <span class="mc-action-divider">|</span>
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="error"
                    @click="handleDelete(m)"
                  >
                    <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                    删除
                  </n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-tab-pane>
        <n-tab-pane :name="4" tab="视觉模型">
          <n-spin :show="loading">
            <n-empty v-if="list.length === 0" description="暂无模型，点击「新增模型」" />
            <div v-else class="model-grid">
              <div v-for="m in list" :key="m.id" class="model-card">
                <div class="mc-head">
                  <span class="mc-name">{{ m.name || '未命名' }}</span>
                  <n-switch size="small" :value="m.status === 1" @update:value="handleStatus(m)">
                    <template #checked>启用</template>
                    <template #unchecked>禁用</template>
                  </n-switch>
                </div>
                <div class="mc-row">
                  <span class="mc-label">模型名</span>
                  <span class="mc-value mc-ellipsis">{{ m.models || '-' }}</span>
                </div>
                <div class="mc-row">
                  <span class="mc-label">优先级</span>
                  <span class="mc-value">{{ m.priority ?? 100 }}</span>
                </div>
                <div class="mc-actions">
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="primary"
                    @click="openEdit(m)"
                  >
                    <template #icon><n-icon><EditOutlined /></n-icon></template>
                    编辑
                  </n-button>
                  <span class="mc-action-divider">|</span>
                  <n-button
                    class="mc-action-btn"
                    size="tiny"
                    text
                    type="error"
                    @click="handleDelete(m)"
                  >
                    <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                    删除
                  </n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <ModelEditModal ref="editModalRef" @saved="loadList" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { PlusOutlined, EditOutlined, DeleteOutlined } from '@vicons/antd';
  import { getModelList, delModel, setModelStatus, type AiModel } from '@/api/system/aiModel';
  import ModelEditModal from './components/ModelEditModal.vue';

  const message = useMessage();
  const dialog = useDialog();

  const activeType = ref<number>(1);
  const loading = ref(false);
  const list = ref<AiModel[]>([]);
  const editModalRef = ref<InstanceType<typeof ModelEditModal> | null>(null);

  async function loadList() {
    loading.value = true;
    try {
      const res: any = await getModelList({ type: activeType.value });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        list.value = res.data.filter((x: any) => x && x.id != null);
      } else {
        list.value = [];
      }
    } catch (e) {
      message.error('加载模型列表失败');
      list.value = [];
    } finally {
      loading.value = false;
    }
  }

  function onTabChange(name: string) {
    activeType.value = Number(name);
    loadList();
  }

  function openCreate() {
    editModalRef.value?.openCreate(activeType.value);
  }

  function openEdit(model: AiModel) {
    editModalRef.value?.openEdit(model);
  }

  function handleStatus(model: AiModel) {
    const next = model.status === 1 ? 2 : 1;
    setModelStatus(model.id!, next).then((res: any) => {
      if (res && res.code === 0) {
        model.status = next;
        message.success(next === 1 ? '已启用' : '已禁用');
      } else {
        message.error(res?.message || '操作失败');
      }
    });
  }

  function handleDelete(model: AiModel) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除模型「${model.name}」？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delModel(model.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  onMounted(() => {
    loadList();
  });
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  /* 启用/禁用开关文字调小，与卡片整体 13px 字体协调 */
  .model-card :deep(.n-switch__children) {
    font-size: 12px;
  }
  .model-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }
  .model-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 14px;
  }
  .model-card {
    position: relative;
    overflow: hidden;
    border: 1px solid #eee;
    border-radius: 8px;
    padding: 14px;
    background: linear-gradient(135deg, #ffffff 0%, rgba(7, 192, 95, 0.04) 100%);
    transition: all 0.2s;
    &:hover {
      border-color: #07c05f;
      box-shadow: 0 4px 12px rgba(7, 192, 95, 0.12);
      background: linear-gradient(135deg, #ffffff 0%, rgba(7, 192, 95, 0.08) 100%);
    }
    // 右上角渐变花纹装饰
    &::after {
      content: '';
      position: absolute;
      top: 0;
      right: 0;
      width: 60px;
      height: 60px;
      background: linear-gradient(135deg, rgba(7, 192, 95, 0.08) 0%, transparent 100%);
      border-radius: 0 12px 0 100%;
      pointer-events: none;
      z-index: 0;
    }
    // 内容置于装饰花纹之上
    > * {
      position: relative;
      z-index: 1;
    }
  }
  .mc-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }
  .mc-name {
    font-weight: 600;
    font-size: 15px;
  }
  .mc-row {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    margin-bottom: 6px;
  }
  .mc-label {
    color: #aaa;
    width: 60px;
    flex-shrink: 0;
  }
  .mc-value {
    color: #333;
    flex: 1;
    min-width: 0;
  }
  .mc-ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .mc-actions {
    display: flex;
    align-items: center;
    margin-top: 10px;
    border-top: 1px solid #f5f5f5;
    padding-top: 8px;
  }
  .mc-action-btn {
    flex: 1;
    text-align: center;
    /* tiny text 按钮默认垂直对不齐，拉齐基线；加图标后保证图标与文字水平居中不换行 */
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 4px;
  }
  .mc-action-divider {
    color: #e0e0e0;
    font-size: 12px;
    line-height: 1;
  }
</style>
