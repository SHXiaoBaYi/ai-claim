import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Button, Input, Spin, Tag, App } from 'antd';
import {
  adoptClaimAdviceApi,
  editClaimAdviceApi,
  getClaimAdviceApi,
  redoClaimAdviceApi,
  type ClaimAdvicePage,
} from '@/api/claim';

type Draft = { nature: string; actions: string; plan: string };

function pick(text: string, label: string) {
  const matched = text.match(new RegExp(label + '：([\\s\\S]*?)(?=\\n\\s*\\d+\\.\\s*|$)'));
  return matched ? matched[1].trim() : '';
}

function splitAdvice(text?: string): Draft {
  const body = text || '';
  return {
    nature: pick(body, '事件定性'),
    actions: pick(body, '行动要素'),
    plan: pick(body, '具体方案'),
  };
}

function decisionLabel(decision?: string) {
  if (decision === 'ADOPTED') return '已完全采纳';
  if (decision === 'REDO') return '已要求重出';
  if (decision === 'PARTIAL') return '已部分采纳';
  return '';
}

export default function ClaimAdvicePage() {
  const { token } = useParams();
  const { message } = App.useApp();
  const [params] = useSearchParams();
  const intent = params.get('do');
  const [page, setPage] = useState<ClaimAdvicePage | null>(null);
  const [draft, setDraft] = useState<Draft>({ nature: '', actions: '', plan: '' });
  const [editing, setEditing] = useState(intent === 'edit');
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) {
      setFailed(true);
      return;
    }
    getClaimAdviceApi(token)
      .then((data) => {
        setPage(data);
        setDraft(splitAdvice(data.recommendation));
      })
      .catch(() => setFailed(true));
  }, [token]);

  async function run(action: 'adopt' | 'redo' | 'edit') {
    if (!token || busy) return;
    setBusy(true);
    try {
      const next =
        action === 'adopt'
          ? await adoptClaimAdviceApi(token)
          : action === 'redo'
            ? await redoClaimAdviceApi(token)
            : await editClaimAdviceApi(token, draft);
      setPage(next);
      setDraft(splitAdvice(next.recommendation));
      setEditing(false);
      message.success(action === 'redo' ? '已重新生成建议' : '已记录');
    } catch {
      setBusy(false);
      return;
    }
    setBusy(false);
  }

  if (failed) {
    return <div className='p-6 text-sm text-neutral-500'>这条链接打不开。</div>;
  }
  if (!page) {
    return (
      <div className='flex min-h-screen items-center justify-center'>
        <Spin />
      </div>
    );
  }

  const label = decisionLabel(page.decision);
  return (
    <div className='mx-auto min-h-screen max-w-3xl bg-neutral-50 px-4 py-5'>
      <h1 className='text-lg font-medium text-neutral-900'>综合建议</h1>
      <p className='mt-1 text-sm text-neutral-500'>
        {[page.customerName, page.productName, page.symptomCategory, page.productGroup].filter(Boolean).join(' · ')}
      </p>
      {label ? (
        <Tag
          className='mt-3'
          color={page.decision === 'REDO' ? 'gold' : 'green'}
        >
          {label}
        </Tag>
      ) : null}
      {page.symptom ? <p className='mt-3 text-sm text-neutral-700'>现象：{page.symptom}</p> : null}

      {editing ? (
        <div className='mt-4 space-y-3'>
          <label className='block text-sm text-neutral-700'>
            事件定性
            <Input.TextArea
              className='mt-1'
              rows={3}
              value={draft.nature}
              onChange={(event) => setDraft({ ...draft, nature: event.target.value })}
            />
          </label>
          <label className='block text-sm text-neutral-700'>
            行动要素
            <Input.TextArea
              className='mt-1'
              rows={3}
              value={draft.actions}
              onChange={(event) => setDraft({ ...draft, actions: event.target.value })}
            />
          </label>
          <label className='block text-sm text-neutral-700'>
            具体方案
            <Input.TextArea
              className='mt-1'
              rows={4}
              value={draft.plan}
              onChange={(event) => setDraft({ ...draft, plan: event.target.value })}
            />
          </label>
        </div>
      ) : (
        <pre className='mt-4 rounded-lg bg-white p-4 text-sm leading-6 whitespace-pre-wrap text-neutral-800 shadow-sm'>
          {page.recommendation}
        </pre>
      )}

      <div className='mt-4 flex flex-col gap-2'>
        {editing ? (
          <>
            <Button
              type='primary'
              loading={busy}
              onClick={() => run('edit')}
            >
              保存修改并采纳
            </Button>
            <Button
              disabled={busy}
              onClick={() => {
                setDraft(splitAdvice(page.recommendation));
                setEditing(false);
              }}
            >
              取消修改
            </Button>
          </>
        ) : (
          <>
            <Button
              type={intent === 'adopt' ? 'primary' : 'default'}
              loading={busy}
              onClick={() => run('adopt')}
            >
              完全采纳
            </Button>
            <Button
              danger={intent === 'redo'}
              loading={busy}
              onClick={() => run('redo')}
            >
              不满意，重新出
            </Button>
            <Button onClick={() => setEditing(true)}>部分修改</Button>
          </>
        )}
        {page.hasRefs && token ? (
          <Link
            className='py-2 text-center text-sm text-blue-600'
            to={`/claim/refs/${token}`}
          >
            查看历史参照
          </Link>
        ) : null}
      </div>
    </div>
  );
}
