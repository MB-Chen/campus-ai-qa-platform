import { ref, Ref, ComputedRef, unref, computed, watch, toRaw, h } from 'vue';
import type { BasicColumn, BasicTableProps } from '../types/table';
import { isEqual, cloneDeep } from 'lodash-es';
import { isArray, isString, isBoolean, isFunction } from '@/utils/is';
import { usePermission } from '@/hooks/web/usePermission';
import { ActionItem } from '@/components/Table';
import { renderEditCell } from '../components/editable';
import { NTooltip, NIcon } from 'naive-ui';
import { FormOutlined } from '@vicons/antd';

export function useColumns(propsRef: ComputedRef<BasicTableProps>) {
  const columnsRef = ref(unref(propsRef).columns) as unknown as Ref<BasicColumn[]>;
  // 修复：使用深拷贝确保cacheColumns是独立的副本
  let cacheColumns = cloneDeep(unref(propsRef).columns);

  const getColumnsRef = computed(() => {
    const columns = cloneDeep(unref(columnsRef));

    handleActionColumn(propsRef, columns);
    if (!columns) return [];
    return columns;
  });

  const { hasPermission } = usePermission();

  function isIfShow(action: ActionItem): boolean {
    const ifShow = action.ifShow;

    let isIfShow = true;

    if (isBoolean(ifShow)) {
      isIfShow = ifShow;
    }
    if (isFunction(ifShow)) {
      isIfShow = ifShow(action);
    }
    return isIfShow;
  }

  const renderTooltip = (trigger, content) => {
    return h(NTooltip, null, {
      trigger: () => trigger,
      default: () => content,
    });
  };

  const getPageColumns = computed(() => {
    const pageColumns = unref(getColumnsRef);
    // 修复：每次都使用深拷贝确保列配置是独立的
    const columns = cloneDeep(pageColumns);
    const result = columns
      .filter((column) => {
        return hasPermission(column.auth as string[]) && isIfShow(column);
      })
      .map((column) => {
        //默认 ellipsis 为true
        column.ellipsis =
          typeof column.ellipsis === 'undefined' ? { tooltip: true } : column.ellipsis;
        const { edit } = column;
        if (edit) {
          column.render = renderEditCell(column);
          if (edit) {
            const title: any = column.title;
            column.title = () => {
              return renderTooltip(
                h('div', { class: 'flex items-center' }, [
                  h('span', { style: { 'margin-right': '5px' } }, title),
                  h(
                    NIcon,
                    {
                      size: 14,
                    },
                    {
                      default: () => h(FormOutlined),
                    }
                  ),
                ]),
                '该列可编辑'
              );
            };
          }
        }
        return column;
      });

    // 修复：确保不会重复添加selection列，但不要移除其他类型的列（如操作列）
    const selectionColumns = result.filter(col => col.type === 'selection');
    if (selectionColumns.length > 1) {
      // 如果有多个selection列，只保留第一个，确保操作列等其他列不被移除
      let firstSelectionFound = false;
      return result.filter(col => {
        if (col.type === 'selection') {
          if (!firstSelectionFound) {
            firstSelectionFound = true;
            return true;
          }
          return false;
        }
        return true;
      });
    }

    return result;
  });

  watch(
    () => unref(propsRef).columns,
    (columns) => {
      // 修复：使用深拷贝确保columnsRef和cacheColumns是独立的副本
      columnsRef.value = cloneDeep(columns);
      cacheColumns = cloneDeep(columns);
    },
    {
      deep: true // 修复：深度监听确保嵌套对象变化也能被捕捉
    }
  );

  function handleActionColumn(propsRef: ComputedRef<BasicTableProps>, columns: BasicColumn[]) {
    const { actionColumn } = unref(propsRef);
    if (!actionColumn) return;

    // 检查是否已经存在 action 列，避免重复添加
    const hasActionColumn = columns.find((col) => col.key === 'action' || col.key === 'actions');
    if (!hasActionColumn) {
      columns.push({
        ...(actionColumn as any),
      });
    }
  }

  //设置
  function setColumns(columnList: string[]) {
    const columns: any[] = cloneDeep(columnList);
    if (!isArray(columns)) return;

    if (!columns.length) {
      columnsRef.value = [];
      return;
    }
    const cacheKeys = cacheColumns.map((item) => item.key);
    //针对拖拽排序
    if (!isString(columns[0])) {
      columnsRef.value = cloneDeep(columns); // 修复：使用深拷贝
    } else {
      const newColumns: any[] = [];
      cacheColumns.forEach((item) => {
        if (columnList.includes(item.key)) {
          newColumns.push(cloneDeep(item)); // 修复：使用深拷贝
        }
      });
      if (!isEqual(cacheKeys, columns)) {
        newColumns.sort((prev, next) => {
          return cacheKeys.indexOf(prev.key) - cacheKeys.indexOf(next.key);
        });
      }
      columnsRef.value = newColumns;
    }
  }

  //获取
  function getColumns(): BasicColumn[] {
    const columns = toRaw(unref(getColumnsRef));
    return columns.map((item) => {
      return { ...item, title: item.title, key: item.key, fixed: item.fixed || undefined };
    });
  }

  //获取原始
  function getCacheColumns(isKey?: boolean): any[] {
    return isKey ? cacheColumns.map((item) => item.key) : cacheColumns;
  }

  //更新原始数据单个字段
  function setCacheColumnsField(key: string | undefined, value: Partial<BasicColumn>) {
    if (!key || !value) {
      return;
    }
    cacheColumns.forEach((item) => {
      if (item.key === key) {
        Object.assign(item, cloneDeep(value)); // 修复：使用深拷贝
        return;
      }
    });
  }

  return {
    getColumnsRef,
    getCacheColumns,
    setCacheColumnsField,
    setColumns,
    getColumns,
    getPageColumns,
  };
}