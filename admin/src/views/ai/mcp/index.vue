<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="MCP服务">
        管理外部 MCP Server 连接配置（SSE / HTTP Streamable），工具可接入知识库意图树与 RAG 管线
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="toolbar">
        <n-input
          v-model:value="keyword"
          class="filter-input"
          placeholder="按名称筛选 MCP 服务"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <n-icon><SearchOutlined /></n-icon>
          </template>
        </n-input>
        <n-button type="primary" secondary @click="handleSearch">
          <template #icon
            ><n-icon><SearchOutlined /></n-icon
          ></template>
          搜索
        </n-button>
        <n-button type="primary" secondary @click="openCreate">+ 新增服务</n-button>
      </div>

      <n-spin :show="loading">
        <div v-if="renderList.length === 0 && !loading" class="empty-tip">
          暂无 MCP 服务，点击「新增服务」配置
        </div>
        <div class="model-grid">
          <div v-for="item in renderList" :key="item.id" class="model-card">
            <div class="mc-tag-wrap">
              <n-tag size="small" :type="transportTagType(item.transportType)" round>
                {{ transportLabel(item.transportType) }}
              </n-tag>
              <n-tag size="small" :type="item.enabled ? 'success' : 'default'" round>
                {{ item.enabled ? '启用' : '禁用' }}
              </n-tag>
            </div>

            <div class="mc-head">
              <span class="mc-name">{{ item.name || '未命名' }}</span>
              <n-switch size="small" :value="!!item.enabled" @update:value="handleStatus(item)" />
            </div>

            <div class="mc-row">
              <span class="mc-label">地址</span>
              <span class="mc-value mc-ellipsis" :title="item.url">{{ item.url || '-' }}</span>
            </div>
            <div class="mc-row">
              <span class="mc-label">认证</span>
              <span class="mc-value">{{ authLabel(item.authType) }}{{ authSuffix(item) }}</span>
            </div>
            <div class="mc-row">
              <span class="mc-label">工具</span>
              <span class="mc-value">{{ item.toolCount ?? 0 }} 个</span>
            </div>
            <div v-if="item.description" class="mc-row">
              <span class="mc-label">描述</span>
              <span class="mc-value mc-ellipsis">{{ item.description }}</span>
            </div>

            <div class="mc-actions">
              <n-button
                class="mc-action-btn"
                size="tiny"
                text
                type="primary"
                @click="openEdit(item)"
              >
                编辑
              </n-button>
              <span class="mc-action-divider">|</span>
              <n-button
                class="mc-action-btn"
                size="tiny"
                text
                type="info"
                @click="handleTest(item)"
              >
                测试
              </n-button>
              <span class="mc-action-divider">|</span>
              <n-button
                class="mc-action-btn"
                size="tiny"
                text
                type="warning"
                @click="handleRefresh(item)"
              >
                刷新工具
              </n-button>
              <span class="mc-action-divider">|</span>
              <n-button
                class="mc-action-btn"
                size="tiny"
                text
                type="error"
                @click="handleDelete(item)"
              >
                删除
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>
    </n-card>

    <McpEditModal ref="editModalRef" @saved="loadAll" />

    <!-- 测试结果弹窗 -->
    <McpTestResult ref="testResultRef" />
  </div>
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { SearchOutlined } from '@vicons/antd';
  import {
    getMcpList,
    delMcp,
    switchMcpStatus,
    testMcp,
    refreshMcpTools,
    TRANSPORT_LABEL,
    TRANSPORT_TAG_TYPE,
    AUTH_LABEL,
    type McpServer,
  } from '@/api/system/aiMcp';
  import McpEditModal from './components/McpEditModal.vue';
  import McpTestResult from './components/McpTestResult.vue';

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const editModalRef = ref<InstanceType<typeof McpEditModal> | null>(null);
  const testResultRef = ref<InstanceType<typeof McpTestResult> | null>(null);

  const dataList = ref<McpServer[]>([]);
  const keyword = ref('');

  // ★ 严格校验数组 + 过滤 null（拦截器非 0 code 不 reject）+ 名称筛选
  const renderList = computed(() => {
    const kw = keyword.value.trim().toLowerCase();
    return dataList.value.filter((m) => {
      if (!m || m.id == null) return false;
      if (kw && !`${m.name || ''}`.toLowerCase().includes(kw)) return false;
      return true;
    });
  });

  const transportLabel = (t?: string) => TRANSPORT_LABEL[t || ''] || t || '-';
  const transportTagType = (t?: string) => (TRANSPORT_TAG_TYPE as any)[t || ''] || 'default';
  const authLabel = (a?: string) => AUTH_LABEL[a || ''] || a || '-';
  const authSuffix = (item: McpServer) => {
    if (item.authType === 'api_key') return item.hasApiKey ? '（已配置）' : '（未配置）';
    if (item.authType === 'bearer') return item.hasToken ? '（已配置）' : '（未配置）';
    return '';
  };

  // 筛选为前端实时 computed（renderList 已依赖 keyword 自动重算），
  // 此处仅规整 keyword（去空格）并触发一次重渲染，保持与知识库列表交互一致
  function handleSearch() {
    keyword.value = keyword.value.trim();
  }

  async function loadAll() {
    loading.value = true;
    try {
      const res: any = await getMcpList();
      if (res && res.code === 0 && Array.isArray(res.data)) {
        dataList.value = res.data.filter((x: any) => x && x.id != null);
      } else {
        dataList.value = [];
        if (res && res.code !== 0) message.error(res.message || '加载失败');
      }
    } catch {
      message.error('加载 MCP 服务列表失败');
      dataList.value = [];
    } finally {
      loading.value = false;
    }
  }

  function openCreate() {
    editModalRef.value?.openCreate();
  }
  function openEdit(item: McpServer) {
    editModalRef.value?.openEdit(item);
  }

  function handleStatus(item: McpServer) {
    const next = !item.enabled;
    switchMcpStatus(item.id!, next).then((res: any) => {
      if (res && res.code === 0) {
        item.enabled = next;
        message.success(next ? '已启用' : '已禁用');
      } else {
        message.error(res?.message || '操作失败');
      }
    });
  }

  function handleTest(item: McpServer) {
    const m = message.loading('测试中...');
    testMcp(item.id!)
      .then((res: any) => {
        m.destroy();
        if (res && res.code === 0 && res.data) {
          testResultRef.value?.show(res.data);
          if (res.data.success) {
            loadAll(); // 测试成功可能更新了工具数
          }
        } else {
          message.error(res?.message || '测试失败');
        }
      })
      .catch(() => {
        m.destroy();
        message.error('测试请求失败');
      });
  }

  function handleRefresh(item: McpServer) {
    const m = message.loading('刷新工具中...');
    refreshMcpTools(item.id!)
      .then((res: any) => {
        m.destroy();
        if (res && res.code === 0 && res.data) {
          testResultRef.value?.show(res.data);
          if (res.data.success) {
            loadAll();
            message.success('工具列表已刷新');
          } else {
            message.error(res.data.message || '刷新失败');
          }
        } else {
          message.error(res?.message || '刷新失败');
        }
      })
      .catch(() => {
        m.destroy();
        message.error('刷新请求失败');
      });
  }

  function handleDelete(item: McpServer) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除 MCP 服务「${item.name}」？关联的工具快照将一并删除，意图树中引用该服务的工具节点将失效。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delMcp(item.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadAll();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  loadAll();
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-bottom: 16px;
  }
  .filter-input {
    max-width: 280px;
  }
  .empty-tip {
    text-align: center;
    color: #bbb;
    padding: 60px 0;
  }
  .model-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
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
    }
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
    > * {
      position: relative;
      z-index: 1;
    }
  }
  .mc-tag-wrap {
    display: flex;
    gap: 6px;
    margin-bottom: 8px;
    flex-wrap: wrap;
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
    width: 44px;
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
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .mc-action-divider {
    color: #e0e0e0;
    font-size: 12px;
    line-height: 1;
  }
</style>
