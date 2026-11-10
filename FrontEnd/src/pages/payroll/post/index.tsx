import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal, Switch, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import { deletePayPostApi, pagePayPostsApi, savePayPostApi, type PayPost } from '@/api/payroll';

const STATUS_OPTIONS = [
  { label: '正常', value: 0 },
  { label: '停用', value: 1 },
];

const PayrollPostPage = memo(function PayrollPostPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayPost | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const openForm = (row?: PayPost) => {
    setEditing(row ?? null);
    form.setFieldsValue({
      postName: row?.postName || '',
      sortOrder: row?.sortOrder ?? 0,
      status: row ? row.status === 0 : true,
    });
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayPostApi({
        id: editing?.id,
        postName: values.postName,
        sortOrder: values.sortOrder ?? 0,
        status: values.status ? 0 : 1,
      });
      message.success('岗位已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<PayPost>[] = [
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
              confirmTitle: '删除该岗位？',
              onClick: async () => {
                await deletePayPostApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '岗位', dataIndex: 'postName', ellipsis: true },
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
      <BaseProTable<PayPost>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        scroll={{ x: 640 }}
        headerTitle='岗位'
        toolBarRender={() => [
          <PermissionButton
            key='add'
            perm='payroll:base:edit'
            type='primary'
            onClick={() => openForm()}
          >
            新增岗位
          </PermissionButton>,
        ]}
        request={async (params) => {
          const res = await pagePayPostsApi(tools.handleSearchParams(params));
          return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
        }}
      />
      <Modal
        title={editing ? '编辑岗位' : '新增岗位'}
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
            name='postName'
            label='岗位名称'
            rules={[{ required: true, message: '请填写岗位名称' }]}
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

export default PayrollPostPage;
