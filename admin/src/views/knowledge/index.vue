<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="知识库">
        管理知识库与文档，支持文档解析、向量化、问题生成与命中测试
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="kb-toolbar">
        <n-space align="center">
          <n-input
            v-model:value="keyword"
            placeholder="搜索知识库名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <n-button type="primary" secondary @click="handleSearch">
            <template #icon
              ><n-icon><SearchOutlined /></n-icon
            ></template>
            搜索
          </n-button>
          <n-button type="primary" secondary @click="openCreate">
            <template #icon
              ><n-icon><PlusOutlined /></n-icon
            ></template>
            新建知识库
          </n-button>
        </n-space>
      </div>

      <n-spin :show="loading">
        <n-empty
          v-if="!loading && list.length === 0"
          description="暂无知识库，点击右上角「新建知识库」"
        />
        <div v-else class="kb-grid">
          <div
            v-for="kb in list"
            :key="kb.id"
            class="kb-card"
            :class="{ disabled: kb.status === 2 }"
          >
            <div class="kb-head" @click="goDetail(kb)">
              <n-icon :size="22" color="#07c05f"><BookOutlined /></n-icon>
              <span class="kb-name">{{ kb.name || '未命名知识库' }}</span>
              <span class="kb-status-switch" @click.stop>
                <n-switch
                  size="small"
                  :value="kb.status === 1"
                  :loading="switchingId === kb.id"
                  @update:value="handleStatus(kb)"
                >
                  <template #checked>启用</template>
                  <template #unchecked>禁用</template>
                </n-switch>
              </span>
            </div>

            <div class="kb-desc" @click="goDetail(kb)">
              {{ kb.description || '暂无描述' }}
            </div>

            <div class="kb-meta">
              <span class="meta-item">
                <span class="meta-label">嵌入模型</span>
                <n-tooltip
                  placement="top"
                  :disabled="!embeddingModelText(kb) || embeddingModelText(kb) === '-'"
                >
                  <template #trigger>
                    <span class="meta-value">{{ embeddingModelText(kb) }}</span>
                  </template>
                  {{ embeddingModelText(kb) }}
                </n-tooltip>
              </span>
              <span class="meta-item">
                <span class="meta-label">维度</span>
                <span class="meta-value">{{ kb.dimension || '-' }}</span>
              </span>
              <span class="meta-item">
                <span class="meta-label">文档数</span>
                <span class="meta-value">{{ kb.docCount ?? 0 }}</span>
              </span>
            </div>

            <div class="kb-actions">
              <n-button class="kb-action-btn" size="tiny" quaternary type="primary" @click="goDetail(kb)">
                <template #icon><n-icon><SettingOutlined /></n-icon></template>
                设置
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary @click="handleHitTest(kb)">
                <template #icon><n-icon><AimOutlined /></n-icon></template>
                命中测试
              </n-button>
              <n-button
                class="kb-action-btn"
                size="tiny"
                quaternary
                type="error"
                :disabled="deleting"
                @click="handleDelete(kb)"
              >
                <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                删除
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>

      <div class="mt-4" style="display: flex; justify-content: flex-end">
        <n-pagination
          v-model:page="page"
          v-model:page-size="size"
          :item-count="total"
          show-size-picker
          :page-sizes="[10, 20, 50]"
          show-quick-jumper
          @update:page="loadList"
          @update:page-size="onSizeChange"
        />
      </div>
    </n-card>

    <KbSaveModal ref="saveModalRef" @saved="loadList" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { useMessage, useDialog } from 'naive-ui';
  import { SearchOutlined, PlusOutlined, BookOutlined, SettingOutlined, AimOutlined, DeleteOutlined } from '@vicons/antd';
  import { getKbList, delKb, switchKbStatus, type KnowledgeBase } from '@/api/system/knowledge';
  import KbSaveModal from './components/KbSaveModal.vue';

  const router = useRouter();
  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const deleting = ref(false);
  const switchingId = ref<string | null>(null);
  const list = ref<KnowledgeBase[]>([]);
  const keyword = ref('');
  const page = ref(1);
  const size = ref(10);
  const total = ref(0);

  const saveModalRef = ref<InstanceType<typeof KbSaveModal> | null>(null);

  async function loadList() {
    loading.value = true;
    try {
      const res: any = await getKbList({
        keyword: keyword.value || undefined,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        list.value = arr.filter((x: any) => x && x.id != null);
        total.value = res.data.total || 0;
      } else {
        list.value = [];
        total.value = 0;
      }
    } catch (e) {
      message.error('加载知识库列表失败');
      list.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    page.value = 1;
    loadList();
  }

  function onSizeChange(s: number) {
    size.value = s;
    page.value = 1;
    loadList();
  }

  function openCreate() {
    saveModalRef.value?.openCreate();
  }

  function goDetail(kb: KnowledgeBase) {
    router.push({ path: '/knowledge/detail', query: { kbId: kb.id } });
  }

  // 拼接嵌入模型展示文本：优先用模型名称，配合 model 标识，尽量完整展示
  function embeddingModelText(kb: KnowledgeBase): string {
    const name = kb.embeddingModelName;
    const model = kb.embeddingModel;
    if (name && model && name !== model) return `${model} / ${name}`;
    return name || model || '-';
  }

  function handleStatus(kb: KnowledgeBase) {
    if (switchingId.value) return;
    const next = kb.status === 1 ? 2 : 1;
    switchingId.value = kb.id;
    switchKbStatus(kb.id, next)
      .then((res: any) => {
        if (res && res.code === 0) {
          kb.status = next;
          message.success(next === 1 ? '已启用' : '已禁用');
        } else {
          message.error(res?.message || '操作失败');
        }
      })
      .catch(() => {
        message.error('操作失败，请重试');
      })
      .finally(() => {
        switchingId.value = null;
      });
  }

  function handleHitTest(kb: KnowledgeBase) {
    // 跳转命中测试页（复用详情页的命中测试面板）
    router.push({ path: '/knowledge/detail', query: { kbId: kb.id, tab: 'hitTest' } });
  }

  function handleDelete(kb: KnowledgeBase) {
    if (deleting.value) return;
    dialog.warning({
      title: '确认删除',
      content: `确定删除知识库「${kb.name}」？将级联删除其下全部文档、子块与问题，不可撤销。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        if (deleting.value) return;
        deleting.value = true;
        const hide = message.loading(`正在删除知识库「${kb.name}」，请稍候...`);
        try {
          const res: any = await delKb(kb.id);
          if (res && res.code === 0) {
            message.success('已删除');
            await loadList();
          } else {
            message.error(res?.message || '删除失败');
          }
        } catch (e) {
          message.error('删除失败，请重试');
        } finally {
          hide.destroy();
          deleting.value = false;
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
  .kb-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }
  .kb-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 14px;
  }
  .kb-card {
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
    &.disabled {
      opacity: 0.6;
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
  .kb-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    cursor: pointer;
  }
  .kb-name {
    font-weight: 600;
    font-size: 15px;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .kb-status-switch {
    flex-shrink: 0;
  }
  .kb-desc {
    font-size: 13px;
    color: #888;
    line-height: 1.5;
    height: 40px;
    overflow: hidden;
    margin-bottom: 10px;
    cursor: pointer;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .kb-meta {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    padding: 8px 0;
    border-top: 1px solid #f5f5f5;
    border-bottom: 1px solid #f5f5f5;
    margin-bottom: 8px;
  }
  .meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .meta-label {
    font-size: 11px;
    color: #aaa;
  }
  .meta-value {
    font-size: 13px;
    color: #333;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .kb-actions {
    display: flex;
    gap: 6px;
    width: 100%;
  }
  .kb-action-btn {
    flex: 1 1 0;
    min-width: 0;
  }
</style>
