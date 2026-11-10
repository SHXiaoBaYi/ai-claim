import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal, Switch, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import { deletePayDeptApi, pagePayDeptsApi, savePayDeptApi, type PayDept } from '@/api/payroll';

const STATUS_OPTIONS = [
  { label: '正常', value: 0 },
  { label: '停用', value: 1 },
];

const PayrollDeptPage = memo(function PayrollDeptPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayDept | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const openForm = (row?: PayDept) => {
    setEditing(row ?? null);
    form.setFieldsValue({
      deptName: row?.deptName || '',
      sortOrder: row?.sortOrder ?? 0,
      status: row ? row.status === 0 : true,
    });
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayDeptApi({
        id: editing?.id,
        deptName: values.deptName,
        sortOrder: values.sortOrder ?? 0,
        status: values.status ? 0 : 1,
      });
      message.success('部门已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<PayDept>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      search: false,
      render: (_, row) => (
        <ActionButtons
          items={[
            { key: 'edit', label: '编辑', perm: 'payroll:base:edit', onClick: () => openForm(row) },
            {
              key: 'delete',
              label: '删除',
              perm: 'payroll:base:edit',
              confirmTitle: '删除该部门？',
              onClick: async () => {
                await deletePayDeptApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '部门', dataIndex: 'deptName', ellipsis: true },
    { title: '排序', dataIndex: 'sortOrder', width: 90, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      fieldProps: { options: STATUS_OPTIONS, allowClear: true },
      render: (_, row) => (row.status === 0 ? <Tag color='success'>正常</Tag> : <Tag>停用</Tag>),
    },
  ];

  return (
    <>
      <BaseProTable<PayDept>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        scroll={{ x: 640 }}
        headerTitle='部门'
        toolBarRender={() => [
          <PermissionButton
            key='add'
            perm='payroll:base:edit'
            type='primary'
            onClick={() => openForm()}
          >
            新增部门
          </PermissionButton>,
        ]}
        request={async (params) => {
          const res = await pagePayDeptsApi(tools.handleSearchParams(params));
          return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
        }}
      />
      <Modal
        title={editing ? '编辑部门' : '新增部门'}
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
            name='deptName'
            label='部门名称'
            rules={[{ required: true, message: '请填写部门名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='sortOrder'
            label='排序'
          >
            <InputNumber
              min={0}
              className='w-full!'
            />
          </Form.Item>
          <Form.Item
            name='status'
            label='启用'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default PayrollDeptPage;
