import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Spin } from 'antd';
import { getClaimAdviceApi, type ClaimAdvicePage } from '@/api/claim';

type Block = { key: string; text: string };

const RULES: { when: RegExp; heading: RegExp; weight: number }[] = [
  { when: /十倍|赔十|食品安全法|异物|腐败|变质|不合格|食安|不新鲜|发臭|坏了/, heading: /148|食品安全法/, weight: 100 },
  { when: /赔三|退一赔三|欺诈|虚标|隐瞒|虚假宣传|缺斤/, heading: /第五十五条/, weight: 90 },
  { when: /霸王|概不退/, heading: /第二十六条/, weight: 80 },
  { when: /漏气|干硬|质量|不符|口感|肥瘦|品质|太硬|膻味/, heading: /第二十三条/, weight: 70 },
  { when: /票据|检疫|发票|凭证/, heading: /第二十二条/, weight: 70 },
  { when: /退换|退款|补发|运费|涨袋|规格/, heading: /第二十四条/, weight: 60 },
  { when: /夸大|海报|朋友圈|虚假宣传|虚标/, heading: /第二十条/, weight: 50 },
  { when: /虚假广告|广告/, heading: /第四十五条/, weight: 50 },
  { when: /产地|净重|保质期|知情|冷冻|发货/, heading: /第八条/, weight: 50 },
  { when: /异物|变质|污染|腐败|安全/, heading: /第七条/, weight: 40 },
  { when: /健康|受伤|致病/, heading: /第四十条/, weight: 40 },
];

function isHeading(line: string) {
  return /^第[一二三四五六七八九十百零〇两\d]+条/.test(line) || line.includes('食品安全法') || line.startsWith('分流');
}

function blocksOf(law: string): Block[] {
  const blocks: Block[] = [];
  let lines: string[] = [];
  const flush = () => {
    const text = lines.join('\n').trim();
    if (text) blocks.push({ key: `law-${blocks.length}`, text });
    lines = [];
  };
  for (const raw of law.split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    if (isHeading(line) && lines.length > 0) flush();
    lines.push(line);
  }
  flush();
  return blocks;
}

function focusOf(blocks: Block[], blob: string) {
  let best = -1;
  let score = 0;
  blocks.forEach((block, index) => {
    const head = block.text.slice(0, 80);
    let next = 0;
    for (const rule of RULES) {
      if (rule.when.test(blob) && rule.heading.test(head)) next += rule.weight;
    }
    if (next > score) {
      score = next;
      best = index;
    }
  });
  return best;
}

function shortTitle(text: string) {
  const line = (text.split('\n')[0] || '相关条款').split(/[：:]/)[0];
  return line.length > 28 ? line.slice(0, 28) : line;
}

export default function ClaimLawView() {
  const { token } = useParams();
  const [page, setPage] = useState<ClaimAdvicePage | null>(null);
  const [failed, setFailed] = useState(false);
  const focusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!token) {
      setFailed(true);
      return;
    }
    getClaimAdviceApi(token)
      .then(setPage)
      .catch(() => setFailed(true));
  }, [token]);

  const blocks = useMemo(() => blocksOf(page?.law || ''), [page?.law]);
  const focus = useMemo(() => {
    const blob = [page?.symptom, page?.recommendation, page?.productName, page?.symptomCategory]
      .filter(Boolean)
      .join('\n');
    return focusOf(blocks, blob);
  }, [blocks, page]);

  useEffect(() => {
    if (focus < 0) return;
    focusRef.current?.scrollIntoView({ block: 'start' });
  }, [focus, blocks]);

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
      {token ? (
        <Link
          className='text-sm text-blue-600'
          to={`/claim/advice/${token}`}
        >
          返回综合建议
        </Link>
      ) : null}
      <h1 className='mt-3 text-lg font-medium text-neutral-900'>消协法</h1>
      {focus >= 0 ? (
        <p className='mt-1 text-sm text-amber-700'>已定位到：{shortTitle(blocks[focus].text)}</p>
      ) : (
        <p className='mt-1 text-sm text-neutral-500'>本单没有单独对应到某一条，下面是全文。</p>
      )}
      {blocks.length === 0 ? <p className='mt-4 text-sm text-neutral-500'>还没有消协法内容。</p> : null}
      {blocks.map((block, index) => (
        <section
          key={block.key}
          ref={index === focus ? focusRef : undefined}
          className={
            index === focus
              ? 'mt-3 scroll-mt-4 rounded-lg bg-amber-50 p-4 ring-1 ring-amber-300'
              : 'mt-3 rounded-lg bg-white p-4 shadow-sm'
          }
        >
          {index === focus ? <p className='mb-2 text-xs font-medium text-amber-700'>本单相关</p> : null}
          <pre className='text-sm leading-6 whitespace-pre-wrap text-neutral-800'>{block.text}</pre>
        </section>
      ))}
    </div>
  );
}
