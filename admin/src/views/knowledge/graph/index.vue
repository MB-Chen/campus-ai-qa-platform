<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="知识图谱">
        基于 LLM 从文档父块中抽取实体/关系，写入 Neo4j 图谱 + PostgreSQL 向量索引。 检索时 LLM 抽取
        query 实体 → 向量召回实体 → 子图扩展 → 取关联 chunk，增强复杂多实体问题的召回。
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <n-spin :show="configLoading">
        <n-form label-placement="left" label-width="140" :model="configForm">
          <n-grid :cols="2" :x-gap="24">
            <n-gi>
              <n-form-item label="知识图谱开关">
                <n-switch
                  v-model:value="configForm.enabled"
                  :checked-value="1"
                  :unchecked-value="2"
                >
                  <template #checked>启用</template>
                  <template #unchecked>禁用</template>
                </n-switch>
                <n-text depth="3" style="margin-left: 8px; font-size: 12px">
                  全局开关；文档级开关在各文档「文档管理」中单独控制
                </n-text>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="Neo4j 连通性">
                <n-space align="center" :size="8">
                  <n-tag
                    :type="
                      neo4jStatus === 'ok'
                        ? 'success'
                        : neo4jStatus === 'fail'
                        ? 'error'
                        : neo4jStatus === 'unconfigured'
                        ? 'warning'
                        : 'default'
                    "
                    size="small"
                    round
                  >
                    {{
                      neo4jStatus === 'ok'
                        ? '已连接'
                        : neo4jStatus === 'fail'
                        ? '连接失败'
                        : neo4jStatus === 'unconfigured'
                        ? '未配置'
                        : '未测试'
                    }}
                  </n-tag>
                  <n-text v-if="neo4jTestedAt" depth="3" style="font-size: 11px">
                    上次测试 {{ neo4jTestedAt }}
                  </n-text>
                  <n-button
                    size="small"
                    type="primary"
                    ghost
                    :loading="testing"
                    @click="handleTestConnect"
                  >
                    测试连接
                  </n-button>
                </n-space>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="抽取 LLM 模型">
                <n-select
                  v-model:value="configForm.extractModelId"
                  placeholder="选择对话模型（type=1）"
                  :options="extractModelOptions"
                  :loading="extractModelLoading"
                  filterable
                  style="width: 280px"
                  @update:value="(val) => handleExtractModelChange(val)"
                />
                <n-text depth="3" style="margin-left: 8px; font-size: 12px">
                  {{ configForm.extractModelName || '未选择' }}
                </n-text>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="向量化模型">
                <n-select
                  v-model:value="configForm.embeddingModelId"
                  placeholder="选择向量模型（type=2）"
                  :options="embeddingModelOptions"
                  :loading="embeddingModelLoading"
                  filterable
                  style="width: 280px"
                  @update:value="(val) => handleEmbeddingModelChange(val)"
                />
                <n-text depth="3" style="margin-left: 8px; font-size: 12px">
                  {{ configForm.embeddingModelName || '未选择' }}
                </n-text>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="向量召回阈值">
                <n-input-number
                  v-model:value="configForm.similarityThreshold"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  style="width: 160px"
                />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="子图跳数">
                <n-radio-group v-model:value="configForm.hopDepth">
                  <n-radio :value="1">一跳</n-radio>
                  <n-radio :value="2">二跳</n-radio>
                </n-radio-group>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="二跳衰减权重" v-if="configForm.hopDepth === 2">
                <n-input-number
                  v-model:value="configForm.secondHopWeight"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  style="width: 160px"
                />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="实体合并阈值">
                <n-input-number
                  v-model:value="configForm.entityMergeThreshold"
                  :min="0.5"
                  :max="1"
                  :step="0.01"
                  style="width: 160px"
                />
                <template #feedback>
                  <span style="font-size: 11px; color: #999">
                    embedding 相似度 ≥ 此值时合并同义实体（默认
                    0.88），解决"北京/北京市"无法合并
                  </span>
                </template>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="抽取批量大小">
                <n-input-number
                  v-model:value="configForm.extractBatchSize"
                  :min="5"
                  :max="100"
                  :step="5"
                  style="width: 160px"
                />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="检索模式">
                <n-radio-group v-model:value="configForm.retrievalMode">
                  <n-radio value="local">local（子图扩展）</n-radio>
                  <n-radio value="global">global（社区摘要）</n-radio>
                  <n-radio value="hybrid">hybrid（双路并行）</n-radio>
                </n-radio-group>
                <template #feedback>
                  <span style="font-size: 11px; color: #999">
                    global/hybrid 需先启用社区检测并点击"运行社区检测"生成摘要
                  </span>
                </template>
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="社区检测">
                <n-switch
                  :value="configForm.communityEnabled === 1"
                  @update:value="(v) => (configForm.communityEnabled = v ? 1 : 2)"
                />
                <span style="font-size: 11px; color: #999; margin-left: 8px">
                  开启后可点"运行社区检测"（需 GDS 插件）
                </span>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-space style="margin-top: 12px">
            <n-button
              type="primary"
              secondary
              :loading="configSaving"
              @click="handleSaveConfig"
              >保存配置</n-button
            >
            <n-select
              v-model:value="selectedKbId"
              placeholder="选择知识库（社区检测目标）"
              :options="kbOptions"
              filterable
              style="width: 260px"
            />
            <n-button
              type="warning"
              secondary
              :loading="communityDetecting"
              :disabled="!selectedKbId || configForm.communityEnabled !== 1"
              @click="handleDetectCommunity"
            >
              运行社区检测
            </n-button>
            <n-text depth="3" style="font-size: 11px; line-height: 32px">
              需先开启社区检测 + 选知识库；依赖 Neo4j GDS 插件
            </n-text>
          </n-space>
        </n-form>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, onMounted } from 'vue';
  import { useMessage } from 'naive-ui';
  import {
    getKgConfig,
    saveKgConfig,
    testKgConnect,
    triggerCommunityDetect,
  } from '@/api/system/knowledgeGraph';
  import { getModelList, MODEL_TYPE } from '@/api/system/aiModel';
  import { getKbList } from '@/api/system/knowledge';
  import type { KgConfig } from '@/api/system/knowledgeGraph';

  const message = useMessage();

  const configLoading = ref(false);
  const configSaving = ref(false);
  const configForm = reactive<KgConfig>({
    enabled: 2,
    extractModelId: undefined,
    extractModelName: '',
    embeddingModelId: undefined,
    embeddingModelName: '',
    similarityThreshold: 0.65,
    hopDepth: 1,
    secondHopWeight: 0.5,
    entityMergeThreshold: 0.88,
    retrievalMode: 'local',
    communityEnabled: 2,
    extractBatchSize: 5,
  });

  // 抽取 LLM 用 type=1（对话），向量化用 type=2（向量），都只列启用的
  const extractModelOptions = ref<{ label: string; value: number; raw?: any }[]>([]);
  const embeddingModelOptions = ref<{ label: string; value: number; raw?: any }[]>([]);
  const extractModelLoading = ref(false);
  const embeddingModelLoading = ref(false);

  /** 把 ai_model 记录格式化成 n-select option：label = 名称(provider) / models 首项 */
  function toModelOption(m: any): { label: string; value: number; raw: any } {
    const firstModel =
      (m.models || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean)[0] || '';
    const label = `${m.name}${firstModel ? ' / ' + firstModel : ''}`;
    return { label, value: m.id, raw: m };
  }

  async function loadExtractModelOptions() {
    extractModelLoading.value = true;
    try {
      const res: any = await getModelList({ type: MODEL_TYPE.CHAT, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        extractModelOptions.value = res.data.map(toModelOption);
      }
    } catch {
      // 静默
    } finally {
      extractModelLoading.value = false;
    }
  }

  async function loadEmbeddingModelOptions() {
    embeddingModelLoading.value = true;
    try {
      const res: any = await getModelList({ type: MODEL_TYPE.EMBEDDING, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        embeddingModelOptions.value = res.data.map(toModelOption);
      }
    } catch {
      // 静默
    } finally {
      embeddingModelLoading.value = false;
    }
  }

  /** 选中抽取模型时，同步回填 modelName 快照（取 models 首项作为具体模型名） */
  function handleExtractModelChange(id: number) {
    const opt = extractModelOptions.value.find((o) => o.value === id);
    if (!opt) {
      configForm.extractModelName = '';
      return;
    }
    const firstModel =
      (opt.raw.models || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean)[0] || '';
    configForm.extractModelName = firstModel;
  }

  /** 选中向量模型时，同步回填 modelName 快照 */
  function handleEmbeddingModelChange(id: number) {
    const opt = embeddingModelOptions.value.find((o) => o.value === id);
    if (!opt) {
      configForm.embeddingModelName = '';
      return;
    }
    const firstModel =
      (opt.raw.models || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean)[0] || '';
    configForm.embeddingModelName = firstModel;
  }

  async function loadConfig() {
    configLoading.value = true;
    try {
      const res: any = await getKgConfig();
      if (res && res.code === 0 && res.data) {
        Object.assign(configForm, res.data);
      }
    } catch {
      // 静默
    } finally {
      configLoading.value = false;
    }
  }

  async function handleSaveConfig() {
    configSaving.value = true;
    try {
      const res: any = await saveKgConfig({ ...configForm });
      if (res && res.code === 0) {
        message.success('配置已保存');
        await loadConfig();
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e: any) {
      message.error(e?.message || '保存失败');
    } finally {
      configSaving.value = false;
    }
  }

  const selectedKbId = ref<string | null>(null);
  const kbOptions = ref<{ label: string; value: string }[]>([]);
  const communityDetecting = ref(false);

  async function loadKbOptions() {
    try {
      const res: any = await getKbList({ page: 1, size: 200 });
      if (res && res.code === 0 && res.data?.data) {
        kbOptions.value = res.data.data.map((kb: any) => ({
          label: kb.name,
          value: kb.id,
        }));
      }
    } catch {
      // 静默
    }
  }

  async function handleDetectCommunity() {
    if (!selectedKbId.value) {
      message.warning('请先选择知识库');
      return;
    }
    communityDetecting.value = true;
    try {
      const res: any = await triggerCommunityDetect(selectedKbId.value);
      if (res && res.code === 0) {
        message.success('社区检测已触发（异步执行），稍后可在 Neo4j 浏览器查看 community_id 属性');
      } else {
        message.error(res?.message || '触发失败');
      }
    } catch (e: any) {
      message.error(e?.message || '触发失败');
    } finally {
      communityDetecting.value = false;
    }
  }

  // unconfigured：Neo4j 未在「外部服务配置」页配置（后端返回约定信号 UNCONFIGURED）
  // 测试结果持久化到 localStorage，刷新页面后仍显示上次结果（而非"未测试"）
  const NEO4J_STATUS_KEY = 'kg_neo4j_status';
  const NEO4J_TESTED_AT_KEY = 'kg_neo4j_tested_at';

  function readStoredStatus(): 'idle' | 'ok' | 'fail' | 'unconfigured' {
    const v = localStorage.getItem(NEO4J_STATUS_KEY);
    return v === 'ok' || v === 'fail' || v === 'unconfigured' ? v : 'idle';
  }

  const neo4jStatus = ref<'idle' | 'ok' | 'fail' | 'unconfigured'>(readStoredStatus());
  const neo4jTestedAt = ref<string>(localStorage.getItem(NEO4J_TESTED_AT_KEY) || '');
  const testing = ref(false);

  function persistNeo4jStatus(status: 'ok' | 'fail' | 'unconfigured') {
    neo4jStatus.value = status;
    neo4jTestedAt.value = new Date().toLocaleString('zh-CN', { hour12: false });
    localStorage.setItem(NEO4J_STATUS_KEY, status);
    localStorage.setItem(NEO4J_TESTED_AT_KEY, neo4jTestedAt.value);
  }

  async function handleTestConnect() {
    testing.value = true;
    try {
      const res: any = await testKgConnect();
      if (res && res.code === 0) {
        // 后端未配置 Neo4j 时返回约定字符串 'UNCONFIGURED'（getSchema()==null）
        if (res.data === 'UNCONFIGURED') {
          persistNeo4jStatus('unconfigured');
          message.warning('Neo4j 未配置，请先在「外部服务配置」页添加并启用 Neo4j 连接');
        } else {
          persistNeo4jStatus('ok');
          message.success('Neo4j 连接成功: ' + (res.data || ''));
        }
      } else {
        persistNeo4jStatus('fail');
        message.error(res?.message || '连接失败');
      }
    } catch {
      persistNeo4jStatus('fail');
      message.error('连接失败');
    } finally {
      testing.value = false;
    }
  }

  onMounted(() => {
    loadConfig();
    loadKbOptions();
    loadExtractModelOptions();
    loadEmbeddingModelOptions();
  });
</script>
