/**
 * 静态菜单（写死，替代后端动态下发）。
 *
 * 由 generator.ts 解析为路由树，asyncImportRoute 把 component 字符串解析成真实懒加载组件：
 *   - 'LAYOUT'   → 主布局壳（@/layout/index.vue），保证侧边栏始终在
 *   - '/xxx/yyy' → 匹配 src/views/xxx/yyy.vue（去掉扩展名）
 *
 * 顶层 path:'#' 时，generator 按 name 生成路由（小写），如 name 'knowledge' → /knowledge。
 *
 * 一级可点击页面（主控台/新对话/知识库/智能体）用 LAYOUT + 单子项结构：
 *   菜单组件对「仅一个非隐藏子项 + alwaysShow:false」的父菜单会扁平化，
 *   父项直接当作叶子项显示，点击跳到唯一子路由（侧边栏保留）。
 * 系统设置有多个二级菜单，children 多项，正常展开。
 *
 * 图标必须命中 src/router/icons.ts 的 constantRouterIcon（仅顶层菜单显示图标）。
 */

// 新对话：LAYOUT + 单子项，扁平化（占位页，后续替换）
const chatMenu = {
  path: '#',
  name: 'chat',
  component: 'LAYOUT',
  meta: {
    title: '新对话',
    icon: 'MessageOutlined',
    alwaysShow: false,
  },
  children: [
    {
      path: 'index',
      name: 'chat_index',
      component: '/chat/index',
      meta: {
        title: '新对话',
      },
    },
    {
      // 会话详情页（动态路由），隐藏于侧边栏，继承 LAYOUT 外壳
      path: ':id',
      name: 'chat_session',
      component: '/chat/session',
      meta: {
        title: '会话',
        hidden: true,
      },
    },
  ],
};

// 知识库：LAYOUT + 单子项，扁平化（列表页 + 隐藏的详情页）
const knowledgeMenu = {
  path: '#',
  name: 'knowledge',
  component: 'LAYOUT',
  meta: {
    title: '知识库',
    icon: 'BookOutlined',
    alwaysShow: false,
  },
  children: [
    {
      path: 'index',
      name: 'kb_kb',
      component: '/knowledge/index',
      meta: {
        title: '知识库',
      },
    },
    {
      path: 'detail',
      name: 'kbdetail_detail',
      component: '/knowledge/detail',
      meta: {
        title: '知识库详情',
        hidden: true,
      },
    },
  ],
};

// 智能体：LAYOUT + 单子项，扁平化
const agentMenu = {
  path: '#',
  name: 'agent',
  component: 'LAYOUT',
  meta: {
    title: '智能体',
    icon: 'AppstoreOutlined',
    alwaysShow: false,
  },
  children: [
    {
      path: 'index',
      name: 'agent_agent',
      component: '/agent/index',
      meta: {
        title: '智能体',
      },
    },
  ],
};

// 编排：LAYOUT + 单子项，扁平化（列表页 + 隐藏的编辑器页）
const workflowMenu = {
  path: '#',
  name: 'workflow',
  component: 'LAYOUT',
  meta: {
    title: '编排',
    icon: 'DeploymentUnitOutlined',
    alwaysShow: false,
  },
  children: [
    {
      path: 'index',
      name: 'workflow_workflow',
      component: '/workflow/index',
      meta: {
        title: '编排',
      },
    },
    {
      path: 'edit',
      name: 'workflow_edit',
      component: '/workflow/edit',
      meta: {
        title: '编排设计',
        hidden: true,
        // 全屏编辑器：隐藏框架底部版权 + 左下角用户/操作区，避免与编辑器自身的
        // 底部工具栏（缩放/居中/添加）重叠遮挡
        fullScreen: true,
      },
    },
  ],
};

// 系统设置：已迁移至左下角用户下拉菜单（与「修改密码/退出登录」合并），
// 故在这里设为 hidden —— 路由仍注册（/system/xxx 可跳转），但侧边栏不再展示。
const systemMenu = {
  path: '#',
  name: 'system',
  component: 'LAYOUT',
  meta: {
    title: '系统设置',
    icon: 'SettingOutlined',
    hidden: true,
    // 多子项默认展开，不设 alwaysShow:false
  },
  children: [
    {
      path: 'model',
      name: 'aimodel_aimodel',
      component: '/ai/model/index',
      meta: {
        title: '配置模型',
      },
    },
    {
      path: 'intent',
      name: 'intent_intent',
      component: '/knowledge/intent/index',
      meta: {
        title: '意图路由',
      },
    },
    {
      path: 'service',
      name: 'aiservice_aiservice',
      component: '/ai/service/index',
      meta: {
        title: '解析引擎',
      },
    },
    {
      path: 'mcp',
      name: 'mcp_mcp',
      component: '/ai/mcp/index',
      meta: {
        title: 'MCP服务',
      },
    },
    {
      path: 'sample-query',
      name: 'samplequery_sample-query',
      component: '/knowledge/sampleQuery/index',
      meta: {
        title: '样例查询',
      },
    },
    {
      path: 'knowledge-graph',
      name: 'knowledgegraph_knowledge-graph',
      component: '/knowledge/graph/index',
      meta: {
        title: '知识图谱',
      },
    },
  ],
};

export const STATIC_MENUS = [
  chatMenu,
  knowledgeMenu,
  agentMenu,
  workflowMenu,
  systemMenu,
];
