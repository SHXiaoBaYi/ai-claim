import { memo, useEffect, useState } from 'react';
import { App, Card, Input, InputNumber } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getPaySocialRulesApi, savePaySocialRuleApi, type PaySocialRule } from '@/api/payroll';

const PayrollSocialPage = memo(function PayrollSocialPage() {
  const { message } = App.useApp();
  const [rules, setRules] = useState<PaySocialRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const rows = await getPaySocialRulesApi();
      setRules(rows ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const patchRule = (id: number, patch: Partial<PaySocialRule>) => {
    setRules((current) => current.map((rule) => (rule.id === id ? { ...rule, ...patch } : rule)));
  };

  const patchItem = (
    ruleId: number,
    itemId: number,
    field: 'employerRate' | 'personalRate' | 'baseMin' | 'baseMax',
    value: number | null,
  ) => {
    setRules((current) =>
      current.map((rule) =>
        rule.id === ruleId
          ? {
              ...rule,
              items: rule.items.map((item) =>
                item.id === itemId
                  ? { ...item, [field]: field === 'employerRate' || field === 'personalRate' ? (value ?? 0) : value }
                  : item,
              ),
            }
          : rule,
      ),
    );
  };

  const save = async (rule: PaySocialRule) => {
    if (rule.baseMax < rule.baseMin) {
      message.warning('社保缴费基数上限不能小于下限');
      return;
    }
    const housing = rule.items.find((item) => item.itemCode === 'housing');
    if (housing?.baseMin != null && housing.baseMax != null && housing.baseMax < housing.baseMin) {
      message.warning('公积金基数上限不能小于下限');
      return;
    }
    setSavingId(rule.id);
    try {
      await savePaySocialRuleApi(rule);
      message.success(`${rule.regionName}${rule.hukouType}规则已保存`);
      await load();
    } finally {
      setSavingId(null);
    }
  };

  return (
    <div className='flex flex-col gap-4'>
      <p className='text-sm text-neutral-500'>
        全公司共用。比例是百分数，例如 16 表示 16%。上面的基数是社保基数。住房公积金单独一行，上海默认各 7%（基数
        2740–37731），新疆默认各 12%（基数 2070–35071）。工伤默认按一类行业
        0.2%。上海城镇和农村职工比例相同；新疆农村失业保险个人不缴。
      </p>
      <div className='grid gap-4 xl:grid-cols-2'>
        {rules.map((rule) => (
          <Card
            key={rule.id}
            loading={loading}
            title={`${rule.regionName} · ${rule.hukouType}`}
            extra={
              <PermissionButton
                perm='payroll:social:edit'
                type='primary'
                loading={savingId === rule.id}
                onClick={() => void save(rule)}
              >
                保存
              </PermissionButton>
            }
          >
            <div className='mb-4 grid grid-cols-2 gap-3'>
              <label className='text-sm text-neutral-600'>
                社保缴费基数下限
                <InputNumber
                  className='mt-1 w-full!'
                  min={0}
                  precision={2}
                  value={rule.baseMin}
                  onChange={(value) => patchRule(rule.id, { baseMin: value ?? 0 })}
                />
              </label>
              <label className='text-sm text-neutral-600'>
                社保缴费基数上限
                <InputNumber
                  className='mt-1 w-full!'
                  min={0}
                  precision={2}
                  value={rule.baseMax}
                  onChange={(value) => patchRule(rule.id, { baseMax: value ?? 0 })}
                />
              </label>
            </div>
            <div className='mb-2 grid grid-cols-[1.4fr_1fr_1fr] gap-2 text-xs text-neutral-500'>
              <span>险种</span>
              <span>单位比例 %</span>
              <span>个人比例 %</span>
            </div>
            <div className='flex flex-col gap-2'>
              {rule.items.map((item) => (
                <div
                  key={item.id}
                  className='grid grid-cols-[1.4fr_1fr_1fr] items-center gap-2'
                >
                  <span className='text-sm'>{item.itemName}</span>
                  <InputNumber
                    min={0}
                    max={100}
                    precision={2}
                    value={item.employerRate}
                    onChange={(value) => patchItem(rule.id, item.id, 'employerRate', value)}
                  />
                  <InputNumber
                    min={0}
                    max={100}
                    precision={2}
                    value={item.personalRate}
                    onChange={(value) => patchItem(rule.id, item.id, 'personalRate', value)}
                  />
                  {item.itemCode === 'housing' ? (
                    <>
                      <span className='text-xs text-neutral-500'>公积金基数</span>
                      <InputNumber
                        min={0}
                        precision={2}
                        placeholder='下限'
                        value={item.baseMin}
                        onChange={(value) => patchItem(rule.id, item.id, 'baseMin', value)}
                      />
                      <InputNumber
                        min={0}
                        precision={2}
                        placeholder='上限'
                        value={item.baseMax}
                        onChange={(value) => patchItem(rule.id, item.id, 'baseMax', value)}
                      />
                    </>
                  ) : null}
                </div>
              ))}
            </div>
            <label className='mt-4 block text-sm text-neutral-600'>
              说明
              <Input.TextArea
                className='mt-1'
                rows={3}
                maxLength={500}
                value={rule.remark}
                onChange={(event) => patchRule(rule.id, { remark: event.target.value })}
              />
            </label>
          </Card>
        ))}
      </div>
    </div>
  );
});

export default PayrollSocialPage;
