import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, DatePicker, Form, Input, InputNumber, Modal, Progress, Select, Tag } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import {
  deletePayEmployeeApi,
  downloadPayWageExportApi,
  getPayDeptsApi,
  getPayEmployeeListApi,
  getPayGradesApi,
  getPayPostsApi,
  getPayWageExportProgressApi,
  savePayEmployeeApi,
  startPayWageExportApi,
  syncPayEmployeeFromDingTalkApi,
  type PayDept,
  type PayEmployee,
  type PayGrade,
  type PayPost,
  type PayWageExportJob,
} from '@/api/payroll';

const SALARY_ITEMS: { name: keyof PayEmployee; label: string }[] = [
  { name: 'amtPost', label: '岗位工资' },
  { name: 'amtFullAttendance', label: '全勤工资' },
  { name: 'amtSocialSubsidy', label: '社会保险补贴' },
  { name: 'amtHousingSubsidy', label: '公积金补贴' },
  { name: 'amtTransport', label: '交通通讯补助' },
  { name: 'amtNoncompete', label: '竞业费' },
  { name: 'amtHygiene', label: '洗理费' },
  { name: 'amtFixedOvertime', label: '固定加班费' },
];

const ZERO_SALARY = Object.fromEntries(SALARY_ITEMS.map((item) => [item.name, 0])) as Record<
  (typeof SALARY_ITEMS)[number]['name'],
  number
>;

const PayrollEmployeePage = memo(function PayrollEmployeePage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [depts, setDepts] = useState<PayDept[]>([]);
  const [posts, setPosts] = useState<PayPost[]>([]);
  const [grades, setGrades] = useState<PayGrade[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayEmployee | null>(null);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [wageOpen, setWageOpen] = useState(false);
  const [wageMonth, setWageMonth] = useState<Dayjs>(() => dayjs().subtract(1, 'month'));
  const [wageJob, setWageJob] = useState<PayWageExportJob | null>(null);
  const [wageStarting, setWageStarting] = useState(false);
  const [wageDownloading, setWageDownloading] = useState(false);
  const wagePollRef = useRef<number | null>(null);
  const [form] = Form.useForm();
  const formValues = Form.useWatch(true, form) as Partial<PayEmployee> | undefined;
  const salaryTotal = SALARY_ITEMS.reduce((sum, item) => sum + Number(formValues?.[item.name] || 0), 0);

  const stopWagePoll = () => {
    if (wagePollRef.current != null) {
      window.clearInterval(wagePollRef.current);
      wagePollRef.current = null;
    }
  };

  useEffect(() => () => stopWagePoll(), []);

  const loadOptions = async () => {
    const [deptRows, postRows, gradeRows] = await Promise.all([getPayDeptsApi(), getPayPostsApi(), getPayGradesApi()]);
    setDepts(deptRows ?? []);
    setPosts(postRows ?? []);
    setGrades(gradeRows ?? []);
  };

  useEffect(() => {
    void loadOptions();
  }, []);

  const activeDepts = depts.filter((item) => item.status === 0);
  const activePosts = posts.filter((item) => item.status === 0);
  const activeGrades = grades.filter((item) => item.status === 0);

  const openForm = (row?: PayEmployee) => {
    setEditing(row ?? null);
    form.setFieldsValue(
      row
        ? {
            ...row,
            hireDate: row.hireDate ? dayjs(row.hireDate) : undefined,
            jobStatus: row.jobStatus === 1,
          }
        : { jobStatus: true, ...ZERO_SALARY },
    );
    setOpen(true);
  };

  const syncDingTalk = () => {
    modal.confirm({
      title: '从钉钉同步通讯录？',
      content:
        '按钉钉企业通讯录写入部门和员工。已有员工的底薪构成、身份证、银行卡不会被覆盖。通讯录里已经没有的人会标成离职。',
      okText: '开始同步',
      onOk: async () => {
        setSyncing(true);
        try {
          const result = await syncPayEmployeeFromDingTalkApi();
          message.success(
            `已同步 ${result.employeeCount} 人，部门 ${result.deptCount} 个（新增 ${result.created}，更新 ${result.updated}，离职 ${result.resigned}）`,
          );
          await loadOptions();
          actionRef.current?.reload();
        } finally {
          setSyncing(false);
        }
      },
    });
  };

  const openWageExport = () => {
    stopWagePoll();
    setWageJob(null);
    setWageMonth(dayjs().subtract(1, 'month'));
    setWageOpen(true);
  };

  const startWageExport = async () => {
    if (!wageMonth) {
      message.warning('请选择生成月份');
      return;
    }
    const period = wageMonth.format('YYYY-MM');
    setWageStarting(true);
    stopWagePoll();
    try {
      const job = await startPayWageExportApi(period);
      setWageJob(job);
      wagePollRef.current = window.setInterval(() => {
        void (async () => {
          try {
            const next = await getPayWageExportProgressApi(job.jobId);
            setWageJob(next);
            if (next.status === 'done' || next.status === 'failed') {
              stopWagePoll();
              if (next.status === 'done') {
                message.success(next.message || '工资明细已生成');
              } else {
                message.error(next.message || '生成失败');
              }
            }
          } catch {
            stopWagePoll();
          }
        })();
      }, 800);
    } catch {
      // request interceptor already tips
    } finally {
      setWageStarting(false);
    }
  };

  const downloadWage = async () => {
    if (!wageJob?.jobId) return;
    setWageDownloading(true);
    try {
      await downloadPayWageExportApi(wageJob.jobId, wageJob.fileName);
    } catch {
      message.error('下载失败，请重新生成');
    } finally {
      setWageDownloading(false);
    }
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayEmployeeApi({
        id: editing?.id,
        deptId: values.deptId,
        postId: values.postId,
        gradeId: values.gradeId,
        employeeName: values.employeeName,
        hireDate: values.hireDate.format('YYYY-MM-DD'),
        amtPost: values.amtPost ?? 0,
        amtFullAttendance: values.amtFullAttendance ?? 0,
        amtSocialSubsidy: values.amtSocialSubsidy ?? 0,
        amtHousingSubsidy: values.amtHousingSubsidy ?? 0,
        amtTransport: values.amtTransport ?? 0,
        amtNoncompete: values.amtNoncompete ?? 0,
        amtHygiene: values.amtHygiene ?? 0,
        amtFixedOvertime: values.amtFixedOvertime ?? 0,
        employeeNo: values.employeeNo,
        phone: values.phone,
        idCard: values.idCard,
        bankAccount: values.bankAccount,
        bankName: values.bankName,
        jobStatus: values.jobStatus ? 1 : 0,
        hukouType: values.hukouType || '',
        socialBase: values.socialBase ?? null,
        socialAccount: values.socialAccount,
      });
      message.success('已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<PayEmployee>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => (
        <ActionButtons
          items={[
            { key: 'edit', label: '编辑', perm: 'payroll:employee:edit', onClick: () => openForm(row) },
            {
              key: 'delete',
              label: '删除',
              perm: 'payroll:employee:edit',
              confirmTitle: '删除该员工？',
              onClick: async () => {
                await deletePayEmployeeApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '工号', dataIndex: 'employeeNo', width: 110, search: false },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 120,
      valueType: 'select',
      fieldProps: { options: depts.map((item) => ({ value: item.id, label: item.deptName })) },
      render: (_, row) => row.deptName || '—',
    },
    {
      title: '岗位',
      dataIndex: 'postId',
      width: 120,
      valueType: 'select',
      fieldProps: { options: posts.map((item) => ({ value: item.id, label: item.postName })) },
      render: (_, row) => row.postName || '—',
    },
    {
      title: '职级',
      dataIndex: 'gradeId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        options: [
          { value: 0, label: '未设职级' },
          ...grades.map((item) => ({ value: item.id, label: item.gradeName })),
        ],
      },
      render: (_, row) => (row.gradeName ? row.gradeName : <Tag color='warning'>未设</Tag>),
    },
    { title: '入职时间', dataIndex: 'hireDate', width: 120, search: false },
    {
      title: '底薪合计',
      dataIndex: 'salaryStandard',
      width: 110,
      search: false,
      render: (_, row) => Number(row.salaryStandard || 0).toFixed(2),
    },
    {
      title: '户口性质',
      dataIndex: 'hukouType',
      width: 100,
      search: false,
      render: (_, row) => row.hukouType || '—',
    },
    {
      title: '缴费基数',
      dataIndex: 'socialBase',
      width: 110,
      search: false,
      render: (_, row) => (row.socialBase == null ? '—' : row.socialBase),
    },
    { title: '个人社保账号', dataIndex: 'socialAccount', width: 160, search: false, ellipsis: true },
    { title: '手机号', dataIndex: 'phone', width: 130, search: false },
    { title: '身份证号', dataIndex: 'idCard', width: 180, search: false, ellipsis: true },
    { title: '银行卡号', dataIndex: 'bankAccount', width: 180, search: false, ellipsis: true },
    { title: '开户行', dataIndex: 'bankName', width: 160, search: false, ellipsis: true },
    {
      title: '在职状态',
      dataIndex: 'jobStatus',
      width: 100,
      valueType: 'select',
      fieldProps: {
        options: [
          { value: 1, label: '在职' },
          { value: 0, label: '离职' },
        ],
      },
      render: (_, row) => (row.jobStatus === 1 ? <Tag color='success'>在职</Tag> : <Tag>离职</Tag>),
    },
  ];

  return (
    <>
      <BaseProTable<PayEmployee>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='员工管理'
        columns={columns}
        scroll={{ x: 2220 }}
        tableExtraRender={() => (
          <div className='mb-3 text-sm text-neutral-500'>
            底薪由岗位工资、全勤工资、社会保险补贴、公积金补贴、交通通讯补助、竞业费、洗理费、固定加班费构成，合计即工资标准。钉钉同步不会覆盖底薪构成与职级。
          </div>
        )}
        toolBarRender={() => [
          <PermissionButton
            key='wage'
            perm='payroll:employee:list'
            onClick={openWageExport}
          >
            生成工资明细
          </PermissionButton>,
          <PermissionButton
            key='sync'
            perm='payroll:employee:edit'
            loading={syncing}
            onClick={syncDingTalk}
          >
            从钉钉同步
          </PermissionButton>,
          <PermissionButton
            key='add'
            perm='payroll:employee:edit'
            type='primary'
            onClick={() => openForm()}
          >
            新增员工
          </PermissionButton>,
        ]}
        request={async (params) => {
          const { rows, total } = await getPayEmployeeListApi(tools.handleSearchParams(params));
          return { data: rows, total, success: true };
        }}
      />

      <Modal
        title='生成工资明细'
        open={wageOpen}
        onCancel={() => {
          stopWagePoll();
          setWageOpen(false);
        }}
        footer={null}
        destroyOnHidden
      >
        <p className='mb-3 text-sm text-neutral-500'>
          按模版「工资」表生成在职员工工资明细：底薪构成、考勤、通报、绩效、额外费用；社保/公积金按缴费基数匹配缴纳规则计算个人扣款；个税一期取额外费用「个税」类目。数据较多时显示进度，完成后可下载
          Excel。
        </p>
        <div className='mb-4 flex flex-wrap items-center gap-3'>
          <span>算薪月份</span>
          <DatePicker
            picker='month'
            value={wageMonth}
            allowClear={false}
            disabled={wageStarting || wageJob?.status === 'pending' || wageJob?.status === 'running'}
            onChange={(value) => value && setWageMonth(value)}
          />
          <PermissionButton
            perm='payroll:employee:list'
            type='primary'
            loading={wageStarting}
            disabled={wageJob?.status === 'pending' || wageJob?.status === 'running'}
            onClick={() => void startWageExport()}
          >
            开始生成
          </PermissionButton>
          {wageJob?.status === 'done' ? (
            <PermissionButton
              perm='payroll:employee:list'
              loading={wageDownloading}
              onClick={() => void downloadWage()}
            >
              下载 Excel
            </PermissionButton>
          ) : null}
        </div>
        {wageJob ? (
          <div className='space-y-2'>
            <Progress
              percent={wageJob.percent ?? 0}
              status={wageJob.status === 'failed' ? 'exception' : wageJob.status === 'done' ? 'success' : 'active'}
            />
            <div className='text-sm text-neutral-500'>{wageJob.message}</div>
          </div>
        ) : null}
      </Modal>

      <Modal
        title={editing ? '编辑员工' : '新增员工'}
        open={open}
        width={760}
        confirmLoading={saving}
        onOk={() => void handleSave()}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout='vertical'
          className='grid grid-cols-2 gap-x-4'
        >
          <Form.Item
            name='employeeName'
            label='姓名'
            rules={[{ required: true, message: '请填写姓名' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='employeeNo'
            label='工号'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='deptId'
            label='部门'
            rules={[{ required: true, message: '请选择部门' }]}
          >
            <Select
              options={activeDepts.map((item) => ({ value: item.id, label: item.deptName }))}
              placeholder='请先在部门页维护'
            />
          </Form.Item>
          <Form.Item
            name='postId'
            label='岗位'
            rules={[{ required: true, message: '请选择岗位' }]}
          >
            <Select
              options={activePosts.map((item) => ({ value: item.id, label: item.postName }))}
              placeholder='请先在岗位页维护'
            />
          </Form.Item>
          <Form.Item
            name='gradeId'
            label='职级'
            rules={[{ required: true, message: '请选择职级' }]}
          >
            <Select
              options={activeGrades.map((item) => ({ value: item.id, label: item.gradeName }))}
              placeholder='请先在职级页维护'
            />
          </Form.Item>
          <Form.Item
            name='hireDate'
            label='入职时间'
            rules={[{ required: true, message: '请选择入职时间' }]}
          >
            <DatePicker className='w-full!' />
          </Form.Item>

          <div className='col-span-2 mb-1 text-sm font-medium text-neutral-700'>底薪构成</div>
          {SALARY_ITEMS.map((item) => (
            <Form.Item
              key={item.name}
              name={item.name}
              label={item.label}
              rules={[{ required: true, message: `请填写${item.label}` }]}
            >
              <InputNumber
                min={0}
                precision={2}
                className='w-full!'
                addonAfter='元'
              />
            </Form.Item>
          ))}
          <div className='col-span-2 -mt-2 mb-3 text-sm text-neutral-500'>
            底薪合计（工资标准）：
            <span className='ml-1 font-medium text-neutral-800'>{salaryTotal.toFixed(2)}</span> 元
          </div>

          <Form.Item
            name='phone'
            label='手机号'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='idCard'
            label='身份证号'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='bankAccount'
            label='银行卡号'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='bankName'
            label='开户行'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='hukouType'
            label='户口性质'
          >
            <Select
              allowClear
              placeholder='请选择'
              options={[
                { value: '城镇', label: '城镇' },
                { value: '农村', label: '农村' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name='socialBase'
            label='缴费基数'
          >
            <InputNumber
              min={0}
              precision={2}
              className='w-full!'
            />
          </Form.Item>
          <Form.Item
            name='socialAccount'
            label='个人社保账号'
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='jobStatus'
            label='在职状态'
          >
            <Select
              options={[
                { value: true, label: '在职' },
                { value: false, label: '离职' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default PayrollEmployeePage;
