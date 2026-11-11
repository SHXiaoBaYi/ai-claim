import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Descriptions, Drawer, Form, Input, Popconfirm, Space, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import {
  deleteClaimArchiveApi,
  getClaimArchiveApi,
  getClaimArchiveListApi,
  openClaimEvidence,
  recommendClaimArchiveApi,
  updateClaimArchiveApi,
  type ClaimArchiveDetail,
  type ClaimCase,
} from '@/api/claim';
import tools from '@/utils/tools';

const STATUS: Record<string, { text: string; color: string }> = {
  COLLECTING: { text: '收集中', color: 'processing' },
  RECOMMENDED: { text: '已出建议', color: 'success' },
  INTERRUPTED: { text: '已中断', color: 'default' },
};

const ClaimArchivePage = memo(function ClaimArchivePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [detail, setDetail] = useState<ClaimArchiveDetail | null>(null);
  const [form] = Form.useForm();

  const openDetail = async (id: number) => {
    const data = await getClaimArchiveApi(id);
    setDetail(data);
    form.setFieldsValue(data.archive);
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateClaimArchiveApi({ ...detail?.archive, ...values });
      message.success('已保存');
      actionRef.current?.reload();
      if (detail?.archive.id) {
        setDetail(await getClaimArchiveApi(detail.archive.id));
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    await deleteClaimArchiveApi(id);
    message.success('已删除');
    if (detail?.archive.id === id) {
      setOpen(false);
      setDetail(null);
    }
    actionRef.current?.reload();
  };

  const recommend = async () => {
    if (!detail?.archive.id) return;
    setSaving(true);
    try {
      await recommendClaimArchiveApi(detail.archive.id);
      const data = await getClaimArchiveApi(detail.archive.id);
      setDetail(data);
      form.setFieldsValue(data.archive);
      message.success('已重新生成建议');
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<ClaimCase>[] = [
    { title: '编号', dataIndex: 'id', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      valueEnum: {
        COLLECTING: { text: '收集中' },
        RECOMMENDED: { text: '已出建议' },
        INTERRUPTED: { text: '已中断' },
      },
      render: (_, row) => {
        const item = STATUS[row.status || ''] || { text: row.status || '-', color: 'default' };
        return <Tag color={item.color}>{item.text}</Tag>;
      },
    },
    { title: '顾客', dataIndex: 'customerName', width: 120 },
    { title: '电话', dataIndex: 'customerPhone', width: 140 },
    { title: '门店', dataIndex: 'storeName', search: false, width: 140, ellipsis: true },
    { title: '产品', dataIndex: 'productName', ellipsis: true },
    { title: '现象', dataIndex: 'symptom', search: false, ellipsis: true },
    { title: '发起人', dataIndex: 'senderName', search: false, width: 120 },
    { title: '更新时间', dataIndex: 'updateTime', valueType: 'dateTime', search: false, width: 170 },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <Button
          key='view'
          type='link'
          className='px-0'
          onClick={() => openDetail(row.id)}
        >
          查看
        </Button>,
        <Popconfirm
          key='delete'
          title='删除这张档案？'
          description='测试数据删除后不再显示，对话记录和照片一并去掉。'
          okText='删除'
          cancelText='取消'
          onConfirm={() => remove(row.id)}
        >
          <Button
            type='link'
            danger
            className='px-0'
          >
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <>
      <BaseProTable<ClaimCase>
        headerTitle='理赔档案'
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        request={async (params) => {
          const { rows, total } = await getClaimArchiveListApi(tools.handleSearchParams(params));
          return { data: rows, total, success: true };
        }}
        tableExtraRender={() => (
          <div className='mb-3 text-sm text-neutral-500'>
            投诉只在钉钉单聊里收集。这里查看和修改已经落库的用户、消费、产品和建议。
          </div>
        )}
      />
      <Drawer
        title={detail ? `档案 ${detail.archive.id}` : '档案'}
        open={open}
        width={760}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Popconfirm
              title='删除这张档案？'
              description='测试数据删除后不再显示，对话记录和照片一并去掉。'
              okText='删除'
              cancelText='取消'
              onConfirm={() => detail?.archive.id && remove(detail.archive.id)}
            >
              <Button danger>删除</Button>
            </Popconfirm>
            <Button
              onClick={recommend}
              loading={saving}
            >
              重新生成建议
            </Button>
            <Button
              type='primary'
              onClick={save}
              loading={saving}
            >
              保存
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout='vertical'
        >
          <div className='grid grid-cols-2 gap-x-3'>
            <Form.Item
              name='customerName'
              label='顾客姓名'
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
              name='customerType'
              label='新客 / 老客'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='memberNo'
              label='会员号'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='storeName'
              label='门店或平台'
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
              name='productSpec'
              label='规格'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='orderNo'
              label='订单号'
            >
              <Input />
            </Form.Item>
            <Form.Item
              name='logisticsNo'
              label='物流单号'
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
            name='consumeSummary'
            label='消费记录'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name='symptom'
            label='现象'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name='cookingMethod'
            label='烹饪 / 解冻'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='logisticsSummary'
            label='物流情况'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name='customerDemand'
            label='顾客诉求'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name='recommendation'
            label='理赔建议'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name='recommendationBasis'
            label='依据'
          >
            <Input.TextArea rows={6} />
          </Form.Item>
        </Form>
        <Descriptions
          title='证据'
          column={1}
          className='mt-2'
          size='small'
        >
          {(detail?.evidences || []).length === 0 ? <Descriptions.Item label='文件'>暂无</Descriptions.Item> : null}
          {(detail?.evidences || []).map((file) => (
            <Descriptions.Item
              key={file.id}
              label={file.fileType || '文件'}
            >
              <Button
                type='link'
                className='px-0'
                onClick={() => openClaimEvidence(file.id)}
              >
                {file.fileName}
              </Button>
            </Descriptions.Item>
          ))}
        </Descriptions>
        <div className='mt-4 text-sm font-medium'>对话</div>
        <div className='mt-2 max-h-80 space-y-2 overflow-auto'>
          {(detail?.messages || []).map((item) => (
            <div
              key={item.id}
              className='rounded bg-neutral-50 px-3 py-2 text-sm'
            >
              <span className='mr-2 text-neutral-400'>{item.roleName === 'user' ? '用户' : '机器人'}</span>
              <span className='whitespace-pre-wrap'>{item.content}</span>
            </div>
          ))}
        </div>
      </Drawer>
    </>
  );
});

export default ClaimArchivePage;
