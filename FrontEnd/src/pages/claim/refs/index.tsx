import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Empty, Spin, Tabs, Tag } from 'antd';
import { getClaimRefsApi, type ClaimRefCase, type ClaimRefPage } from '@/api/claim';

function kindLabel(kind: string) {
  if (kind === 'special') return '特殊案例';
  if (kind === 'similar') return '相似案例';
  return '历史参照';
}

function CaseCard({ item }: { item: ClaimRefCase }) {
  const rows = [
    ['投诉日期', item.complaintDate],
    ['顾客', item.customerName],
    ['产品', item.productName],
    ['现象', item.symptomBrief],
    ['判定', item.judgment],
    ['结果', item.resultText],
    ['处理意见', item.claimOpinion],
  ];
  return (
    <article className='rounded-lg bg-white p-4 shadow-sm'>
      <div className='mb-3 flex items-center gap-2'>
        <Tag color={item.kind === 'special' ? 'gold' : item.kind === 'similar' ? 'blue' : 'green'}>
          {kindLabel(item.kind)}
        </Tag>
        <Tag>{item.source === 'archive' ? '理赔档案' : '历史案例'}</Tag>
        <span className='text-sm text-neutral-500'>等价度 {item.score}</span>
      </div>
      {item.kind === 'special' ? (
        <p className='mb-3 text-sm text-amber-700'>当时无需理赔或不做处理，不作为本次处理依据。</p>
      ) : null}
      <dl className='space-y-2'>
        {rows.map(([label, value]) =>
          value ? (
            <div key={label}>
              <dt className='text-xs text-neutral-400'>{label}</dt>
              <dd className='mt-0.5 text-sm whitespace-pre-wrap text-neutral-800'>{value}</dd>
            </div>
          ) : null,
        )}
      </dl>
    </article>
  );
}

export default function ClaimRefsPage() {
  const { token } = useParams();
  const [page, setPage] = useState<ClaimRefPage | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) {
      setFailed(true);
      return;
    }
    getClaimRefsApi(token)
      .then(setPage)
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

  return (
    <div className='mx-auto min-h-screen max-w-3xl bg-neutral-50 px-4 py-5'>
      <h1 className='text-lg font-medium text-neutral-900'>历史参照案例</h1>
      {page.symptomCategory ? (
        <p className='mt-1 text-sm text-neutral-500'>
          现象大类：{page.symptomCategory}
          {page.productGroup ? `，产品：${page.productGroup}` : ''}
        </p>
      ) : null}
      {page.symptom ? <p className='mt-1 text-sm text-neutral-500'>本次现象：{page.symptom}</p> : null}
      {page.tabs.length === 0 ? (
        <Empty
          className='mt-10'
          description='没有可查看的历史参照'
        />
      ) : (
        <Tabs
          className='mt-4'
          items={page.tabs.map((tab) => ({
            key: tab.score,
            label: tab.score,
            children: (
              <div className='space-y-3 pb-8'>
                {tab.cases.map((item) => (
                  <CaseCard
                    key={item.id}
                    item={item}
                  />
                ))}
              </div>
            ),
          }))}
        />
      )}
    </div>
  );
}
