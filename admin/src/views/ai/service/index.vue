<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="解析引擎"> 管理 MinerU、Neo4j 等外部服务的接入配置 </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <n-spin :show="loading">
        <div class="model-grid">
          <div
            v-for="item in renderList"
            :key="item.key"
            class="model-card"
            :class="{ 'mc-empty': !item.config }"
          >
            <div class="mc-tag">{{ categoryLabel(item.category) }}</div>

            <!-- 已配置 -->
            <template v-if="item.config">
              <div class="mc-head">
                <span class="mc-name">{{ item.config.name || '未命名' }}</span>
                <n-switch
                  size="small"
                  :value="item.config.status === 1"
                  @update:value="handleStatus(item.config)"
                >
                  <template #checked>启用</template>
                  <template #unchecked>禁用</template>
                </n-switch>
              </div>
              <div v-for="row in summaryRows(item.config)" :key="row.label" class="mc-row">
                <span class="mc-label">{{ row.label }}</span>
                <span class="mc-value mc-ellipsis">{{ row.value }}</span>
              </div>
              <div v-if="item.config.remark" class="mc-row">
                <span class="mc-label">备注</span>
                <span class="mc-value mc-ellipsis">{{ item.config.remark }}</span>
              </div>
              <div class="mc-actions">
                <n-button
                  class="mc-action-btn"
                  size="tiny"
                  text
                  type="primary"
                  @click="openEdit(item.config)"
                >
                  <template #icon
                    ><n-icon><EditOutlined /></n-icon
                  ></template>
                  编辑
                </n-button>
                <span class="mc-action-divider">|</span>
                <n-button
                  class="mc-action-btn"
                  size="tiny"
                  text
                  type="info"
                  @click="handleTest(item.config)"
                >
                  <template #icon
                    ><n-icon><ApiOutlined /></n-icon
                  ></template>
                  测试
                </n-button>
                <span class="mc-action-divider">|</span>
                <n-button
                  class="mc-action-btn"
                  size="tiny"
                  text
                  type="error"
                  @click="handleDelete(item.config)"
                >
                  <template #icon
                    ><n-icon><DeleteOutlined /></n-icon
                  ></template>
                  删除
                </n-button>
              </div>
            </template>

            <!-- 未配置占位 -->
            <template v-else>
              <div class="mc-head">
                <span class="mc-name mc-name-muted">未配置</span>
                <n-switch size="small" :value="false" disabled>
                  <template #unchecked>禁用</template>
                </n-switch>
              </div>
              <div class="mc-row">
                <span class="mc-label">状态</span>
                <span class="mc-value mc-muted">尚未接入</span>
              </div>
              <div class="mc-actions">
                <n-button
                  class="mc-action-btn"
                  size="tiny"
                  text
                  type="primary"
                  @click="openCreate(item.category)"
                >
                  <template #icon
                    ><n-icon><PlusOutlined /></n-icon
                  ></template>
                  配置
                </n-button>
              </div>
            </template>
          </div>
        </div>
      </n-spin>
    </n-card>

    <ServiceEditModal ref="editModalRef" @saved="loadAll" />
  </div>
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { EditOutlined, ApiOutlined, DeleteOutlined, PlusOutlined } from '@vicons/antd';
  import {
    getServiceList,
    delService,
    setServiceStatus,
    testService,
    getServiceTypes,
    type ExtServiceConfig,
  } from '@/api/system/aiService';
  import ServiceEditModal from './components/ServiceEditModal.vue';

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const editModalRef = ref<InstanceType<typeof ServiceEditModal> | null>(null);

  const serviceTypes = getServiceTypes();
  const categoryMeta = computed<Record<string, { label: string }>>(() => {
    const map: Record<string, { label: string }> = {};
    for (const st of serviceTypes) {
      for (const sub of st.subCategories) {
        map[sub.key] = { label: `${st.label} · ${sub.label}` };
      }
    }
    return map;
  });
  const categoryLabel = (cat: string) => categoryMeta.value[cat]?.label ?? cat;
  const allCategories = computed(() => Object.keys(categoryMeta.value));

  const dataMap = ref<Record<string, ExtServiceConfig[]>>({});
  const getList = (cat: string) => dataMap.value[cat] ?? [];

  /**
   * 统一渲染列表：每个分类至少占一个槽位
   * - 有配置：每条配置一张卡片
   * - 无配置：一张「未配置」占位卡片
   */
  interface RenderItem {
    key: string; // v-for key（category + id / category + '__empty'）
    category: string;
    config: ExtServiceConfig | null; // null = 占位
  }
  const renderList = computed<RenderItem[]>(() => {
    const items: RenderItem[] = [];
    for (const cat of allCategories.value) {
      const list = getList(cat);
      if (list.length === 0) {
        items.push({ key: cat + '__empty', category: cat, config: null });
      } else {
        for (const c of list) {
          items.push({ key: cat + '_' + c.id, category: cat, config: c });
        }
      }
    }
    return items;
  });

  async function loadAll() {
    loading.value = true;
    try {
      const results = await Promise.all(
        allCategories.value.map((cat) =>
          getServiceList({ category: cat })
            .then((res: any) =>
              res && res.code === 0 && Array.isArray(res.data)
                ? [cat, res.data.filter((x: any) => x && x.id != null)]
                : [cat, []]
            )
            .catch(() => [cat, []])
        )
      );
      const map: Record<string, ExtServiceConfig[]> = {};
      for (const [cat, list] of results) {
        map[cat as string] = list as ExtServiceConfig[];
      }
      dataMap.value = map;
    } catch {
      message.error('加载配置列表失败');
    } finally {
      loading.value = false;
    }
  }

  function openCreate(category: string) {
    editModalRef.value?.openCreate(category);
  }
  function openEdit(config: ExtServiceConfig) {
    editModalRef.value?.openEdit(config);
  }

  function handleStatus(config: ExtServiceConfig) {
    const next = config.status === 1 ? 2 : 1;
    setServiceStatus(config.id!, next).then((res: any) => {
      if (res && res.code === 0) {
        config.status = next;
        message.success(next === 1 ? '已启用' : '已禁用');
      } else {
        message.error(res?.message || '操作失败');
      }
    });
  }

  function handleTest(config: ExtServiceConfig) {
    message.loading('测试中...');
    testService(config.id!).then((res: any) => {
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
    });
  }

  function handleDelete(config: ExtServiceConfig) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除配置「${config.name}」？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delService(config.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadAll();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  /** 按类别提取卡片摘要（脱敏 apiKey） */
  function summaryRows(c: ExtServiceConfig): { label: string; value: string }[] {
    let cfg: any = {};
    try {
      cfg = c.config ? JSON.parse(c.config) : {};
    } catch {
      cfg = {};
    }
    if (c.category === 'mineru_self') {
      return [
        { label: '地址', value: cfg.endpoint || '-' },
        { label: '后端', value: cfg.model || 'pipeline' },
      ];
    }
    if (c.category === 'mineru_cloud') {
      return [
        { label: '模型', value: cfg.model || 'pipeline' },
        { label: 'ApiKey', value: maskKey(cfg.apiKey) },
      ];
    }
    if (c.category === 'neo4j_self') {
      return [
        { label: '地址', value: cfg.uri || '-' },
        { label: '用户', value: cfg.username || '-' },
      ];
    }
    return [{ label: '类别', value: c.category }];
  }

  function maskKey(key: string | undefined): string {
    if (!key) return '-';
    if (key.length <= 8) return '***';
    return key.slice(0, 6) + '***' + key.slice(-4);
  }

  loadAll();
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .model-card :deep(.n-switch__children) {
    font-size: 12px;
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
  /* 未配置占位卡片：整体变淡 */
  .mc-empty {
    background: #fafafa;
    border-style: dashed;
    border-color: #e0e0e0;
    &::after {
      background: linear-gradient(135deg, rgba(160, 160, 160, 0.06) 0%, transparent 100%);
    }
    &:hover {
      border-color: #07c05f;
      background: #fff;
    }
  }
  .mc-tag {
    display: inline-block;
    font-size: 11px;
    color: #07c05f;
    background: rgba(7, 192, 95, 0.1);
    padding: 2px 8px;
    border-radius: 4px;
    margin-bottom: 8px;
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
  .mc-name-muted {
    color: #bbb;
    font-weight: 500;
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
  .mc-muted {
    color: #bbb;
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
    gap: 4px;
  }
  .mc-action-divider {
    color: #e0e0e0;
    font-size: 12px;
    line-height: 1;
  }
</style>
