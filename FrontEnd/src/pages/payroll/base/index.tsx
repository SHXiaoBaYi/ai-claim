import { memo, useEffect, useState } from 'react';
import { App, Card, InputNumber, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getPayLateRulesApi, savePayLateRuleApi, type PayLateRule } from '@/api/payroll';

type AmountField =
  | 'allowCount'
  | 'personal1Amount'
  | 'personal2Amount'
  | 'amtVerbalPraise'
  | 'amtCommendation'
  | 'amtMerit'
  | 'amtMajorMerit'
  | 'amtVerbalCritic'
  | 'amtBulletinCritic'
  | 'amtDemerit'
  | 'amtMajorDemerit';

const PayrollBasePage = memo(function PayrollBasePage() {
  const { message } = App.useApp();
  const [rows, setRows] = useState<PayLateRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setRows((await getPayLateRulesApi()) ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const patch = (id: number, field: AmountField, value: number | null) => {
    setRows((current) => current.map((row) => (row.id === id ? { ...row, [field]: Number(value ?? 0) } : row)));
  };

  const save = async () => {
    setSaving(true);
    try {
      for (const row of rows) {
        await savePayLateRuleApi({
          id: row.id,
          allowCount: row.allowCount,
          personal1Amount: row.personal1Amount,
          personal2Amount: row.personal2Amount,
          amtVerbalPraise: row.amtVerbalPraise,
          amtCommendation: row.amtCommendation,
          amtMerit: row.amtMerit,
          amtMajorMerit: row.amtMajorMerit,
          amtVerbalCritic: row.amtVerbalCritic,
          amtBulletinCritic: row.amtBulletinCritic,
          amtDemerit: row.amtDemerit,
          amtMajorDemerit: row.amtMajorDemerit,
        });
      }
      message.success('职级规则已保存');
      await load();
    } finally {
      setSaving(false);
    }
  };

  const moneyColumn = (title: string, field: AmountField, width = 130): ColumnsType<PayLateRule>[number] => ({
    title,
    dataIndex: field,
    width,
    render: (value: number, row) => (
      <InputNumber
        min={0}
        precision={field === 'allowCount' ? 0 : 2}
        max={field === 'allowCount' ? 31 : undefined}
        value={value}
        className='w-28'
        addonAfter={field === 'allowCount' ? undefined : '元'}
        onChange={(next) => patch(row.id, field, next)}
      />
    ),
  });

  const columns: ColumnsType<PayLateRule> = [
    { title: '职级', dataIndex: 'gradeName', width: 100, fixed: 'left' },
    moneyColumn('允许次数 N', 'allowCount', 120),
    moneyColumn('事假1整月', 'personal1Amount'),
    moneyColumn('事假2每次', 'personal2Amount'),
    moneyColumn('口头表扬', 'amtVerbalPraise'),
    moneyColumn('通报嘉奖', 'amtCommendation'),
    moneyColumn('记功', 'amtMerit'),
    moneyColumn('记大功', 'amtMajorMerit'),
    moneyColumn('口头批评', 'amtVerbalCritic'),
    moneyColumn('通报批评', 'amtBulletinCritic'),
    moneyColumn('记过', 'amtDemerit'),
    moneyColumn('记大过', 'amtMajorDemerit'),
  ];

  return (
    <Card
      title='职级规则'
      loading={loading}
      extra={
        <PermissionButton
          perm='payroll:base:edit'
          type='primary'
          loading={saving}
          onClick={() => void save()}
        >
          保存规则
        </PermissionButton>
      }
    >
      <p className='mb-4 text-sm text-neutral-500'>
        按职级配置。事假：当月迟到+早退次数 C≥1 扣事假1整月定额（超门槛仍扣）；C&gt;N 再记 (C−N)
        次事假2。行政通报八类：正文没写明金额时按对应单价×次数；正文写了奖/罚多少元则用明文。
      </p>
      <Table<PayLateRule>
        rowKey='id'
        size='middle'
        pagination={false}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1600 }}
        locale={{ emptyText: '还没有职级，请先在职级页新增' }}
      />
    </Card>
  );
});

export default PayrollBasePage;
