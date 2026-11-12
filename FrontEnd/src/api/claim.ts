import request from './request';
import type { PageQuery, PageResult } from '@/types/api';
import { getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';

export type ClaimCase = {
  id: number;
  conversationId?: string;
  senderId?: string;
  senderName?: string;
  status?: string;
  customerName?: string;
  customerPhone?: string;
  customerAddress?: string;
  memberNo?: string;
  customerType?: string;
  consumeSummary?: string;
  productCategory?: string;
  productName?: string;
  productSpec?: string;
  storeName?: string;
  purchaseDate?: string;
  orderNo?: string;
  logisticsNo?: string;
  logisticsSummary?: string;
  symptom?: string;
  symptomCode?: string;
  cookingMethod?: string;
  customerDemand?: string;
  sopTitle?: string;
  recommendation?: string;
  recommendationBasis?: string;
  createTime?: string;
  updateTime?: string;
};

export type ClaimMessage = {
  id: number;
  roleName?: string;
  msgType?: string;
  content?: string;
  createTime?: string;
};

export type ClaimEvidence = {
  id: number;
  fileType?: string;
  fileName?: string;
  fileSize?: number;
  createTime?: string;
};

export type ClaimHistory = {
  id: number;
  sourceYear?: string;
  sourceFile?: string;
  complaintDate?: string;
  purchaseDate?: string;
  complaintType?: string;
  storeName?: string;
  customerName?: string;
  customerPhone?: string;
  customerAddress?: string;
  complaintContent?: string;
  productCategory?: string;
  productName?: string;
  symptomBrief?: string;
  claimOpinion?: string;
  claimProduct?: string;
  judgment?: string;
  thirdPartyAmount?: string;
  fundAmount?: string;
  dutyAmount?: string;
  freight?: string;
  resultText?: string;
  adminResult?: string;
  dutyDept?: string;
  handlerName?: string;
  manualEdited?: number;
};

export type ClaimArchiveDetail = {
  archive: ClaimCase;
  messages: ClaimMessage[];
  evidences: ClaimEvidence[];
  similarCases: ClaimHistory[];
};

export type ClaimSopNode = {
  id: number;
  nodeCode: string;
  parentCode?: string;
  nodeType: string;
  title: string;
  content?: string;
  keywords?: string;
  sortOrder?: number;
  children?: ClaimSopNode[];
};

export type ClaimRobot = {
  clientId?: string;
  clientSecretMasked?: string;
  hasClientSecret?: boolean;
  robotCode?: string;
  enabled?: number;
  connected?: boolean;
};

export type ClaimImportResult = {
  inserted: number;
  updated: number;
  skipped: number;
  message?: string;
};

export function getClaimArchiveListApi(data: PageQuery & Record<string, unknown>) {
  return request.post<unknown, PageResult<ClaimCase>>('/claim/archive/list', data);
}

export function getClaimArchiveApi(id: number) {
  return request.get<unknown, ClaimArchiveDetail>(`/claim/archive/${id}`);
}

export function updateClaimArchiveApi(data: ClaimCase) {
  return request.put('/claim/archive', data);
}

export function recommendClaimArchiveApi(id: number) {
  return request.post<unknown, ClaimCase>(`/claim/archive/${id}/recommend`);
}

export function deleteClaimArchiveApi(id: number) {
  return request.delete(`/claim/archive/${id}`);
}

export function getClaimSopTreeApi() {
  return request.get<unknown, ClaimSopNode[]>('/claim/sop/tree');
}

export function saveClaimSopApi(data: Partial<ClaimSopNode> & { id?: number }, creating: boolean) {
  return creating ? request.post('/claim/sop', data) : request.put('/claim/sop', data);
}

export function deleteClaimSopApi(id: number) {
  return request.delete(`/claim/sop/${id}`);
}

export function getClaimHistoryListApi(data: PageQuery & Record<string, unknown>) {
  return request.post<unknown, PageResult<ClaimHistory>>('/claim/history/list', data);
}

export function updateClaimHistoryApi(data: ClaimHistory) {
  return request.put('/claim/history', data);
}

export function importClaimHistoryApi() {
  return request.post<unknown, ClaimImportResult>('/claim/history/import');
}

export function getClaimRobotApi() {
  return request.get<unknown, ClaimRobot>('/claim/robot');
}

export function saveClaimRobotApi(data: {
  clientId?: string;
  clientSecret?: string;
  robotCode?: string;
  enabled?: number;
}) {
  return request.put('/claim/robot', data);
}

export function testClaimRobotApi(data: { clientId?: string; clientSecret?: string }) {
  return request.post('/claim/robot/test', data);
}

export async function openClaimEvidence(id: number) {
  const response = await fetch(withBase(`/api/claim/archive/evidence/${id}`), {
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  });
  if (!response.ok) {
    throw new Error('证据下载失败');
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
}

export type ClaimRefCase = {
  id: number;
  score: string;
  kind: string;
  complaintDate?: string;
  customerName?: string;
  productName?: string;
  symptomBrief?: string;
  claimOpinion?: string;
  judgment?: string;
  resultText?: string;
};

export type ClaimRefPage = {
  symptom?: string;
  tabs: { score: string; cases: ClaimRefCase[] }[];
};

export function getClaimRefsApi(token: string) {
  return request.get<unknown, ClaimRefPage>(`/claim/refs/${encodeURIComponent(token)}`);
}
