import { memo, useEffect, useMemo, useState } from 'react';
import { App, Input, InputNumber, Select, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getPayCalendarApi, savePayCalendarApi, type PayWorkCalendar } from '@/api/payroll';

type CountKey =
  'dualRest' | 'dualWork' | 'dualPay' | 'singleRest' | 'singleWork' | 'singlePay' | 'altRest' | 'altWork' | 'altPay';

const GROUPS = [
  { key: 'dual', title: '双休', header: '#1d4ed8', cell: '#eef2ff' },
  { key: 'single', title: '单休', header: '#0891b2', cell: '#ecfeff' },
  { key: 'alt', title: '大小周', header: '#65a30d', cell: '#f7fee7' },
] as const;

const COUNT_TITLES = [
  ['Rest', '休息天数'],
  ['Work', '出勤天数'],
  ['Pay', '计薪天数'],
] as const;

function sum(rows: PayWorkCalendar[], key: keyof PayWorkCalendar) {
  return rows.reduce((total, row) => total + Number(row[key] ?? 0), 0);
}

const PayrollCalendarPage = memo(function PayrollCalendarPage() {
  const { message } = App.useApp();
  const [year, setYear] = useState(2026);
  const [rows, setRows] = useState<PayWorkCalendar[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = async (target: number) => {
    setLoading(true);
    try {
      setRows((await getPayCalendarApi(target)) ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(year);
  }, [year]);

  const patch = (month: number, field: CountKey | 'holidayName' | 'holidayDays', value: string | number | null) => {
    setRows((current) =>
      current.map((row) => {
        if (row.month !== month) return row;
        const next = { ...row };
        if (field === 'holidayName') {
          next.holidayName = String(value ?? '');
          return next;
        }
        const number = Number(value ?? 0);
        if (field === 'holidayDays') {
          next.holidayDays = number;
          next.dualPay = next.dualWork + number;
          next.singlePay = next.singleWork + number;
          next.altPay = next.altWork + number;
          return next;
        }
        next[field] = number;
        const group = field.startsWith('dual') ? 'dual' : field.startsWith('single') ? 'single' : 'alt';
        const restKey = `${group}Rest` as CountKey;
        const workKey = `${group}Work` as CountKey;
        const payKey = `${group}Pay` as CountKey;
        if (field === restKey) {
          next[workKey] = Math.max(0, next.calendarDays - number);
          next[payKey] = next[workKey] + next.holidayDays;
        } else if (field === workKey) {
          next[restKey] = Math.max(0, next.calendarDays - number);
          next[payKey] = number + next.holidayDays;
        }
        return next;
      }),
    );
  };

  const dataSource = useMemo(() => {
    if (!rows.length) return [];
    const total = {
      year,
      month: 0,
      calendarDays: sum(rows, 'calendarDays'),
      holidayName: '',
      holidayDays: sum(rows, 'holidayDays'),
      dualRest: sum(rows, 'dualRest'),
      dualWork: sum(rows, 'dualWork'),
      dualPay: sum(rows, 'dualPay'),
      singleRest: sum(rows, 'singleRest'),
      singleWork: sum(rows, 'singleWork'),
      singlePay: sum(rows, 'singlePay'),
      altRest: sum(rows, 'altRest'),
      altWork: sum(rows, 'altWork'),
      altPay: sum(rows, 'altPay'),
    } satisfies PayWorkCalendar;
    return [...rows, total];
  }, [rows, year]);

  const numberColumn = (key: CountKey, title: string, color: string): ColumnsType<PayWorkCalendar>[number] => ({
    title,
    dataIndex: key,
    width: 88,
    align: 'center',
    onHeaderCell: () => ({ style: { background: color, color: '#fff', textAlign: 'center' } }),
    onCell: (record) => ({ style: { background: record.month === 0 ? '#f8fafc' : `${color}14` } }),
    render: (value: number, record) =>
      record.month === 0 ? (
        <span className='font-semibold'>{value}</span>
      ) : (
        <InputNumber
          min={0}
          max={31}
          size='small'
          value={value}
          className='w-[72px]'
          onChange={(next) => patch(record.month, key, next)}
        />
      ),
  });

  const columns: ColumnsType<PayWorkCalendar> = [
    {
      title: '月份',
      dataIndex: 'month',
      width: 72,
      fixed: 'left',
      align: 'center',
      render: (month: number) => (month === 0 ? '合计' : `${month}月`),
    },
    {
      title: '日历天数',
      dataIndex: 'calendarDays',
      width: 88,
      align: 'center',
    },
    {
      title: '法定假',
      children: [
        {
          title: '名称',
          dataIndex: 'holidayName',
          width: 110,
          align: 'center',
          render: (name: string, record) =>
            record.month === 0 ? (
              ''
            ) : (
              <Input
                size='small'
                maxLength={20}
                value={name}
                placeholder='无'
                onChange={(event) => patch(record.month, 'holidayName', event.target.value)}
              />
            ),
        },
        {
          title: '天数',
          dataIndex: 'holidayDays',
          width: 80,
          align: 'center',
          render: (days: number, record) =>
            record.month === 0 ? (
              <span className='font-semibold'>{days}</span>
            ) : (
              <InputNumber
                min={0}
                max={31}
                size='small'
                value={days}
                className='w-[72px]'
                onChange={(next) => patch(record.month, 'holidayDays', next)}
              />
            ),
        },
      ],
    },
    ...GROUPS.map((group) => ({
      title: group.title,
      onHeaderCell: () => ({ style: { background: group.header, color: '#fff', textAlign: 'center' as const } }),
      children: COUNT_TITLES.map(([suffix, title]) =>
        numberColumn(`${group.key}${suffix}` as CountKey, title, group.header),
      ),
    })),
  ];

  const save = async () => {
    setSaving(true);
    try {
      await savePayCalendarApi(year, rows);
      message.success('已保存');
      await load(year);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className='p-4'>
      <div className='mb-3 flex items-center justify-between gap-3'>
        <div>
          <div className='text-base font-medium'>计薪天数</div>
          <div className='mt-1 text-sm text-slate-500'>
            2026 年初始数据按国务院办公厅国办发明电〔2025〕7号和《全国年节及纪念日放假办法》生成。法定假 13
            天；双休含调休，单休是周日加法定假日，大小周按单双周周六轮休。日历天数固定，其余格子可以改。改休息或出勤时，计薪天数会按出勤加法定假重算，计薪天数本身也可以直接改。
          </div>
        </div>
        <div className='flex shrink-0 items-center gap-2'>
          <Select
            value={year}
            className='w-28'
            options={[{ value: 2026, label: '2026年' }]}
            onChange={setYear}
          />
          <PermissionButton
            perm='payroll:calendar:edit'
            type='primary'
            loading={saving}
            onClick={() => void save()}
          >
            保存
          </PermissionButton>
        </div>
      </div>
      <Table<PayWorkCalendar>
        rowKey={(row) => String(row.month)}
        size='small'
        bordered
        loading={loading}
        pagination={false}
        columns={columns}
        dataSource={dataSource}
        scroll={{ x: 1400 }}
        locale={{ emptyText: '这一年还没有国家放假安排，暂不能编辑' }}
      />
    </div>
  );
});

export default PayrollCalendarPage;
