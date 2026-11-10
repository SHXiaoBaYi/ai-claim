import type { ProColumns } from '@ant-design/pro-components';

/** 业务列表通用：创建时间范围筛选项 */
export function createTimeRangeColumn<T extends Record<string, any>>(
  options?: Partial<ProColumns<T>>,
): ProColumns<T> {
  return {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateRange',
    hideInTable: true,
    hideInForm: true,
    search: {
      transform: (value) => ({
        createTimeStart: value?.[0],
        createTimeEnd: value?.[1],
      }),
    },
    ...options,
  };
}

/** 表格列展示用的创建时间（不可搜，与上面 search 列配对） */
export function createTimeDisplayColumn<T extends Record<string, any>>(
  options?: Partial<ProColumns<T>>,
): ProColumns<T> {
  return {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateTime',
    width: 170,
    search: false,
    hideInForm: true,
    ...options,
  };
}
