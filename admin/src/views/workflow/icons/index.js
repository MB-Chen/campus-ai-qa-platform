// 节点图标注册表：把节点 shape 名映射到对应的图标组件 + 名称 + 主题色。
// 不依赖外部 iconfont，统一用 @vicons/antd 的 SVG 图标（项目已装）。
import {
  PlayCircleOutlined,
  ApartmentOutlined,
  RobotOutlined,
  DatabaseOutlined,
  MessageOutlined,
  BranchesOutlined,
  ThunderboltOutlined,
  PartitionOutlined,
} from '@vicons/antd';

export const NODE_ICON_META = {
  'start-node': { icon: PlayCircleOutlined, name: '开始', color: '#18a058' },
  'purpose-node': { icon: ApartmentOutlined, name: '意图分类', color: '#f79009' },
  'llm-node': { icon: ThunderboltOutlined, name: 'LLM', color: '#6172f3' },
  'dataset-node': { icon: DatabaseOutlined, name: '知识检索', color: '#6172f3' },
  'graph-node': { icon: PartitionOutlined, name: '知识图谱', color: '#722ed1' },
  'answer-node': { icon: MessageOutlined, name: '回复', color: '#06ae4d' },
  'switch-node': { icon: BranchesOutlined, name: '条件分支', color: '#f79009' },
  'agent-node': { icon: RobotOutlined, name: 'Agent', color: '#17b26a' },
};

// 兼容原 spark-x-master 的 iconComponent(name) 调用：返回一个内联渲染的函数式组件包装。
export function iconComponent(name) {
  return NODE_ICON_META[name] || null;
}
