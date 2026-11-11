import { memo, useEffect, useState } from 'react';
import { App, Button, Card, Form, Input, Space, Switch, Tag } from 'antd';
import { getClaimRobotApi, saveClaimRobotApi, testClaimRobotApi, type ClaimRobot } from '@/api/claim';

const ClaimRobotPage = memo(function ClaimRobotPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [saved, setSaved] = useState<ClaimRobot | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const data = await getClaimRobotApi();
      setSaved(data);
      form.setFieldsValue({
        clientId: data.clientId || '',
        clientSecret: '',
        robotCode: data.robotCode || '',
        enabled: data.enabled === 1,
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const payload = async () => {
    const values = await form.validateFields();
    return {
      clientId: String(values.clientId || '').trim(),
      clientSecret: values.clientSecret ? String(values.clientSecret).trim() : '',
      robotCode: String(values.robotCode || '').trim(),
      enabled: values.enabled ? 1 : 0,
    };
  };

  const save = async () => {
    const data = await payload();
    setSaving(true);
    try {
      await saveClaimRobotApi(data);
      message.success('已保存');
      await load();
    } finally {
      setSaving(false);
    }
  };

  const test = async () => {
    const data = await payload();
    setTesting(true);
    try {
      await testClaimRobotApi(data);
      message.success('已连通钉钉');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Card
      title='理赔机器人'
      loading={loading}
      className='max-w-3xl'
    >
      <p className='mb-4 text-sm text-neutral-500'>
        这是单独的理赔机器人，不使用扫码登录那个应用。在钉钉开放平台新建企业内部应用，开通机器人单聊，把 Client
        ID、Client Secret 和 RobotCode
        填在这里。启用后，坐席和门店在单聊里发投诉，机器人会边问边记，收齐后直接给出建议和依据。
      </p>
      <div className='mb-4 flex items-center gap-2 text-sm'>
        <span>连接状态</span>
        {saved?.connected ? <Tag color='success'>已连接</Tag> : <Tag>未连接</Tag>}
        {saved?.hasClientSecret ? <span className='font-mono text-neutral-500'>{saved.clientSecretMasked}</span> : null}
      </div>
      <Form
        form={form}
        layout='vertical'
      >
        <Form.Item
          name='clientId'
          label='Client ID'
          rules={[{ required: true, message: '请填写 Client ID' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name='clientSecret'
          label='Client Secret'
          extra='留空表示不修改已保存的密钥'
        >
          <Input.Password />
        </Form.Item>
        <Form.Item
          name='robotCode'
          label='RobotCode'
          extra='下载聊天里的照片和视频时需要'
        >
          <Input />
        </Form.Item>
        <Form.Item
          name='enabled'
          label='启用'
          valuePropName='checked'
        >
          <Switch />
        </Form.Item>
        <Space>
          <Button
            type='primary'
            onClick={save}
            loading={saving}
          >
            保存
          </Button>
          <Button
            onClick={test}
            loading={testing}
          >
            测试连接
          </Button>
        </Space>
      </Form>
    </Card>
  );
});

export default ClaimRobotPage;
