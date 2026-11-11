import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Form, Input, InputNumber, Modal, Select } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import { deleteClaimSopApi, getClaimSopTreeApi, saveClaimSopApi, type ClaimSopNode } from '@/api/claim';

const ClaimSopPage = memo(function ClaimSopPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm<ClaimSopNode>();

  const edit = (row?: ClaimSopNode, parentCode?: string) => {
    setCreating(!row);
    form.setFieldsValue(
      row || {
        nodeCode: '',
        parentCode: parentCode || '',
        nodeType: parentCode ? 'ACTION' : 'CATEGORY',
        title: '',
        content: '',
        keywords: '',
        sortOrder: 1,
      },
    );
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveClaimSopApi(values, creating);
      message.success('已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const remove = (row: ClaimSopNode) => {
    Modal.confirm({
      title: `删除「${row.title}」？`,
      content: '有下级节点时不能删除。',
      onOk: async () => {
        await deleteClaimSopApi(row.id);
        message.success('已删除');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumnType<ClaimSopNode>[] = [
    { title: '标题', dataIndex: 'title', width: 220 },
    { title: '类型', dataIndex: 'nodeType', width: 120, search: false },
    { title: '编码', dataIndex: 'nodeCode', width: 220, search: false },
    { title: '关键词', dataIndex: 'keywords', search: false, ellipsis: true },
    { title: '规则', dataIndex: 'content', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, row) => [
        <Button
          key='edit'
          type='link'
          className='px-0'
          onClick={() => edit(row)}
        >
          编辑
        </Button>,
        <Button
          key='add'
          type='link'
          className='px-0'
          onClick={() => edit(undefined, row.nodeCode)}
        >
          加下级
        </Button>,
        <Button
          key='del'
          type='link'
          danger
          className='px-0'
          onClick={() => remove(row)}
        >
          删除
        </Button>,
      ],
    },
  ];

  return (
    <>
      <BaseProTable<ClaimSopNode>
        headerTitle='处理流程'
        actionRef={actionRef}
        rowKey='id'
        search={false}
        pagination={false}
        columns={columns}
        request={async () => {
          const data = await getClaimSopTreeApi();
          return { data, success: true };
        }}
        toolBarRender={() => [
          <Button
            key='add'
            type='primary'
            onClick={() => edit()}
          >
            新增分类
          </Button>,
        ]}
      />
      <Modal
        title={creating ? '新增节点' : '编辑节点'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={save}
        confirmLoading={saving}
      >
        <Form
          form={form}
          layout='vertical'
          className='mt-3'
        >
          <Form.Item
            name='id'
            hidden
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='nodeCode'
            label='编码'
            rules={[{ required: true, message: '请填写编码' }]}
          >
            <Input disabled={!creating} />
          </Form.Item>
          <Form.Item
            name='parentCode'
            label='父编码'
          >
            <Input placeholder='根节点留空' />
          </Form.Item>
          <Form.Item
            name='nodeType'
            label='类型'
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { value: 'CATEGORY', label: '分类' },
                { value: 'SYMPTOM', label: '现象' },
                { value: 'ACTION', label: '处理方式' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name='title'
            label='标题'
            rules={[{ required: true, message: '请填写标题' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='keywords'
            label='关键词'
          >
            <Input placeholder='逗号分隔，用于识别现象' />
          </Form.Item>
          <Form.Item
            name='sortOrder'
            label='排序'
          >
            <InputNumber className='w-full' />
          </Form.Item>
          <Form.Item
            name='content'
            label='规则原文'
          >
            <Input.TextArea rows={5} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default ClaimSopPage;
