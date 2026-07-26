import type { PropType } from 'vue';
import { propTypes } from '@/utils/propTypes';
import { BasicColumn } from './types/table';
import { NDataTable } from 'naive-ui';
export const basicProps = {
  // 明确排除可能导致选择列重复的属性
  // 只保留必要的属性，避免继承 NDataTable 的所有 props
  title: {
    type: String,
    default: null,
  },
  titleTooltip: {
    type: String,
    default: null,
  },
  size: {
    type: String,
    default: 'medium',
  },
  dataSource: {
    type: [Array, Object],
    default: () => [],
  },
  columns: {
    type: [Array] as PropType<BasicColumn[]>,
    default: () => [],
    required: true,
  },
  beforeRequest: {
    type: Function as PropType<(...arg: any[]) => void | Promise<any>>,
    default: null,
  },
  request: {
    type: Function as PropType<(...arg: any[]) => Promise<any>>,
    default: null,
  },
  afterRequest: {
    type: Function as PropType<(...arg: any[]) => void | Promise<any>>,
    default: null,
  },
  rowKey: {
    type: [String, Function] as PropType<string | ((record) => string)>,
    default: undefined,
  },
  pagination: {
    type: [Object, Boolean],
    default: () => {},
  },
  // 选择相关的属性
  checkedRowKeys: {
    type: Array as PropType<Array<string | number>>,
    default: undefined, // 外部控制时使用，内部控制时为undefined
  },
  rowSelection: {
    type: [Object, Boolean] as PropType<any>,
    // 修复：使用工厂函数返回新对象，避免引用重复问题
    default: () => null,
  },
  //废弃
  showPagination: {
    type: [String, Boolean],
    default: 'auto',
  },
  actionColumn: {
    type: Object as PropType<BasicColumn>,
    default: null,
  },
  canResize: propTypes.bool.def(true),
  resizeHeightOffset: propTypes.number.def(0),
  striped: propTypes.bool.def(false),
  showToolbar: propTypes.bool.def(true),
  maxHeight: propTypes.oneOfType([Number, String]).def(undefined),
};
