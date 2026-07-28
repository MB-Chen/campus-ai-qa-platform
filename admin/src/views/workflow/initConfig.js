// 编排节点初始数据 + 端口配置 + 条件操作符。
// 节点 data 结构是前后端契约（后端 FlowNodeParser.buildData 按 shape 分发）。
// 端口描边色用项目主色（Naive UI 默认绿 #18a058）。
const THEME_COLOR = '#18a058';

export default {
  // 开始节点初始数据
  startData: {
    type: 'start',
    sysData: [
      { field: 'sys.question', name: '用户问题' },
      { field: 'sys.time', name: '当前时间' },
      { field: 'sys.ip', name: '用户IP' },
      { field: 'sys.workflowId', name: '编排ID' },
    ],
  },
  // 意图分类节点初始数据
  purposeData: {
    type: 'purpose',
    cateList: [{ name: '分类1' }],
    modelInfo: { modelId: '', modelName: '', temperature: 0 },
    inputData: [],
    outData: [{ field: 'sys.purposeName', name: '意图分类名' }],
  },
  // llm节点初始数据
  llmData: {
    type: 'llm',
    modelInfo: { modelId: '', modelName: '', temperature: 0 },
    // ★ 默认知识库问答提示词：新建 LLM 节点即预填，用户可改。
    //   systemMsg 约束「只依据知识库、不得编造」；userPrompt 显式引用 {{sys.result}}（上游检索结果）
    //   + {{sys.question}}（用户问题）——配了 userPrompt 后后端走模板分支，必须显式引用召回变量，
    //   否则召回内容进不了 prompt。
    userPrompt:
      '请根据以下知识库信息回答用户问题。\n\n知识库信息：\n{{sys.result}}\n\n用户问题：\n{{sys.question}}',
    memory: 2,
    systemMsg:
      '你是企业知识库助手。请严格根据提供的知识库信息回答用户问题，禁止使用训练数据中的常识或自行编造。若知识库信息不足以回答，请直接回复「知识库中未找到相关内容」，不要臆测或补充。回答应简洁、准确、紧扣知识库原文。',
    // ★ rerank 统一在 LLM 节点做：检索节点只召回候选，由 LLM 节点融合后统一重排取 top。
    // rerankModelId 为空=不重排；topRank=重排后保留的片段数。
    rerankModelId: '',
    topRank: 3,
    outData: [{ field: 'sys.content', name: '生成内容' }],
  },
  // 知识检索节点初始数据
  datasetData: {
    type: 'dataset',
    inputData: [],
    datasets: [],
    topRank: 3, // 向量召回数量（检索返回的候选数），与 rerank 无关，保留
    similarity: 0.2,
    outData: [{ field: 'sys.result', name: '检索结果' }],
  },
  // 知识图谱节点初始数据（绑定知识库 + 文档，检索该文档的知识图谱）
  graphData: {
    type: 'graph',
    inputData: [],
    kbId: '',
    docIds: [],
    topRank: 5,
    outData: [{ field: 'sys.result', name: '检索结果' }],
  },
  // 回复节点的初始数据
  answerData: {
    type: 'answer',
    inputData: [], // Array<{nodeId, field}>：可引用多个上游变量
    answerType: 1,
    answer: '',
    answerTemplate: '', // 多变量聚合模板，{{1}}{{2}} 指代第 n 个变量
    outData: [{ field: 'sys.answer', name: '回复内容' }],
  },
  // 条件分支节点的初始数据
  switchData: {
    type: 'switch',
    inputData: [],
    ifBranch: [{ type: 'if', data: [{ input: [], tips: '', value: '' }], switch: 1 }],
    elseBranch: '',
    outData: [],
  },
  // agent节点的初始数据
  agentData: {
    type: 'agent',
    inputData: [],
    agentId: '',
    agentName: '',
    agentLogo: '',
    outData: [{ field: 'sys.agentContent', name: 'agent输出内容' }],
  },
  // 基础桩点（左右各一）
  ports: {
    groups: {
      leftPorts: {
        position: 'left',
        attrs: {
          circle: {
            style: { visibility: 'hidden' },
            r: 4,
            magnet: true,
            stroke: THEME_COLOR,
            strokeWidth: 1,
            fill: '#fff',
          },
        },
      },
      rightPorts: {
        position: 'right',
        attrs: {
          circle: {
            style: { visibility: 'hidden' },
            r: 4,
            magnet: true,
            stroke: THEME_COLOR,
            strokeWidth: 1,
            fill: '#fff',
          },
        },
      },
    },
    items: [
      { group: 'leftPorts', type: 'input' },
      { group: 'rightPorts', type: 'output' },
    ],
  },
  // 左边桩点
  leftPorts: {
    position: 'left',
    attrs: {
      circle: {
        style: { visibility: 'hidden' },
        r: 4,
        magnet: true,
        stroke: THEME_COLOR,
        strokeWidth: 1,
        fill: '#fff',
      },
    },
  },
  // 条件操作符（与后端 SwitchNode.switchTest 的 tips 编号一一对应）
  switchOptions: [
    { type: 1, label: '为空', value: '' },
    { type: 2, label: '不为空', value: '' },
    { type: 3, label: '包含', value: '' },
    { type: 4, label: '不包含', value: '' },
    { type: 5, label: '等于', value: '' },
    { type: 6, label: '大于等于', value: '' },
    { type: 7, label: '小于', value: '' },
    { type: 8, label: '长度等于', value: '' },
    { type: 9, label: '长度大于等于', value: '' },
    { type: 10, label: '长度大于', value: '' },
    { type: 11, label: '长度小于等于', value: '' },
    { type: 12, label: '长度小于', value: '' },
  ],
};
