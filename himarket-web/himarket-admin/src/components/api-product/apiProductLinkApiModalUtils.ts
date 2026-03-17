import type {
  ApiProduct,
  NacosMCPItem,
  ApiItem,
} from '@/types/api-product'
import type { Gateway } from '@/types/gateway'

export function getApiLabel(apiProduct: ApiProduct) {
  if (apiProduct.type === 'REST_API') {
    return 'REST API'
  }
  if (apiProduct.type === 'AGENT_API') {
    return 'Agent API'
  }
  if (apiProduct.type === 'MODEL_API') {
    return 'Model API'
  }
  return 'MCP Server'
}

export function getSupportedGateways(
  apiProduct: ApiProduct,
  gateways: Gateway[]
) {
  return gateways.filter(gateway => {
    if (apiProduct.type === 'AGENT_API') {
      return gateway.gatewayType === 'APIG_AI'
    }
    if (apiProduct.type === 'MODEL_API') {
      return (
        gateway.gatewayType === 'APIG_AI' ||
        gateway.gatewayType === 'HIGRESS' ||
        gateway.gatewayType === 'ADP_AI_GATEWAY' ||
        gateway.gatewayType === 'APSARA_GATEWAY'
      )
    }
    return true
  })
}

export function getApiOptionValue(
  apiProduct: ApiProduct,
  api: ApiItem | NacosMCPItem | any
) {
  if (apiProduct.type === 'REST_API') {
    return {
      key: api.apiId,
      value: api.apiId,
      displayName: api.apiName,
    }
  }

  if (apiProduct.type === 'AGENT_API') {
    if ('agentName' in api) {
      return {
        key: api.agentName,
        value: api.agentName,
        displayName: api.agentName,
      }
    }

    return {
      key: api.agentApiId || api.agentApiName,
      value: api.agentApiId || api.agentApiName,
      displayName: api.agentApiName,
    }
  }

  if (apiProduct.type === 'MODEL_API') {
    if (api.fromGatewayType === 'HIGRESS') {
      return {
        key: api.modelRouteName,
        value: api.modelRouteName,
        displayName: api.modelRouteName,
      }
    }

    return {
      key: api.modelApiId || api.modelApiName,
      value: api.modelApiId || api.modelApiName,
      displayName: api.modelApiName,
    }
  }

  return {
    key: api.mcpRouteId || api.mcpServerName || api.name,
    value: api.mcpRouteId || api.mcpServerName || api.name,
    displayName: api.mcpServerName || api.name,
  }
}
