import { memo, useEffect, useState } from 'react';
import { App, Card, Input } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getClaimLawApi, saveClaimLawApi } from '@/api/claim';

const ClaimLawPage = memo(function ClaimLawPage() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [text, setText] = useState('');

  useEffect(() => {
    setLoading(true);
    getClaimLawApi()
      .then((data) => setText(data.text || ''))
      .catch(() => setText(''))
      .finally(() => setLoading(false));
  }, []);

  const save = async () => {
    const body = text.trim();
    if (!body) {
      message.warning('请填写消协法内容');
      return;
    }
    setSaving(true);
    try {
      await saveClaimLawApi(body);
      setText(body);
      message.success('已保存');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title='消协法'
      loading={loading}
      extra={
        <PermissionButton
          perm='claim:law:edit'
          type='primary'
          loading={saving}
          onClick={save}
        >
          保存
        </PermissionButton>
      }
    >
      <p className='mb-3 text-sm text-neutral-500'>
        生成综合建议时会按这里的消协法来写。钉钉卡片上有「查看消协法」按钮，打开后定位到本单相关条款。
      </p>
      <Input.TextArea
        rows={22}
        value={text}
        onChange={(event) => setText(event.target.value)}
      />
    </Card>
  );
});

export default ClaimLawPage;
