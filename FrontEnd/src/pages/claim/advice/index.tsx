import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Spin, Tag } from 'antd';
import { getClaimAdviceApi, type ClaimAdvicePage } from '@/api/claim';

function decisionLabel(decision?: string) {
  if (decision === 'ADOPTED') return '已完全采纳';
  if (decision === 'REDO') return '已要求重出';
  if (decision === 'PARTIAL') return '已部分采纳';
  return '';
}

export default function ClaimAdvicePage() {
  const { token } = useParams();
  const [page, setPage] = useState<ClaimAdvicePage | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) {
      setFailed(true);
      return;
    }
    getClaimAdviceApi(token)
      .then((data) => setPage(data))
      .catch(() => setFailed(true));
  }, [token]);

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

      <pre className='mt-4 rounded-lg bg-white p-4 text-sm leading-6 whitespace-pre-wrap text-neutral-800 shadow-sm'>
        {page.recommendation}
      </pre>

      {token && page.law ? (
        <Link
          className='mt-4 block text-sm text-blue-600'
          to={`/claim/advice/${token}/law`}
        >
          查看消协法
        </Link>
      ) : null}

      <p className='mt-4 text-sm text-neutral-600'>
        要改这条综合建议，回到钉钉聊天里直接说改哪里，机器人会按这句话改。
      </p>
      {page.hasRefs && token ? (
        <Link
          className='mt-4 block text-sm text-blue-600'
          to={`/claim/refs/${token}`}
        >
          查看历史参照
        </Link>
      ) : null}
    </div>
  );
}
