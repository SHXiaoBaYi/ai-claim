import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Form, Input, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import { getClaimHistoryListApi, importClaimHistoryApi, updateClaimHistoryApi, type ClaimHistory } from '@/api/claim';
import tools from '@/utils/tools';

const ClaimHistoryPage = memo(function ClaimHistoryPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [form] = Form.useForm<ClaimHistory>();

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateClaimHistoryApi(values);
      message.success('已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const importDocs = async () => {
    setImporting(true);
    try {
      const result = await importClaimHistoryApi();
      message.success(result.message || '导入完成');
      actionRef.current?.reload();
    } finally {
      setImporting(false);
    }
  };

  const columns: ProColumnType<ClaimHistory>[] = [
    { title: '年份', dataIndex: 'sourceYear', width: 80 },
    { title: '投诉日期', dataIndex: 'complaintDate', width: 120, search: false },
    { title: '顾客', dataIndex: 'customerName', width: 120, search: false },
    { title: '电话', dataIndex: 'customerPhone', width: 140, search: false },
    { title: '产品', dataIndex: 'productName', ellipsis: true, search: false },
    { title: '现象', dataIndex: 'symptomBrief', ellipsis: true, search: false },
    { title: '判定', dataIndex: 'judgment', width: 120, search: false },
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, row) => [
        <Button
          key='edit'
          type='link'
          className='px-0'
          onClick={() => {
            form.setFieldsValue(row);
            setOpen(true);
          }}
        >
          编辑
        </Button>,
      ],
    },
  ];

  return (
    <>
      <BaseProTable<ClaimHistory>
        headerTitle='历史案例'
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        request={async (params) => {
          const { rows, total } = await getClaimHistoryListApi(tools.handleSearchParams(params));
          return { data: rows, total, success: true };
        }}
        toolBarRender={() => [
          <Button
            key='import'
            loading={importing}
            onClick={importDocs}
          >
            从 docs 重新导入
          </Button>,
        ]}
        tableExtraRender={() => (
          <div className='mb-3 text-sm text-neutral-500'>三年客诉作为建议依据。手工改过的案例，重新导入时会保留。</div>
        )}
      />
      <Modal
        title='编辑历史案例'
        open={open}
        width={760}
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
          <div className='grid grid-cols-2 gap-x-3'>
            <Form.Item
              name='complaintDate'
              label='投诉日期'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='purchaseDate'
              label='购买日期'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='customerName'
              label='顾客'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='customerPhone'
              label='电话'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='storeName'
              label='门店'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='complaintType'
              label='投诉类型'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='productCategory'
              label='产品分类'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='productName'
              label='产品名称'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='judgment'
              label='初步判定'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='dutyDept'
              label='归属部门'
            >
              <Input />
            </Form.Item>
          </div>
          <Form.Item
            name='customerAddress'
            label='地址'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='symptomBrief'
            label='投诉简述'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='claimProduct'
            label='理赔产品'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='complaintContent'
            label='投诉内容'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name='claimOpinion'
            label='理赔意见'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name='adminResult'
            label='行政处理结果'
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default ClaimHistoryPage;
