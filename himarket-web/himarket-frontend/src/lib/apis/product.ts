/**
 * 模型相关接口
 */

import request, { type RespI } from "../request";
import type {
  IAgentConfig,
  IAPIConfig,
  IMCPConfig,
  IModelConfig,
  IProductIcon,
  ISkillConfig,
} from "./typing";
import type { CredentialType } from "../../types/consumer";

export interface IProductDetail {
  productId: string;
  name: string;
  description: string;
  status: string;
  enableConsumerAuth: boolean;
  type: string;
  document: string | null;
  icon?: IProductIcon;
  categories: {
    categoryId: string;
    name: string;
    description: string;
    icon: {
      type: string;
      value: string;
    };
    createAt: string;
    updatedAt: string;
  }[];
  autoApprove: boolean | null;
  requiredCredentialType?: CredentialType | null;
  createAt: string;
  updatedAt: string;
  apiConfig: IAPIConfig;
  agentConfig: IAgentConfig;
  mcpConfig: IMCPConfig;
  modelConfig?: IModelConfig;
  skillConfig?: ISkillConfig;
  enabled: boolean;
  feature?: {
    modelFeature: {
      model: string;
      webSearch: boolean;
      enableMultiModal: boolean;
    };
  };
}

interface GetProductsResp {
  content: IProductDetail[];
  number: number;
  size: number;
  totalElements: number;
}
// 获取模型列表
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  name?: string;
  page?: number;
  size?: number;
  ["modelFilter.category"]?: "Image" | "TEXT";
}) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>(
    "/products",
    {
      params: {
        name: params.name,
        type: params.type,
        categoryIds: params.categoryIds,
        page: params.page || 0,
        size: params.size || 100,
        ["modelFilter.category"]: params["modelFilter.category"],
      },
    }
  );
}

export function getProduct(params: { id: string }) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>(
    "/products/" + params.id
  );
}

// MCP 工具列表相关类型
export interface IMcpTool {
  name: string;
  description: string;
  inputSchema: {
    type: string;
    properties?: Record<
      string,
      {
        type?: string;
        description?: string;
        enum?: string[];
        items?: unknown;
        properties?: Record<string, unknown>;
      }
    >;
    required?: string[];
    additionalProperties?: boolean;
  };
}

export interface IMcpToolsListResp {
  nextCursor?: string;
  tools: IMcpTool[];
}

// 获取 MCP 服务的工具列表
export function getMcpTools(params: { productId: string }) {
  return request.get<RespI<IMcpToolsListResp>, RespI<IMcpToolsListResp>>(
    `/products/${params.productId}/tools`
  );
}
