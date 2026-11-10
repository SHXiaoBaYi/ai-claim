import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType, ProFormInstance } from '@ant-design/pro-components';
import { App, DatePicker, Form, Input, InputNumber, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import {
  deletePayPerformanceApi,
  generatePayPerformanceApi,
  getPayDeptsApi,
  getPayEmployeeListApi,
  pagePayPerformancesApi,
  savePayPerformanceApi,
  type PayDept,
  type PayEmployee,
  type PayPerformance,
} from '@/api/payroll';

const PayrollPerformancePage = memo(function PayrollPerformancePage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const formRef = useRef<ProFormInstance>(undefined);
  const [depts, setDepts] = useState<PayDept[]>([]);
  const [employees, setEmployees] = useState<PayEmployee[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayPerformance | null>(null);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
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

  const openForm = async (row?: PayPerformance) => {
    if (!employees.length) {
      await loadEmployees();
    }
    setEditing(row ?? null);
    form.setFieldsValue({
      employeeId: row?.employeeId,
      period: row?.period ? dayjs(row.period) : dayjs(currentPeriod()),
      coefficient: row?.coefficient ?? 1,
      remark: row?.remark || '',
    });
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayPerformanceApi({
        id: editing?.id,
        employeeId: values.employeeId,
        period: dayjs(values.period).format('YYYY-MM'),
        coefficient: values.coefficient,
        remark: values.remark,
      });
      message.success('绩效考核已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const generate = () => {
    const period = currentPeriod();
    modal.confirm({
      title: `为 ${period} 生成在职员工考核行？`,
      content: '只补还没有的人，默认系数 1，已有记录不会改。生成后可再逐人改系数。',
      okText: '生成',
      onOk: async () => {
        setGenerating(true);
        try {
          const count = await generatePayPerformanceApi(period);
          message.success(count > 0 ? `已补齐 ${count} 人` : '该月在职员工都已有记录');
          actionRef.current?.reload();
        } finally {
          setGenerating(false);
        }
      },
    });
  };

  const columns: ProColumnType<PayPerformance>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => (
        <ActionButtons
          items={[
            { key: 'edit', label: '编辑', perm: 'payroll:performance:edit', onClick: () => void openForm(row) },
            {
              key: 'delete',
              label: '删除',
              perm: 'payroll:performance:edit',
              confirmTitle: '删除该月考核记录？',
              onClick: async () => {
                await deletePayPerformanceApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    {
      title: '考核月份',
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
    { title: '岗位', dataIndex: 'postName', width: 120, search: false, ellipsis: true },
    { title: '职级', dataIndex: 'gradeName', width: 100, search: false, render: (_, row) => row.gradeName || '—' },
    {
      title: '月份',
      dataIndex: 'period',
      width: 100,
      search: false,
    },
    {
      title: '绩效系数',
      dataIndex: 'coefficient',
      width: 110,
      search: false,
      render: (_, row) => Number(row.coefficient).toFixed(4),
    },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
  ];

  return (
    <>
      <BaseProTable<PayPerformance>
        rowKey='id'
        actionRef={actionRef}
        formRef={formRef}
        headerTitle='绩效考核'
        columns={columns}
        scroll={{ x: 960 }}
        tableExtraRender={() => (
          <div className='mb-3 text-sm text-neutral-500'>
            按员工、按月维护绩效考核系数，核算工资时挂到对应员工。可先按月份「生成在职员工」，再改系数；同一员工同一月只能有一条。
          </div>
        )}
        toolBarRender={() => [
          <PermissionButton
            key='generate'
            perm='payroll:performance:edit'
            loading={generating}
            onClick={generate}
          >
            生成本月在职员工
          </PermissionButton>,
          <PermissionButton
            key='add'
            perm='payroll:performance:edit'
            type='primary'
            onClick={() => void openForm()}
          >
            新增考核
          </PermissionButton>,
        ]}
        request={async (params) => {
          const query = tools.handleSearchParams(params) as Record<string, unknown>;
          if (params.period) {
            query.period = dayjs(params.period as string).format('YYYY-MM');
          }
          const { rows, total } = await pagePayPerformancesApi(query);
          return { data: rows, total, success: true };
        }}
      />

      <Modal
        title={editing ? '编辑绩效考核' : '新增绩效考核'}
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
              placeholder='选择在职员工'
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
            label='考核月份'
            rules={[{ required: true, message: '请选择月份' }]}
          >
            <DatePicker
              picker='month'
              className='w-full!'
              allowClear={false}
            />
          </Form.Item>
          <Form.Item
            name='coefficient'
            label='绩效系数'
            rules={[{ required: true, message: '请填写绩效系数' }]}
            extra='默认 1，可按考核结果上调或下调。'
          >
            <InputNumber
              min={0}
              max={9.9999}
              step={0.01}
              precision={4}
              className='w-full!'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default PayrollPerformancePage;
