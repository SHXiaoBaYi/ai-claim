import { memo, useEffect, useState } from 'react';
import { App, Button, Card, Form, Input, Switch } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { usePermission } from '@/hooks/usePermission';
import { getDingTalkAppApi, saveDingTalkAppApi, testDingTalkAppApi, type DingTalkAppVO } from '@/api/dingtalk';

const DingTalkConfigPage = memo(function DingTalkConfigPage() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('system:dingtalk:edit');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [saved, setSaved] = useState<DingTalkAppVO | null>(null);

  const applyVo = (data: DingTalkAppVO | null | undefined) => {
    setSaved(data ?? null);
    form.setFieldsValue({
      appId: data?.appId || '',
      agentId: data?.agentId || '',
      clientId: data?.clientId || '',
      corpId: data?.corpId || '',
      clientSecret: '',
      enabled: data?.enabled === 1,
    });
  };

  const load = async () => {
    setLoading(true);
    try {
      applyVo(await getDingTalkAppApi());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const payload = async () => {
    const values = await form.validateFields();
    return {
      appId: String(values.appId).trim(),
      agentId: String(values.agentId).trim(),
      clientId: String(values.clientId).trim(),
      corpId: values.corpId ? String(values.corpId).trim() : '',
      clientSecret: values.clientSecret ? String(values.clientSecret).trim() : undefined,
      enabled: values.enabled ? 1 : 0,
    };
  };

  const handleSave = async () => {
    const data = await payload();
    setSaving(true);
    try {
      await saveDingTalkAppApi(data);
      message.success('已保存');
      await load();
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    const data = await payload();
    setTesting(true);
    try {
      await testDingTalkAppApi(data);
      message.success('已连通钉钉');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Card
      title='钉钉应用配置'
      loading={loading}
      className='max-w-3xl'
    >
      <p className='mb-4 text-sm text-neutral-500'>
        用于钉钉扫码登录。Client Secret 只保存到数据库，页面只显示脱敏结果。扫码登录需在钉钉开放平台配置回调域名，并与本系统登录页同源。
      </p>
      {saved?.hasClientSecret ? (
        <div className='mb-4 text-sm text-neutral-600'>
          当前密钥：<span className='font-mono'>{saved.clientSecretMasked}</span>
          （留空表示不修改）
        </div>
      ) : null}
      <Form
        form={form}
        layout='vertical'
        disabled={!canEdit}
        initialValues={{ enabled: false }}
      >
        <Form.Item
          name='appId'
          label='AppId'
          rules={[{ required: true, message: '请填写 AppId' }]}
        >
          <Input placeholder='钉钉应用 AppId' />
        </Form.Item>
        <Form.Item
          name='agentId'
          label='AgentId'
          rules={[{ required: true, message: '请填写 AgentId' }]}
        >
          <Input placeholder='钉钉应用 AgentId' />
        </Form.Item>
        <Form.Item
          name='clientId'
          label='Client ID'
          rules={[{ required: true, message: '请填写 Client ID' }]}
        >
          <Input placeholder='Client ID / AppKey' />
        </Form.Item>
        <Form.Item
          name='corpId'
          label='CorpId'
          rules={[{ required: true, message: '请填写企业 CorpId（不是 ClientId）' }]}
          extra='钉钉开放平台 → 应用信息/企业信息里的 CorpId。'
        >
          <Input placeholder='企业 CorpId，例如 dingxxxxxxxx' />
        </Form.Item>
        <Form.Item
          name='clientSecret'
          label='Client Secret'
          extra={saved?.hasClientSecret ? '填写后覆盖原密钥；留空则保持不变' : '首次保存必填'}
        >
          <Input.Password
            placeholder={saved?.hasClientSecret ? '留空则不修改' : 'Client Secret'}
            autoComplete='new-password'
          />
        </Form.Item>
        <Form.Item
          name='enabled'
          label='启用'
          valuePropName='checked'
        >
          <Switch />
        </Form.Item>
      </Form>
      <div className='flex gap-2'>
        <PermissionButton
          perm='system:dingtalk:edit'
          type='primary'
          loading={saving}
          onClick={() => void handleSave()}
        >
          保存
        </PermissionButton>
        <Button
          loading={testing}
          disabled={!canEdit}
          onClick={() => void handleTest()}
        >
          测试连接
        </Button>
      </div>
    </Card>
  );
});

export default DingTalkConfigPage;
