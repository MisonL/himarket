/**
 * 开发者认证相关接口（OIDC/OAuth）
 */

import request, { type RespI } from "../request";

// ============ 类型定义 ============

export interface IIdpProvider {
  provider: string;
  name?: string;
  displayName?: string;
  sloEnabled?: boolean;
}

export interface IAuthResult {
  access_token: string;
}

export interface IIdentity {
  provider: string;
  displayName: string;
  rawInfoJson: string;
}

interface OidcCallbackParams {
  code: string;
  state: string;
}

interface CasCallbackParams {
  ticket: string;
  state: string;
}

interface IDeveloperInfo {
  username: string;
  email: string;
  createdAt: string;
  avatarUrl?: string
}

// ============ API 函数 ============

/**
 * 获取用户登陆信息
 */
export function getDeveloperInfo() {
  return request.get<RespI<IDeveloperInfo>, RespI<IDeveloperInfo>>(
    '/developers/profile'
  );
}

/**
 * 获取 OIDC 提供商列表
 */
export function getOidcProviders() {
  return request.get<RespI<IIdpProvider[]>, RespI<IIdpProvider[]>>(
    '/developers/oidc/providers'
  );
}

export function getCasProviders() {
  return request.get<RespI<IIdpProvider[]>, RespI<IIdpProvider[]>>(
    '/developers/cas/providers'
  );
}

export function getLdapProviders() {
  return request.get<RespI<IIdpProvider[]>, RespI<IIdpProvider[]>>(
    '/developers/ldap/providers'
  );
}

/**
 * OIDC 回调处理
 */
export function handleOidcCallback(params: OidcCallbackParams) {
  return request.get<RespI<IAuthResult>, RespI<IAuthResult>>(
    '/developers/oidc/callback',
    {
      params: {
        code: params.code,
        state: params.state,
      },
    }
  );
}

export function handleCasCallback(params: CasCallbackParams) {
  return request.get<RespI<IAuthResult>, RespI<IAuthResult>>(
    '/developers/cas/callback',
    {
      params: {
        ticket: params.ticket,
        state: params.state,
      },
    }
  );
}

export function handleLdapLogin(params: LdapLoginParam) {
  return request.post<RespI<IAuthResult>, RespI<IAuthResult>>(
    '/developers/ldap/login',
    params
  );
}

/**
 * 开发者登出
 */
export function developerLogout() {
  return request.post<RespI<void>, RespI<void>>(
    '/developers/logout'
  );
}


export function developersListIdentities() {
  return request.post<RespI<IIdentity[]>, RespI<IIdentity[]>>(
    '/developers/list-identities'
  );
}

interface LdapLoginParam {
  provider: string;
  username: string;
  password: string;
}
