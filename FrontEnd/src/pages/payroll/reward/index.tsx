import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import { App, DatePicker, Form, Input, InputNumber, Modal, Select, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import {
  deletePayRewardApi,
  getPayDeptsApi,
  getPayEmployeeListApi,
  pagePayRewardsApi,
  savePayRewardApi,
  type PayDept,
  type PayEmployee,
  type PayReward,
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
];

const SOURCE_LABEL: Record<string, string> = {
  bulletin: '通报明文',
  grade: '职级单价',
  manual: '手工',
};

const PayrollRewardPage = memo(function PayrollRewardPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const formRef = useRef<ProFormInstance>(undefined);
  const [depts, setDepts] = useState<PayDept[]>([]);
  const [employees, setEmployees] = useState<PayEmployee[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayReward | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    void getPayDeptsApi().then((rows) => setDepts(rows ?? []));
  }, []);

  const loadEmployees = async () => {
    const first = await getPayEmployeeListApi({ pageNum: 1, pageSize: 200, jobStatus: 1 });
    let rows = first?.rows ?? [];
    if ((first?.total ?? 0) > rows.length) {
      const second = await getPayEmployeeListApi({ pageNum: 2, pageSize: 200, jobStatus: 1 });
      rows = rows.concat(second?.rows ?? []);
    }
    setEmployees(rows);
    return rows;
  };

  const currentPeriod = () => {
    const value = formRef.current?.getFieldValue('period');
    return value ? dayjs(value).format('YYYY-MM') : dayjs().format('YYYY-MM');
  };

  const openForm = async (row?: PayReward) => {
    if (!employees.length) {
      await loadEmployees();
    }
    setEditing(row ?? null);
    form.setFieldsValue({
      employeeId: row?.employeeId,
      period: row?.period ? dayjs(row.period) : dayjs(currentPeriod()),
      rewardKind: row?.rewardKind || '通报嘉奖',
      reason: row?.reason || '',
      times: row?.times ?? 1,
      amount: row ? Math.abs(Number(row.amount)) : 0,
      remark: row?.remark || '',
    });
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayRewardApi({
        id: editing?.id,
        employeeId: values.employeeId,
        period: dayjs(values.period).format('YYYY-MM'),
        rewardKind: values.rewardKind,
        reason: values.reason,
        times: values.times,
        amount: values.amount,
        remark: values.remark,
      });
      message.success('奖惩已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<PayReward>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => (
        <ActionButtons
          items={[
            { key: 'edit', label: '编辑', perm: 'payroll:reward:edit', onClick: () => void openForm(row) },
            {
              key: 'delete',
              label: '删除',
              perm: 'payroll:reward:edit',
              confirmTitle: '删除该奖惩记录？',
              onClick: async () => {
                await deletePayRewardApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    {
      title: '算薪月份',
      dataIndex: 'period',
      valueType: 'dateMonth',
      width: 120,
      initialValue: dayjs(),
      fieldProps: { allowClear: false },
      hideInTable: true,
    },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        options: depts.map((item) => ({ value: item.id, label: item.deptName })),
      },
      render: (_, row) => row.deptName || '—',
    },
    { title: '职级', dataIndex: 'gradeName', width: 100, search: false, render: (_, row) => row.gradeName || '—' },
    {
      title: '月份',
      dataIndex: 'periodShow',
      width: 100,
      search: false,
      render: (_, row) => row.period,
    },
    {
      title: '类型',
      dataIndex: 'rewardKind',
      width: 110,
      valueType: 'select',
      fieldProps: { options: KIND_OPTIONS, allowClear: true },
      render: (_, row) => {
        const item = KIND_OPTIONS.find((option) => option.value === row.rewardKind);
        return <Tag color={item?.color || 'default'}>{row.rewardKind}</Tag>;
      },
    },
    { title: '事由', dataIndex: 'reason', search: false, ellipsis: true },
    { title: '次数', dataIndex: 'times', width: 70, search: false },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 110,
      search: false,
      render: (_, row) => {
        const value = Number(row.amount);
        const color = value >= 0 ? 'text-emerald-600' : 'text-red-600';
        return <span className={color}>{value.toFixed(2)}</span>;
      },
    },
    {
      title: '来源',
      dataIndex: 'amountSource',
      width: 100,
      search: false,
      render: (_, row) => SOURCE_LABEL[row.amountSource || ''] || row.amountSource || '—',
    },
    { title: '关联通报', dataIndex: 'bulletinTitle', search: false, ellipsis: true },
  ];

  return (
    <>
      <BaseProTable<PayReward>
        rowKey='id'
        actionRef={actionRef}
        formRef={formRef}
        headerTitle='通报奖惩'
        columns={columns}
        scroll={{ x: 1200 }}
        tableExtraRender={() => (
          <div className='mb-3 text-sm text-neutral-500'>
            跟算薪月份挂钩。同步行政通报后，按八类（口头表扬/通报嘉奖/记功/记大功/口头批评/通报批评/记过/记大过）生成：正文写明金额用明文，否则用职级对应单价×次数。手工改过的同步时不覆盖。
          </div>
        )}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            perm='payroll:reward:edit'
            type='primary'
            onClick={() => void openForm()}
          >
            新增奖惩
          </PermissionButton>,
        ]}
        request={async (params) => {
          const query = tools.handleSearchParams(params) as Record<string, unknown>;
          if (params.period) {
            query.period = dayjs(params.period as string).format('YYYY-MM');
          }
          const { rows, total } = await pagePayRewardsApi(query);
          return { data: rows, total, success: true };
        }}
      />

      <Modal
        title={editing ? '编辑奖惩' : '新增奖惩'}
        open={open}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='employeeId'
            label='员工'
            rules={[{ required: true, message: '请选择员工' }]}
          >
            <Select
              showSearch
              optionFilterProp='label'
              options={employees.map((item) => ({
                value: item.id,
                label: `${item.employeeName}${item.deptName ? `（${item.deptName}）` : ''}`,
              }))}
              onDropdownVisibleChange={(visible) => {
                if (visible && !employees.length) {
                  void loadEmployees();
                }
              }}
            />
          </Form.Item>
          <Form.Item
            name='period'
            label='算薪月份'
            rules={[{ required: true, message: '请选择月份' }]}
          >
            <DatePicker
              picker='month'
              className='w-full!'
              allowClear={false}
            />
          </Form.Item>
          <Form.Item
            name='rewardKind'
            label='类型'
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select options={KIND_OPTIONS.map(({ label, value }) => ({ label, value }))} />
          </Form.Item>
          <Form.Item
            name='reason'
            label='事由'
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
            />
          </Form.Item>
          <Form.Item
            name='times'
            label='次数'
            rules={[{ required: true, message: '请填写次数' }]}
          >
            <InputNumber
              min={1}
              max={99}
              className='w-full!'
            />
          </Form.Item>
          <Form.Item
            name='amount'
            label='金额'
            rules={[{ required: true, message: '请填写金额' }]}
            extra='填绝对值；保存时奖记为正、惩记为负。'
          >
            <InputNumber
              min={0}
              precision={2}
              className='w-full!'
              addonAfter='元'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default PayrollRewardPage;
