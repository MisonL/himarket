import type { Product } from "./index";

export type CredentialType = "API_KEY" | "HMAC" | "JWT";

export interface Consumer {
  consumerId: string;
  name: string;
  description?: string;
  status?: string;
  createAt?: string;
  enabled?: boolean;
  credentialType?: CredentialType;
}

export type ConsumerCredential = HMACCredential | APIKeyCredential;

export interface HMACCredential {
  ak?: string;
  sk?: string;
  mode?: "SYSTEM" | "CUSTOM";
}

export interface APIKeyCredential {
  apiKey?: string;
  mode?: "SYSTEM" | "CUSTOM";
}

export interface JWTFromHeader {
  name?: string;
  valuePrefix?: string;
}

export interface JWTClaimToHeader {
  claim?: string;
  header?: string;
  override?: boolean;
}

export interface JWTCredentialConfig {
  issuer?: string;
  jwks?: string;
  fromHeaders?: JWTFromHeader[];
  fromParams?: string[];
  fromCookies?: string[];
  claimsToHeaders?: JWTClaimToHeader[];
  clockSkewSeconds?: number;
  keepToken?: boolean;
}

export interface APIKeyCredentialConfig {
  credentials?: APIKeyCredential[];
  source?: string;
  key?: string;
}

export interface HMACCredentialConfig {
  credentials?: HMACCredential[];
}

export interface ConsumerCredentialResult {
  credentialType?: CredentialType;
  apiKeyConfig?: APIKeyCredentialConfig;
  hmacConfig?: HMACCredentialConfig;
  jwtConfig?: JWTCredentialConfig;
}

export interface Subscription {
  productId: string;
  consumerId: string;
  status: "PENDING" | "APPROVED";
  createAt: string;
  updatedAt: string;
  productName: string;
  productType: "REST_API" | "MCP_SERVER" | "AGENT_API" | "MODEL_API";
  consumerName?: string;
  product?: Product;
}

export interface CreateCredentialParam {
  credentialType?: CredentialType;
  apiKeyConfig?: APIKeyCredentialConfig;
  hmacConfig?: HMACCredentialConfig;
  jwtConfig?: JWTCredentialConfig;
}
