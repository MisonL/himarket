export interface AuthCodeConfig {
  clientId: string;
  clientSecret: string;
  scopes: string;
  authorizationEndpoint: string;
  tokenEndpoint: string;
  userInfoEndpoint: string;
  jwkSetUri: string;
  // OIDC issuer地址（用于自动发现模式）
  issuer?: string;
  // 可选的身份映射配置
  identityMapping?: IdentityMapping;
}

export interface OidcConfig {
  provider: string;
  name: string;
  logoUrl?: string | null;
  enabled: boolean;
  grantType: "AUTHORIZATION_CODE";
  authCodeConfig: AuthCodeConfig;
  identityMapping?: IdentityMapping;
}

export type CasProtocolVersion = "CAS1" | "CAS2" | "CAS3" | "SAML1";

export type CasValidationResponseFormat = "XML" | "JSON";

export type CasServiceResponseType = "REDIRECT" | "POST" | "HEADER";

export type CasServiceLogoutType = "NONE" | "BACK_CHANNEL" | "FRONT_CHANNEL";

export interface CasLoginConfig {
  gateway?: boolean;
  renew?: boolean;
  warn?: boolean;
  rememberMe?: boolean;
}

export interface CasValidationConfig {
  protocolVersion?: CasProtocolVersion;
  responseFormat?: CasValidationResponseFormat;
}

export interface CasProxyConfig {
  enabled?: boolean;
  callbackPath?: string;
  callbackUrlPattern?: string;
  proxyEndpoint?: string;
  targetServicePattern?: string;
  policyMode?: "REGEX" | "REST" | "REFUSE";
  useServiceId?: boolean;
  exactMatch?: boolean;
  policyEndpoint?: string;
  policyHeaders?: Record<string, string>;
}

export interface CasServiceDefinitionConfig {
  serviceIdPattern?: string;
  serviceId?: number;
  evaluationOrder?: number;
  responseType?: CasServiceResponseType;
  logoutType?: CasServiceLogoutType;
  logoutUrl?: string;
}

export interface CasDelegatedAuthenticationPolicyConfig {
  allowedProviders?: string[];
  permitUndefined?: boolean;
  exclusive?: boolean;
}

export interface CasHttpRequestAccessStrategyConfig {
  ipAddressPattern?: string;
  userAgentPattern?: string;
  headers?: Record<string, string>;
}

export interface CasAccessStrategyConfig {
  enabled?: boolean;
  ssoEnabled?: boolean;
  unauthorizedRedirectUrl?: string;
  startingDateTime?: string;
  endingDateTime?: string;
  zoneId?: string;
  requireAllAttributes?: boolean;
  caseInsensitive?: boolean;
  requiredAttributes?: Record<string, string[]>;
  rejectedAttributes?: Record<string, string[]>;
  delegatedAuthenticationPolicy?: CasDelegatedAuthenticationPolicyConfig;
  httpRequest?: CasHttpRequestAccessStrategyConfig;
}

export interface CasAttributeReleasePolicyConfig {
  mode?: "RETURN_ALLOWED" | "RETURN_ALL" | "DENY_ALL";
  allowedAttributes?: string[];
}

export interface CasMultifactorPolicyConfig {
  providers?: string[];
  bypassEnabled?: boolean;
  forceExecution?: boolean;
  failureMode?: "UNDEFINED" | "OPEN" | "CLOSED" | "PHANTOM" | "NONE";
  bypassPrincipalAttributeName?: string;
  bypassPrincipalAttributeValue?: string;
  bypassIfMissingPrincipalAttribute?: boolean;
}

export interface CasAuthenticationPolicyConfig {
  criteriaMode?: "ALLOWED" | "EXCLUDED" | "ANY" | "ALL" | "NOT_PREVENTED";
  requiredAuthenticationHandlers?: string[];
  excludedAuthenticationHandlers?: string[];
  tryAll?: boolean;
}

export interface CasExpirationPolicyConfig {
  expirationDate?: string;
  deleteWhenExpired?: boolean;
  notifyWhenExpired?: boolean;
  notifyWhenDeleted?: boolean;
}

export interface CasServiceContactConfig {
  name?: string;
  email?: string;
  phone?: string;
  department?: string;
  type?: string;
}

export interface CasConfig {
  provider: string;
  name: string;
  enabled: boolean;
  sloEnabled?: boolean;
  serverUrl: string;
  loginEndpoint?: string;
  validateEndpoint?: string;
  logoutEndpoint?: string;
  login?: CasLoginConfig;
  validation?: CasValidationConfig;
  proxy?: CasProxyConfig;
  serviceDefinition?: CasServiceDefinitionConfig;
  accessStrategy?: CasAccessStrategyConfig;
  attributeRelease?: CasAttributeReleasePolicyConfig;
  multifactorPolicy?: CasMultifactorPolicyConfig;
  authenticationPolicy?: CasAuthenticationPolicyConfig;
  expirationPolicy?: CasExpirationPolicyConfig;
  contacts?: CasServiceContactConfig[];
  identityMapping?: IdentityMapping;
}

export interface LdapConfig {
  provider: string;
  name: string;
  enabled: boolean;
  serverUrl: string;
  baseDn: string;
  bindDn?: string;
  bindPassword?: string;
  userSearchFilter?: string;
  identityMapping?: IdentityMapping;
}

// 第三方认证相关类型定义
export enum AuthenticationType {
  OIDC = "OIDC",
  CAS = "CAS",
  LDAP = "LDAP",
  OAUTH2 = "OAUTH2",
}

export enum GrantType {
  AUTHORIZATION_CODE = "AUTHORIZATION_CODE",
  JWT_BEARER = "JWT_BEARER",
}

export enum PublicKeyFormat {
  PEM = "PEM",
  JWK = "JWK",
}

export interface PublicKeyConfig {
  kid: string;
  format: PublicKeyFormat;
  algorithm: string;
  value: string;
}

export interface JwtBearerConfig {
  issuer?: string;
  jwkSetUri?: string;
  audiences?: string[];
  publicKeys?: PublicKeyConfig[];
}

export interface IdentityMapping {
  userIdField?: string | null;
  userNameField?: string | null;
  emailField?: string | null;
  customFields?: { [key: string]: string } | null;
}

// OAuth2配置（使用现有格式）
export interface OAuth2Config {
  provider: string;
  name: string;
  enabled: boolean;
  grantType: GrantType;
  jwtBearerConfig?: JwtBearerConfig;
  identityMapping?: IdentityMapping;
}

// 为了UI显示方便，给配置添加类型标识的联合类型
export type ThirdPartyAuthConfig =
  | (OidcConfig & { type: AuthenticationType.OIDC })
  | (CasConfig & { type: AuthenticationType.CAS })
  | (LdapConfig & { type: AuthenticationType.LDAP })
  | (OAuth2Config & { type: AuthenticationType.OAUTH2 });

export interface PortalSettingConfig {
  builtinAuthEnabled: boolean;
  autoApproveDevelopers: boolean;
  autoApproveSubscriptions: boolean;
  frontendRedirectUrl?: string;

  // 第三方认证配置（分离存储）
  oidcConfigs?: OidcConfig[];
  casConfigs?: CasConfig[];
  ldapConfigs?: LdapConfig[];
  oauth2Configs?: OAuth2Config[];
}

export interface PortalUiConfig {
  logo: string | null;
  icon: string | null;
  menuVisibility?: Record<string, boolean> | null;
}

export interface PortalDomainConfig {
  domain: string;
  type: string;
  protocol: string;
}

export interface Portal {
  portalId: string;
  name: string;
  title: string;
  description: string;
  adminId: string;
  portalSettingConfig: PortalSettingConfig;
  portalUiConfig: PortalUiConfig;
  portalDomainConfig: PortalDomainConfig[];
}

export interface Developer {
  portalId: string;
  developerId: string;
  username: string;
  status: string;
  avatarUrl?: string;
  createAt: string;
}
