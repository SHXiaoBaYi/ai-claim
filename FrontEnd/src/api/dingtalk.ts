import request from './request';

export type DingTalkAppVO = {
  appId: string;
  agentId: string;
  clientId: string;
  corpId?: string;
  clientSecretMasked?: string;
  hasClientSecret?: boolean;
  enabled: number;
};

export type DingTalkAppDTO = {
  appId: string;
  agentId: string;
  clientId: string;
  corpId?: string;
  clientSecret?: string;
  enabled: number;
};

export function getDingTalkAppApi() {
  return request.get<unknown, DingTalkAppVO>('/system/dingtalk');
}

export function saveDingTalkAppApi(data: DingTalkAppDTO) {
  return request.put('/system/dingtalk', data);
}

export function testDingTalkAppApi(data: DingTalkAppDTO) {
  return request.post('/system/dingtalk/test', data);
}
