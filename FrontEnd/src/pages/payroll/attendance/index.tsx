import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import { App, Tabs, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import {
  getPayPullStatusApi,
  pagePayAttendanceApi,
  pagePayAttendanceSummaryApi,
  rebuildPayAttendanceSummaryApi,
  syncPayAttendanceApi,
  type PayAttendance,
  type PayAttendanceSummary,
  type PayMonthPullStatus,
} from '@/api/payroll';

const CHECK_TYPE: Record<string, string> = { OnDuty: '上班', OffDuty: '下班' };
const TIME_RESULT = [
  { label: '正常', value: 'Normal', color: 'success' },
  { label: '迟到', value: 'Late', color: 'warning' },
  { label: '严重迟到', value: 'SeriousLate', color: 'error' },
  { label: '早退', value: 'Early', color: 'warning' },
  { label: '旷工迟到', value: 'Absenteeism', color: 'error' },
  { label: '未打卡', value: 'NotSigned', color: 'default' },
];

function pullLine(label: string, item?: { recordCount?: number; syncedAt?: string }) {
  if (!item?.syncedAt) return `${label}未同步`;
  return `${label}已于 ${item.syncedAt} 同步 ${item.recordCount ?? 0} 条`;
}

const PayrollAttendancePage = memo(function PayrollAttendancePage() {
  const { message, modal } = App.useApp();
  const summaryRef = useRef<ActionType>(null);
  const detailRef = useRef<ActionType>(null);
  const summaryFormRef = useRef<ProFormInstance>(undefined);
  const detailFormRef = useRef<ProFormInstance>(undefined);
  const [tab, setTab] = useState('summary');
  const [syncing, setSyncing] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const [status, setStatus] = useState<PayMonthPullStatus | null>(null);

  const currentPeriod = () => {
    const form = tab === 'summary' ? summaryFormRef.current : detailFormRef.current;
    const value = form?.getFieldValue('period');
    return dayjs(value || undefined).format('YYYY-MM');
  };

  const reloadAll = () => {
    summaryRef.current?.reload();
    detailRef.current?.reload();
  };

  const sync = () => {
    const period = currentPeriod();
    modal.confirm({
      title: `同步 ${period} 的钉钉考勤？`,
      content: '会覆盖本系统里该月已保存的考勤，并重算月度汇总（事假1/2、旷工、缺卡、年假、婚丧产、调休）。',
      okText: '开始同步',
      onOk: async () => {
        setSyncing(true);
        try {
          const result = await syncPayAttendanceApi(period);
          message.success(result.message || '考勤已同步');
          reloadAll();
        } finally {
          setSyncing(false);
        }
      },
    });
  };

  const rebuild = () => {
    const period = currentPeriod();
    modal.confirm({
      title: `按已同步打卡重算 ${period} 月度汇总？`,
      content: '不重新拉钉钉打卡，只根据本地打卡与假期接口重算事假/旷工/缺卡/假期字段。',
      okText: '重算汇总',
      onOk: async () => {
        setRebuilding(true);
        try {
          const count = await rebuildPayAttendanceSummaryApi(period);
          message.success(`已重算 ${count} 人汇总`);
          summaryRef.current?.reload();
        } finally {
          setRebuilding(false);
        }
      },
    });
  };

  const summaryColumns: ProColumnType<PayAttendanceSummary>[] = [
    {
      title: '月份',
      dataIndex: 'period',
      valueType: 'dateMonth',
      initialValue: dayjs(),
      hideInTable: true,
      fieldProps: { format: 'YYYY-MM', allowClear: false },
    },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'deptName', width: 140 },
    { title: '迟到/早退', dataIndex: 'lateEarlyCount', width: 100, search: false },
    { title: '事假1', dataIndex: 'personal1Count', width: 80, search: false },
    { title: '事假2', dataIndex: 'personal2Count', width: 80, search: false },
    { title: '旷工', dataIndex: 'absentCount', width: 70, search: false },
    { title: '缺卡', dataIndex: 'missingCardCount', width: 70, search: false },
    {
      title: '年假',
      dataIndex: 'annualLeaveDays',
      width: 80,
      search: false,
      render: (_, row) => Number(row.annualLeaveDays || 0).toFixed(1),
    },
    {
      title: '婚/丧/产假',
      dataIndex: 'specialLeaveDays',
      width: 110,
      search: false,
      render: (_, row) => Number(row.specialLeaveDays || 0).toFixed(1),
    },
    {
      title: '本月实休',
      dataIndex: 'actualRestDays',
      width: 100,
      search: false,
      render: (_, row) => Number(row.actualRestDays || 0).toFixed(1),
    },
    {
      title: '剩余调休',
      dataIndex: 'remainingCompDays',
      width: 100,
      search: false,
      render: (_, row) => Number(row.remainingCompDays || 0).toFixed(1),
    },
  ];

  const detailColumns: ProColumnType<PayAttendance>[] = [
    {
      title: '月份',
      dataIndex: 'period',
      valueType: 'dateMonth',
      initialValue: dayjs(),
      hideInTable: true,
      fieldProps: { format: 'YYYY-MM', allowClear: false },
    },
    { title: '姓名', dataIndex: 'employeeName', width: 120 },
    { title: '部门', dataIndex: 'deptName', width: 160 },
    { title: '工作日', dataIndex: 'workDate', width: 120, search: false },
    {
      title: '班次',
      dataIndex: 'checkType',
      width: 90,
      search: false,
      render: (_, row) => CHECK_TYPE[row.checkType] || row.checkType,
    },
    {
      title: '结果',
      dataIndex: 'timeResult',
      width: 110,
      valueType: 'select',
      fieldProps: { options: TIME_RESULT, allowClear: true },
      render: (_, row) => {
        const item = TIME_RESULT.find((option) => option.value === row.timeResult);
        return <Tag color={item?.color}>{item?.label || row.timeResult}</Tag>;
      },
    },
    { title: '打卡时间', dataIndex: 'userCheckTime', width: 170, search: false },
    { title: '应打卡', dataIndex: 'baseCheckTime', width: 170, search: false },
  ];

  const ready = Boolean(status?.attendance?.syncedAt && status?.bulletin?.syncedAt);
  const toolbar = [
    <PermissionButton
      key='sync'
      perm='payroll:attendance:sync'
      type='primary'
      loading={syncing}
      onClick={sync}
    >
      同步钉钉考勤
    </PermissionButton>,
    <PermissionButton
      key='rebuild'
      perm='payroll:attendance:sync'
      loading={rebuilding}
      onClick={rebuild}
    >
      重算月度汇总
    </PermissionButton>,
  ];

  return (
    <>
      <p className={`mb-3 text-sm ${ready ? 'text-neutral-500' : 'text-amber-600'}`}>
        核算该月工资前，先同步钉钉考勤和行政通报。{pullLine('考勤', status?.attendance)}；
        {pullLine('行政通报', status?.bulletin)}
        。汇总区分事假1/2、旷工、迟到早退、缺卡、年假、婚丧产、本月实休、剩余调休。
      </p>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'summary',
            label: '月度汇总',
            children: (
              <BaseProTable<PayAttendanceSummary>
                rowKey='id'
                actionRef={summaryRef}
                formRef={summaryFormRef}
                columns={summaryColumns}
                scroll={{ x: 1200 }}
                headerTitle='考勤月度汇总'
                toolBarRender={() => toolbar}
                request={async (params) => {
                  const query = tools.handleSearchParams(params);
                  if (query.period) query.period = dayjs(query.period).format('YYYY-MM');
                  const [res, pull] = await Promise.all([
                    pagePayAttendanceSummaryApi(query),
                    query.period ? getPayPullStatusApi(String(query.period)) : Promise.resolve(null),
                  ]);
                  setStatus(pull);
                  return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
                }}
              />
            ),
          },
          {
            key: 'detail',
            label: '打卡明细',
            children: (
              <BaseProTable<PayAttendance>
                rowKey='id'
                actionRef={detailRef}
                formRef={detailFormRef}
                columns={detailColumns}
                scroll={{ x: 980 }}
                headerTitle='考勤打卡明细'
                toolBarRender={() => toolbar}
                request={async (params) => {
                  const query = tools.handleSearchParams(params);
                  if (query.period) query.period = dayjs(query.period).format('YYYY-MM');
                  const [res, pull] = await Promise.all([
                    pagePayAttendanceApi(query),
                    query.period ? getPayPullStatusApi(String(query.period)) : Promise.resolve(null),
                  ]);
                  setStatus(pull);
                  return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
                }}
              />
            ),
          },
        ]}
      />
    </>
  );
});

export default PayrollAttendancePage;
