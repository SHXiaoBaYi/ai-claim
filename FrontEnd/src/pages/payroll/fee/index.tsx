import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, DatePicker, Form, Image, Input, InputNumber, Modal, Select, Tag, Upload } from 'antd';
import dayjs from 'dayjs';
import type { UploadFile } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import tools from '@/utils/tools';
import { resolveUploadUrl } from '@/utils/uploadUrl';
import {
  deletePayEmployeeFeeApi,
  deletePayFeeCategoryApi,
  getPayDeptsApi,
  getPayEmployeeListApi,
  getPayFeeCategoriesApi,
  pagePayEmployeeFeesApi,
  savePayEmployeeFeeApi,
  savePayFeeCategoryApi,
  uploadPayFeeImageApi,
  type PayDept,
  type PayEmployee,
  type PayEmployeeFee,
  type PayFeeCategory,
} from '@/api/payroll';

const PayrollFeePage = memo(function PayrollFeePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [depts, setDepts] = useState<PayDept[]>([]);
  const [categories, setCategories] = useState<PayFeeCategory[]>([]);
  const [employees, setEmployees] = useState<PayEmployee[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<PayEmployeeFee | null>(null);
  const [saving, setSaving] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [categoryName, setCategoryName] = useState('');
  const [categoryFeeKind, setCategoryFeeKind] = useState<'add' | 'deduct'>('deduct');
  const [categorySaving, setCategorySaving] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm();
  const watchedCategoryId = Form.useWatch('categoryId', form);
  const watchedEmployeeId = Form.useWatch('employeeId', form);
  const hireLocked = categories.find((item) => item.id === watchedCategoryId)?.monthRule === 'hire';

  const loadCategories = async () => {
    setCategories((await getPayFeeCategoriesApi()) ?? []);
  };

  useEffect(() => {
    void (async () => {
      const [deptRows] = await Promise.all([getPayDeptsApi(), loadCategories()]);
      setDepts(deptRows ?? []);
    })();
  }, []);

  const loadEmployees = async () => {
    const first = await getPayEmployeeListApi({ pageNum: 1, pageSize: 200, jobStatus: 1 });
    let rows = first?.rows ?? [];
    if ((first?.total ?? 0) > rows.length) {
      const second = await getPayEmployeeListApi({ pageNum: 2, pageSize: 200, jobStatus: 1 });
      rows = rows.concat(second?.rows ?? []);
    }
    setEmployees(rows);
    return rows;
  };

  const hireMonthOf = (employeeId?: number, people = employees) => {
    const employee = people.find((item) => item.id === employeeId);
    return employee?.hireDate ? dayjs(employee.hireDate) : undefined;
  };

  const openForm = async (row?: PayEmployeeFee) => {
    const people = employees.length > 0 ? employees : await loadEmployees();
    const categoryId = row?.categoryId ?? categories[0]?.id;
    const locked = categories.find((item) => item.id === categoryId)?.monthRule === 'hire';
    const employeeId = row?.employeeId;
    setEditing(row ?? null);
    form.setFieldsValue({
      employeeId,
      feeMonth: locked ? hireMonthOf(employeeId, people) : row?.feeMonth ? dayjs(row.feeMonth) : dayjs(),
      categoryId,
      amount: row?.amount,
      imageUrl: row?.imageUrl || '',
      remark: row?.remark || '',
    });
    setFileList(
      row?.imageUrl ? [{ uid: String(row.id), name: '截图', status: 'done', url: resolveUploadUrl(row.imageUrl) }] : [],
    );
    setOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await savePayEmployeeFeeApi({
        id: editing?.id,
        employeeId: values.employeeId,
        feeMonth: dayjs(values.feeMonth).format('YYYY-MM'),
        categoryId: values.categoryId,
        amount: values.amount,
        imageUrl: values.imageUrl,
        remark: values.remark,
      });
      message.success('额外费用已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const addCategory = async () => {
    const name = categoryName.trim();
    if (!name) {
      message.warning('请填写费用类目');
      return;
    }
    setCategorySaving(true);
    try {
      await savePayFeeCategoryApi({ categoryName: name, feeKind: categoryFeeKind });
      setCategoryName('');
      setCategoryFeeKind('deduct');
      message.success('类目已添加');
      await loadCategories();
    } finally {
      setCategorySaving(false);
    }
  };

  const changeCategoryKind = async (item: PayFeeCategory, feeKind: 'add' | 'deduct') => {
    if (item.feeKind === feeKind) return;
    await savePayFeeCategoryApi({ id: item.id, categoryName: item.categoryName, feeKind });
    message.success('类型已更新');
    await loadCategories();
    actionRef.current?.reload();
  };

  const columns: ProColumnType<PayEmployeeFee>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      search: false,
      render: (_, row) => (
        <ActionButtons
          items={[
            { key: 'edit', label: '编辑', perm: 'payroll:fee:edit', onClick: () => void openForm(row) },
            {
              key: 'delete',
              label: '删除',
              perm: 'payroll:fee:edit',
              confirmTitle: '删除这条额外费用？',
              onClick: async () => {
                await deletePayEmployeeFeeApi(row.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '员工', dataIndex: 'employeeName', width: 120 },
    {
      title: '归属月份',
      dataIndex: 'feeMonth',
      width: 120,
      valueType: 'dateMonth',
      initialValue: dayjs(),
      fieldProps: { format: 'YYYY-MM', allowClear: true },
      render: (_, row) => row.feeMonth,
    },
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 160,
      valueType: 'select',
      fieldProps: {
        options: depts.map((item) => ({ label: item.deptName, value: item.id })),
        allowClear: true,
        showSearch: true,
      },
      render: (_, row) => row.deptName || '-',
    },
    {
      title: '费用类目',
      dataIndex: 'categoryId',
      width: 200,
      valueType: 'select',
      fieldProps: {
        options: categories.map((item) => ({ label: item.categoryName, value: item.id })),
        allowClear: true,
      },
      render: (_, row) => row.categoryName,
    },
    {
      title: '类型',
      dataIndex: 'feeKind',
      width: 100,
      search: false,
      render: (_, row) => (row.feeKind === 'add' ? <Tag color='success'>加钱类</Tag> : <Tag color='error'>扣费类</Tag>),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (_, row) => {
        const value = Number(row.amount).toFixed(2);
        const color = row.feeKind === 'add' ? 'text-emerald-600' : 'text-red-600';
        return <span className={color}>{row.feeKind === 'add' ? `+¥${value}` : `-¥${value}`}</span>;
      },
    },
    {
      title: '截图',
      dataIndex: 'imageUrl',
      width: 90,
      search: false,
      render: (_, row) =>
        row.imageUrl ? (
          <Image
            width={48}
            src={resolveUploadUrl(row.imageUrl)}
          />
        ) : (
          '-'
        ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, search: false },
  ];

  return (
    <>
      <p className='mb-3 text-sm text-neutral-500'>
        核算工资时记在这里，每笔费用要选归属月份。新入职人员碳14费用固定为入职当月，不能改。「个税」类目（扣费类）一期先手工录入，生成工资明细时写入个税列；其他扣费进其他扣款，加钱类进补发。
      </p>
      <BaseProTable<PayEmployeeFee>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        scroll={{ x: 1200 }}
        headerTitle='额外费用'
        toolBarRender={() => [
          <PermissionButton
            key='category'
            perm='payroll:fee:edit'
            onClick={() => setCategoryOpen(true)}
          >
            费用类目
          </PermissionButton>,
          <PermissionButton
            key='add'
            perm='payroll:fee:edit'
            type='primary'
            onClick={() => void openForm()}
          >
            新增费用
          </PermissionButton>,
        ]}
        request={async (params) => {
          const query = tools.handleSearchParams(params);
          if (query.feeMonth) query.feeMonth = dayjs(query.feeMonth).format('YYYY-MM');
          const res = await pagePayEmployeeFeesApi(query);
          return { data: res?.rows ?? [], total: res?.total ?? 0, success: true };
        }}
      />
      <Modal
        title={editing ? '编辑额外费用' : '新增额外费用'}
        open={open}
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='employeeId'
            label='员工'
            rules={[{ required: true, message: '请选择员工' }]}
          >
            <Select
              showSearch
              optionFilterProp='label'
              options={employees.map((item) => ({
                value: item.id,
                label: item.deptName ? `${item.employeeName}（${item.deptName}）` : item.employeeName,
              }))}
              onChange={(value) => {
                if (hireLocked) form.setFieldValue('feeMonth', hireMonthOf(value));
              }}
            />
          </Form.Item>
          <Form.Item
            name='feeMonth'
            label='归属月份'
            rules={[{ required: true, message: '请选择归属月份' }]}
          >
            <DatePicker
              picker='month'
              className='w-full!'
              disabled={hireLocked}
            />
          </Form.Item>
          {hireLocked ? <p className='-mt-3 mb-3 text-xs text-neutral-500'>按入职月份，不能修改</p> : null}
          <Form.Item
            name='categoryId'
            label='费用类目'
            rules={[{ required: true, message: '请选择费用类目' }]}
          >
            <Select
              options={categories.map((item) => ({ value: item.id, label: item.categoryName }))}
              onChange={(value) => {
                const locked = categories.find((item) => item.id === value)?.monthRule === 'hire';
                form.setFieldValue('feeMonth', locked ? hireMonthOf(watchedEmployeeId) : dayjs());
              }}
            />
          </Form.Item>
          <Form.Item
            name='amount'
            label='金额'
            rules={[{ required: true, message: '请填写金额' }]}
          >
            <InputNumber
              min={0.01}
              precision={2}
              className='w-full!'
              prefix='¥'
            />
          </Form.Item>
          <Form.Item
            name='imageUrl'
            hidden
            rules={[{ required: true, message: '请上传截图' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label='截图'
            required
            shouldUpdate
          >
            {() => (
              <>
                <Upload
                  accept='image/png,image/jpeg,image/webp,image/gif'
                  listType='picture-card'
                  maxCount={1}
                  fileList={fileList}
                  beforeUpload={(file) => {
                    if (file.size > 5 * 1024 * 1024) {
                      message.warning('截图不能超过5MB');
                      return Upload.LIST_IGNORE;
                    }
                    return true;
                  }}
                  customRequest={async (options) => {
                    try {
                      const result = await uploadPayFeeImageApi(options.file as File);
                      form.setFieldValue('imageUrl', result.url);
                      setFileList([
                        {
                          uid: result.url,
                          name: '截图',
                          status: 'done',
                          url: resolveUploadUrl(result.url),
                        },
                      ]);
                      options.onSuccess?.(result);
                    } catch (error) {
                      options.onError?.(error as Error);
                    }
                  }}
                  onRemove={() => {
                    form.setFieldValue('imageUrl', '');
                    setFileList([]);
                  }}
                >
                  {fileList.length > 0 ? null : (
                    <div>
                      <PlusOutlined />
                      <div className='mt-1'>上传</div>
                    </div>
                  )}
                </Upload>
                {form.getFieldError('imageUrl').length > 0 ? (
                  <div className='text-sm text-red-500'>请上传截图</div>
                ) : null}
              </>
            )}
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title='费用类目'
        open={categoryOpen}
        footer={null}
        onCancel={() => setCategoryOpen(false)}
        destroyOnHidden
      >
        <div className='mb-3 flex flex-wrap gap-2'>
          <Input
            className='min-w-40 flex-1'
            value={categoryName}
            maxLength={40}
            placeholder='新的费用类目'
            onChange={(event) => setCategoryName(event.target.value)}
            onPressEnter={() => void addCategory()}
          />
          <Select
            className='w-28'
            value={categoryFeeKind}
            options={[
              { value: 'deduct', label: '扣费类' },
              { value: 'add', label: '加钱类' },
            ]}
            onChange={(value) => setCategoryFeeKind(value)}
          />
          <PermissionButton
            perm='payroll:fee:edit'
            type='primary'
            loading={categorySaving}
            onClick={() => void addCategory()}
          >
            添加
          </PermissionButton>
        </div>
        <div className='mb-2 grid grid-cols-[1fr_100px_56px] gap-3 px-1 text-xs text-neutral-400'>
          <span>类目</span>
          <span>类型</span>
          <span>操作</span>
        </div>
        <div className='flex flex-col gap-2'>
          {categories.map((item) => (
            <div
              key={item.id}
              className='grid grid-cols-[1fr_100px_56px] items-center gap-3 border-b border-neutral-100 py-2'
            >
              <span>
                {item.categoryName}
                {item.monthRule === 'hire' ? <span className='ml-2 text-xs text-neutral-400'>归属入职月</span> : null}
              </span>
              <Select
                size='small'
                value={item.feeKind || 'deduct'}
                options={[
                  { value: 'deduct', label: '扣费类' },
                  { value: 'add', label: '加钱类' },
                ]}
                onChange={(value) => void changeCategoryKind(item, value)}
              />
              <ActionButtons
                items={[
                  {
                    key: 'delete',
                    label: '删除',
                    perm: 'payroll:fee:edit',
                    confirmTitle: '删除该类目？',
                    onClick: async () => {
                      await deletePayFeeCategoryApi(item.id);
                      message.success('类目已删除');
                      await loadCategories();
                    },
                  },
                ]}
              />
            </div>
          ))}
        </div>
      </Modal>
    </>
  );
});

export default PayrollFeePage;
