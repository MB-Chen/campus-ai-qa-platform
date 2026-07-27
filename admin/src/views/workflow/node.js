// X6 节点配置工厂 + Vue 组件注册。
// 每个 XxxNode(x,y,no) 返回 X6 addNode 所需的节点配置（shape/ports/data）。
// 末尾把 7 个 .vue 组件注册成 x6-vue-shape 的 shape。
import { register } from '@antv/x6-vue-shape';
import initConfig from '@/views/workflow/initConfig.js';

const THEME_COLOR = '#18a058';

// 右侧桩点（absolute 定位，给 purpose/switch 用，支持多分支）
const rightPortAttrs = {
  circle: {
    style: { visibility: 'hidden' },
    r: 4,
    magnet: true,
    stroke: THEME_COLOR,
    strokeWidth: 1,
    fill: '#fff',
  },
};

export default {
  // 开始节点
  startNode: (x, y) => {
    return {
      x,
      y,
      shape: 'start-node',
      width: 230,
      height: 40,
      data: {
        pages: 'start',
        checked: false,
        portsVisible: false,
        sysData: initConfig.startData.sysData,
        userData: [],
        outData: initConfig.startData.sysData,
      },
      ports: {
        groups: {
          rightPorts: {
            position: 'right',
            attrs: rightPortAttrs,
          },
        },
        items: [{ group: 'rightPorts', type: 'output' }],
      },
    };
  },
  // 意图分类
  purposeNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'purpose-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'purpose',
        checked: false,
        portsVisible: false,
        ...initConfig.purposeData,
      },
      ports: {
        groups: {
          leftPorts: { ...initConfig.leftPorts },
          rightPorts: {
            attrs: rightPortAttrs,
            position: { name: 'absolute' },
          },
        },
        items: [
          { group: 'leftPorts', type: 'input' },
          { group: 'rightPorts', args: { x: 230, y: 100 }, type: 'output' },
        ],
      },
    };
  },
  // LLM节点
  llmNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'llm-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'llm',
        checked: false,
        portsVisible: false,
        ...initConfig.llmData,
      },
      ports: { ...initConfig.ports },
    };
  },
  // 知识检索节点
  datasetNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'dataset-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'dataset',
        checked: false,
        portsVisible: false,
        ...initConfig.datasetData,
      },
      ports: { ...initConfig.ports },
    };
  },
  // 回复节点
  answerNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'answer-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'answer',
        checked: false,
        portsVisible: false,
        ...initConfig.answerData,
      },
      ports: { ...initConfig.ports },
    };
  },
  // 智能体节点
  agentNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'agent-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'agent',
        checked: false,
        portsVisible: false,
        ...initConfig.agentData,
      },
      ports: { ...initConfig.ports },
    };
  },
  // 条件分支
  switchNode: (x, y, no) => {
    return {
      x,
      y,
      shape: 'switch-node',
      width: 230,
      height: 40,
      data: {
        no,
        pages: 'switch',
        checked: false,
        portsVisible: false,
        ...initConfig.switchData,
      },
      ports: {
        groups: {
          leftPorts: { ...initConfig.leftPorts },
          rightPorts: {
            attrs: rightPortAttrs,
            position: { name: 'absolute' },
          },
        },
        items: [
          { group: 'leftPorts', type: 'input' },
          { group: 'rightPorts', args: { x: 230, y: 130 }, type: 'output' },
          { group: 'rightPorts', args: { x: 230, y: 80 }, type: 'output' },
        ],
      },
    };
  },
};

// 注册 X6 shape → Vue 组件
import Start from './node/start.vue';
import Purpose from './node/purpose.vue';
import Llm from './node/llm.vue';
import Dataset from './node/dataset.vue';
import Answer from './node/answer.vue';
import Switch from './node/switch.vue';
import Agent from './node/agent.vue';

register({ shape: 'start-node', width: 100, height: 100, component: Start });
register({ shape: 'purpose-node', width: 100, height: 100, component: Purpose });
register({ shape: 'llm-node', width: 100, height: 100, component: Llm });
register({ shape: 'dataset-node', width: 100, height: 100, component: Dataset });
register({ shape: 'answer-node', width: 100, height: 100, component: Answer });
register({ shape: 'switch-node', width: 100, height: 100, component: Switch });
register({ shape: 'agent-node', width: 100, height: 100, component: Agent });
