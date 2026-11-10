import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import { App, Modal, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import {
  getPayPullStatusApi,
  pagePayBulletinsApi,
  syncPayBulletinApi,
  type PayBulletin,
  type PayMonthPullStatus,
} from '@/api/payroll';

const KIND_OPTIONS = [
  { label: '口头表扬', value: '口头表扬', color: 'success' },
  { label: '通报嘉奖', value: '通报嘉奖', color: 'success' },
  { label: '记功', value: '记功', color: 'success' },
  { label: '记大功', value: '记大功', color: 'success' },
  { label: '口头批评', value: '口头批评', color: 'error' },
  { label: '通报批评', value: '通报批评', color: 'error' },
  { label: '记过', value: '记过', color: 'error' },
  { label: '记大过', value: '记大过', color: 'error' },
  { label: '通报', value: '通报', color: 'processing' },
];

function pullLine(label: string, item?: { recordCount?: number; syncedAt?: string }) {
  if (!item?.syncedAt) return `${label}未同步`;
  return `${label}已于 ${item.syncedAt} 同步 ${item.recordCount ?? 0} 条`;
}

const PayrollBulletinPage = memo(function PayrollBulletinPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const formRef = useRef<ProFormInstance>(undefined);
  const [syncing, setSyncing] = useState(false);
  const [status, setStatus] = useState<PayMonthPullStatus | null>(null);
  const [reading, setReading] = useState<PayBulletin | null>(null);

  const currentPeriod = () => {
    const value = formRef.current?.getFieldValue('period');
    return dayjs(value || undefined).format('YYYY-MM');
  };

  const sync = () => {
    const period = currentPeriod();
    modal.confirm({
      title: `同步 ${period} 的行政通报？`,
      content: '从钉钉公告拉取该月行政通报，并按口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过生成奖惩。',
      okText: '开始同步',
      onOk: async () => {
        setSyncing(true);
        try {
          const result = await syncPayBulletinApi(period);
          message.success(result.message || '行政通报已同步');
          actionRef.current?.reload();
        } finally {
          setSyncing(false);
        }
      },
    });
  };

  const columns: ProColumnType<PayBulletin>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      search: false,
      render: (_, row) => <ActionButtons items={[{ key: 'view', label: '查看', onClick: () => setReading(row) }]} />,
    },
    {
      title: '月份',
      dataIndex: 'period',
      valueType: 'dateMonth',
      initialValue: dayjs(),
      hideInTable: true,
      fieldProps: { format: 'YYYY-MM', allowClear: false },
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'bulletinKind',
      width: 110,
      valueType: 'select',
      fieldProps: { options: KIND_OPTIONS, allowClear: true },
      render: (_, row) => {
        const item = KIND_OPTIONS.find((option) => option.value === row.bulletinKind);
        return <Tag color={item?.color || 'default'}>{row.bulletinKind}</Tag>;
      },
    },
    { title: '分类', dataIndex: 'categoryName', width: 140, search: false },
    { title: '发布人', dataIndex: 'author', width: 100, search: false },
    { title: '发布时间', dataIndex: 'publishTime', width: 170, search: false },
  ];

  const ready = Boolean(status?.attendance?.syncedAt && status?.bulletin?.syncedAt);

  return (
    <>
      <p className={`mb-3 text-sm ${ready ? 'text-neutral-500' : 'text-amber-600'}`}>
        核算该月工资前，先同步钉钉考勤和行政通报。{pullLine('考勤', status?.attendance)}；
        {pullLine('行政通报', status?.bulletin)}。
      </p>
      <BaseProTable<PayBulletin>
        rowKey='id'
        actionRef={actionRef}
        formRef={formRef}
        columns={columns}
        scroll={{ x: 900 }}
        headerTitle='行政通报'
        toolBarRender={() => [
          <PermissionButton
            key='sync'
            perm='payroll:bulletin:sync'
            type='primary'
            loading={syncing}
            onClick={sync}
          >
            同步钉钉通报
          </PermissionButton>,
        ]}
        request={async (params) => {
          const query = tools.handleSearchParams(params);
          if (query.period) query.period = dayjs(query.period).format('YYYY-MM');
          const [res, pull] = await Promise.all([
            pagePayBulletinsApi(query),
            query.period ? getPayPullStatusApi(String(query.period)) : Promise.resolve(null),
          ]);
          setStatus(pull);
          return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
        }}
      />
      <Modal
        title={reading?.title || '通报'}
        open={Boolean(reading)}
        footer={null}
        width={720}
        onCancel={() => setReading(null)}
        destroyOnHidden
      >
        <p className='mb-3 text-sm text-neutral-500'>
          {reading?.author || '未知发布人'} · {reading?.publishTime || ''} · {reading?.receiverText || '未注明接收范围'}{' '}
          · {reading?.bulletinKind}
        </p>
        <div className='max-h-[60vh] overflow-auto text-sm whitespace-pre-wrap'>
          {reading?.plainContent || '没有正文'}
        </div>
      </Modal>
    </>
  );
});

export default PayrollBulletinPage;
