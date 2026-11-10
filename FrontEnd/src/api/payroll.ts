import request from './request';
import type { PageResult } from '@/types/api';
import { getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';

export type PayDept = {
  id: number;
  deptName: string;
  sortOrder: number;
  status: number;
};

export type PayPost = {
  id: number;
  postName: string;
  sortOrder: number;
  status: number;
};

export type PayGrade = {
  id: number;
  gradeName: string;
  sortOrder: number;
  status: number;
};

export type PayLateRule = {
  id: number;
  gradeId: number;
  gradeName: string;
  allowCount: number;
  personal1Amount: number;
  personal2Amount: number;
  amtVerbalPraise: number;
  amtCommendation: number;
  amtMerit: number;
  amtMajorMerit: number;
  amtVerbalCritic: number;
  amtBulletinCritic: number;
  amtDemerit: number;
  amtMajorDemerit: number;
};

export type PayEmployee = {
  id: number;
  deptId: number;
  deptName?: string;
  postId: number;
  postName?: string;
  gradeId?: number | null;
  gradeName?: string;
  employeeName: string;
  hireDate: string;
  salaryStandard: number;
  amtPost: number;
  amtFullAttendance: number;
  amtSocialSubsidy: number;
  amtHousingSubsidy: number;
  amtTransport: number;
  amtNoncompete: number;
  amtHygiene: number;
  amtFixedOvertime: number;
  employeeNo?: string;
  phone?: string;
  idCard?: string;
  bankAccount?: string;
  bankName?: string;
  jobStatus: number;
  hukouType?: string;
  socialBase?: number | null;
  socialAccount?: string;
};

export function getPayDeptsApi() {
  return request.get<unknown, PayDept[]>('/payroll/base/depts');
}

export function pagePayDeptsApi(params: { deptName?: string; status?: number; pageNum?: number; pageSize?: number }) {
  return request.post<unknown, PageResult<PayDept>>('/payroll/base/depts/list', params);
}

export function savePayDeptApi(data: Partial<PayDept>) {
  return request.post('/payroll/base/depts', data);
}

export function deletePayDeptApi(id: number) {
  return request.delete(`/payroll/base/depts/${id}`);
}

export function getPayPostsApi() {
  return request.get<unknown, PayPost[]>('/payroll/base/posts');
}

export function pagePayPostsApi(params: { postName?: string; status?: number; pageNum?: number; pageSize?: number }) {
  return request.post<unknown, PageResult<PayPost>>('/payroll/base/posts/list', params);
}

export function savePayPostApi(data: Partial<PayPost>) {
  return request.post('/payroll/base/posts', data);
}

export function deletePayPostApi(id: number) {
  return request.delete(`/payroll/base/posts/${id}`);
}

export function getPayGradesApi() {
  return request.get<unknown, PayGrade[]>('/payroll/base/grades');
}

export function pagePayGradesApi(params: { gradeName?: string; status?: number; pageNum?: number; pageSize?: number }) {
  return request.post<unknown, PageResult<PayGrade>>('/payroll/base/grades/list', params);
}

export function savePayGradeApi(data: Partial<PayGrade>) {
  return request.post('/payroll/base/grades', data);
}

export function deletePayGradeApi(id: number) {
  return request.delete(`/payroll/base/grades/${id}`);
}

export function getPayLateRulesApi() {
  return request.get<unknown, PayLateRule[]>('/payroll/base/late-rules');
}

export function savePayLateRuleApi(
  data: Pick<
    PayLateRule,
    | 'id'
    | 'allowCount'
    | 'personal1Amount'
    | 'personal2Amount'
    | 'amtVerbalPraise'
    | 'amtCommendation'
    | 'amtMerit'
    | 'amtMajorMerit'
    | 'amtVerbalCritic'
    | 'amtBulletinCritic'
    | 'amtDemerit'
    | 'amtMajorDemerit'
  >,
) {
  return request.put('/payroll/base/late-rules', data);
}

export function getPayEmployeeListApi(data: Record<string, unknown>) {
  return request.post<unknown, PageResult<PayEmployee>>('/payroll/employee/list', data);
}

export function savePayEmployeeApi(data: Partial<PayEmployee>) {
  return request.post('/payroll/employee', data);
}

export function deletePayEmployeeApi(id: number) {
  return request.delete(`/payroll/employee/${id}`);
}

export type PayDingTalkSyncResult = {
  deptCount: number;
  employeeCount: number;
  created: number;
  updated: number;
  resigned: number;
};

export function syncPayEmployeeFromDingTalkApi() {
  return request.post<unknown, PayDingTalkSyncResult>('/payroll/employee/sync-dingtalk', {}, { timeout: 120000 });
}

export type PayWageExportJob = {
  jobId: string;
  period: string;
  status: 'pending' | 'running' | 'done' | 'failed' | string;
  percent: number;
  message?: string;
  fileName?: string;
};

export function startPayWageExportApi(period: string) {
  return request.post<unknown, PayWageExportJob>('/payroll/employee/wage-export/start', { period }, { timeout: 60000 });
}

export function getPayWageExportProgressApi(jobId: string) {
  return request.get<unknown, PayWageExportJob>(`/payroll/employee/wage-export/${jobId}/progress`);
}

export async function downloadPayWageExportApi(jobId: string, fileName?: string) {
  const token = getToken();
  const res = await fetch(withBase(`/api/payroll/employee/wage-export/${jobId}/download`), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error('下载失败');
  }
  const blob = await res.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.style.display = 'none';
  a.href = objectUrl;
  a.download = fileName || '员工工资明细.xlsx';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(objectUrl);
}

export type PaySocialRuleItem = {
  id: number;
  itemCode: string;
  itemName: string;
  employerRate: number;
  personalRate: number;
  baseMin?: number | null;
  baseMax?: number | null;
  sortOrder: number;
};

export type PaySocialRule = {
  id: number;
  ruleCode: string;
  regionName: string;
  hukouType: string;
  baseMin: number;
  baseMax: number;
  remark: string;
  sortOrder: number;
  items: PaySocialRuleItem[];
};

export function getPaySocialRulesApi() {
  return request.get<unknown, PaySocialRule[]>('/payroll/social/rules');
}

export type PayFeeCategory = {
  id: number;
  categoryName: string;
  monthRule?: string;
  /** add=加钱类 deduct=扣费类 */
  feeKind: 'add' | 'deduct';
  sortOrder: number;
};

export type PayEmployeeFee = {
  id: number;
  employeeId: number;
  employeeName: string;
  deptName?: string;
  categoryId: number;
  categoryName: string;
  feeKind?: 'add' | 'deduct';
  feeMonth: string;
  amount: number;
  imageUrl: string;
  remark: string;
};

export function getPayFeeCategoriesApi() {
  return request.get<unknown, PayFeeCategory[]>('/payroll/fee/categories');
}

export function savePayFeeCategoryApi(data: { id?: number; categoryName: string; feeKind: 'add' | 'deduct' }) {
  return request.post('/payroll/fee/categories', data);
}

export function deletePayFeeCategoryApi(id: number) {
  return request.delete(`/payroll/fee/categories/${id}`);
}

export function pagePayEmployeeFeesApi(params: {
  employeeName?: string;
  deptId?: number;
  categoryId?: number;
  feeMonth?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayEmployeeFee>>('/payroll/fee/list', params);
}

export function savePayEmployeeFeeApi(data: {
  id?: number;
  employeeId: number;
  categoryId: number;
  feeMonth: string;
  amount: number;
  imageUrl: string;
  remark?: string;
}) {
  return request.post('/payroll/fee', data);
}

export function deletePayEmployeeFeeApi(id: number) {
  return request.delete(`/payroll/fee/${id}`);
}

export function uploadPayFeeImageApi(file: File) {
  const body = new FormData();
  body.append('file', file);
  return request.post<unknown, { url: string }>('/payroll/fee/image', body);
}

export type PayAttendance = {
  id: number;
  period: string;
  employeeName: string;
  deptName?: string;
  workDate: string;
  checkType: string;
  timeResult: string;
  userCheckTime?: string;
  baseCheckTime?: string;
};

export type PayAttendanceSummary = {
  id: number;
  period: string;
  employeeId: number;
  employeeName: string;
  deptName?: string;
  lateEarlyCount: number;
  personal1Count: number;
  personal2Count: number;
  absentCount: number;
  missingCardCount: number;
  annualLeaveDays: number;
  specialLeaveDays: number;
  actualRestDays: number;
  remainingCompDays: number;
};

export type PayBulletin = {
  id: number;
  period: string;
  title: string;
  categoryName?: string;
  author?: string;
  bulletinKind: string;
  plainContent?: string;
  publishTime?: string;
  receiverText?: string;
};

export type PayDingTalkPull = {
  period: string;
  recordCount: number;
  message: string;
};

export type PayMonthPullStatus = {
  period: string;
  attendance?: { recordCount?: number; syncedAt?: string };
  bulletin?: { recordCount?: number; syncedAt?: string };
};

export function pagePayAttendanceApi(params: {
  period?: string;
  employeeName?: string;
  deptName?: string;
  timeResult?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayAttendance>>('/payroll/attendance/list', params);
}

export function pagePayAttendanceSummaryApi(params: {
  period?: string;
  employeeName?: string;
  deptName?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayAttendanceSummary>>('/payroll/attendance/summary/list', params);
}

export function rebuildPayAttendanceSummaryApi(period: string) {
  return request.post<unknown, number>('/payroll/attendance/summary/rebuild', { period }, { timeout: 300000 });
}

export function syncPayAttendanceApi(period: string) {
  return request.post<unknown, PayDingTalkPull>('/payroll/attendance/sync', { period }, { timeout: 300000 });
}

export function pagePayBulletinsApi(params: {
  period?: string;
  title?: string;
  bulletinKind?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayBulletin>>('/payroll/bulletin/list', params);
}

export function syncPayBulletinApi(period: string) {
  return request.post<unknown, PayDingTalkPull>('/payroll/bulletin/sync', { period }, { timeout: 180000 });
}

export function getPayPullStatusApi(period: string) {
  return request.get<unknown, PayMonthPullStatus>('/payroll/dingtalk/pull-status', { params: { period } });
}

export type PayPerformance = {
  id: number;
  employeeId: number;
  employeeName?: string;
  deptName?: string;
  postName?: string;
  gradeName?: string;
  period: string;
  coefficient: number;
  remark?: string;
};

export function pagePayPerformancesApi(params: {
  period?: string;
  employeeName?: string;
  deptId?: number;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayPerformance>>('/payroll/performance/list', params);
}

export function savePayPerformanceApi(data: Partial<PayPerformance>) {
  return request.post('/payroll/performance', data);
}

export function deletePayPerformanceApi(id: number) {
  return request.delete(`/payroll/performance/${id}`);
}

export function generatePayPerformanceApi(period: string) {
  return request.post<unknown, number>('/payroll/performance/generate', { period });
}

export type PayReward = {
  id: number;
  period: string;
  employeeId: number;
  employeeName?: string;
  deptName?: string;
  gradeName?: string;
  bulletinId?: number | null;
  bulletinTitle?: string;
  rewardKind: string;
  reason?: string;
  times: number;
  unitAmount?: number | null;
  amount: number;
  amountSource?: string;
  remark?: string;
};

export function pagePayRewardsApi(params: {
  period?: string;
  employeeName?: string;
  deptId?: number;
  rewardKind?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.post<unknown, PageResult<PayReward>>('/payroll/reward/list', params);
}

export function savePayRewardApi(data: {
  id?: number;
  period: string;
  employeeId: number;
  rewardKind: string;
  reason?: string;
  times: number;
  amount: number;
  remark?: string;
}) {
  return request.post('/payroll/reward', data);
}

export function deletePayRewardApi(id: number) {
  return request.delete(`/payroll/reward/${id}`);
}

export type PayWorkCalendar = {
  year: number;
  month: number;
  calendarDays: number;
  holidayName: string;
  holidayDays: number;
  dualRest: number;
  dualWork: number;
  dualPay: number;
  singleRest: number;
  singleWork: number;
  singlePay: number;
  altRest: number;
  altWork: number;
  altPay: number;
};

export function getPayCalendarApi(year: number) {
  return request.get<unknown, PayWorkCalendar[]>('/payroll/calendar', { params: { year } });
}

export function savePayCalendarApi(year: number, months: PayWorkCalendar[]) {
  return request.put('/payroll/calendar', {
    year,
    months: months.map((row) => ({
      month: row.month,
      holidayName: row.holidayName,
      holidayDays: row.holidayDays,
      dualRest: row.dualRest,
      dualWork: row.dualWork,
      dualPay: row.dualPay,
      singleRest: row.singleRest,
      singleWork: row.singleWork,
      singlePay: row.singlePay,
      altRest: row.altRest,
      altWork: row.altWork,
      altPay: row.altPay,
    })),
  });
}

export function savePaySocialRuleApi(data: Pick<PaySocialRule, 'id' | 'baseMin' | 'baseMax' | 'remark' | 'items'>) {
  return request.put('/payroll/social/rules', {
    id: data.id,
    baseMin: data.baseMin,
    baseMax: data.baseMax,
    remark: data.remark,
    items: data.items.map((item) => ({
      id: item.id,
      employerRate: item.employerRate,
      personalRate: item.personalRate,
      baseMin: item.baseMin ?? null,
      baseMax: item.baseMax ?? null,
    })),
  });
}
