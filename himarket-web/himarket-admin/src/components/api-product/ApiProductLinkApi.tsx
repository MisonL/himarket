import { Card, Button, Modal, Form, Select, message, Collapse, Tabs } from 'antd'
import { PlusOutlined, DeleteOutlined, ExclamationCircleOutlined, CopyOutlined } from '@ant-design/icons'
import { useState, useEffect } from 'react'
import type { ApiProduct, LinkedService, RestAPIItem, NacosMCPItem, APIGAIMCPItem, AIGatewayAgentItem, AIGatewayModelItem, ApiItem, AdpAIGatewayModelItem, ApsaraGatewayModelItem } from '@/types/api-product'
import type { Gateway, NacosInstance } from '@/types/gateway'
import { apiProductApi, gatewayApi, nacosApi } from '@/lib/api'
import { copyToClipboard, formatDomainWithPort } from '@/lib/utils'
import * as yaml from 'js-yaml'
import { ApiProductAgentConfigCard } from './ApiProductAgentConfigCard'
import { ApiProductLinkApiModal } from './ApiProductLinkApiModal'
import { ApiProductMcpConfigCard } from './ApiProductMcpConfigCard'
import { ApiProductModelConfigCard } from './ApiProductModelConfigCard'
import { SwaggerUIWrapper } from './SwaggerUIWrapper'

interface ApiProductLinkApiProps {
  apiProduct: ApiProduct
  linkedService: LinkedService | null
  onLinkedServiceUpdate: (linkedService: LinkedService | null) => void
  handleRefresh: () => void
}

export function ApiProductLinkApi({ apiProduct, linkedService, onLinkedServiceUpdate, handleRefresh }: ApiProductLinkApiProps) {
  // 移除了内部的 linkedService 状态，现在从 props 接收
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [form] = Form.useForm()
  const [gateways, setGateways] = useState<Gateway[]>([])
  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([])
  const [gatewayLoading, setGatewayLoading] = useState(false)
  const [nacosLoading, setNacosLoading] = useState(false)
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null)
  const [selectedNacos, setSelectedNacos] = useState<NacosInstance | null>(null)
  const [nacosNamespaces, setNacosNamespaces] = useState<any[]>([])
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>(null)
  const [apiList, setApiList] = useState<ApiItem[] | NacosMCPItem[]>([])
  const [apiLoading, setApiLoading] = useState(false)
  const [sourceType, setSourceType] = useState<'GATEWAY' | 'NACOS'>('GATEWAY')
  const [parsedTools, setParsedTools] = useState<Array<{
    name: string;
    description: string;
    args?: Array<{
      name: string;
      description: string;
      type: string;
      required: boolean;
      position: string;
      default?: string;
      enum?: string[];
    }>;
  }>>([])
  const [httpJson, setHttpJson] = useState('')
  const [sseJson, setSseJson] = useState('')
  const [localJson, setLocalJson] = useState('')
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0)
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState<number>(0)
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState<number>(0)

  useEffect(() => {
    fetchGateways()
    fetchNacosInstances()
  }, [])

  // 解析MCP tools配置
  useEffect(() => {
    if (apiProduct.type === 'MCP_SERVER' && apiProduct.mcpConfig?.tools) {
      const parsedConfig = parseYamlConfig(apiProduct.mcpConfig.tools)
      if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
        setParsedTools(parsedConfig.tools)
      } else {
        // 如果tools字段存在但是空数组，也设置为空数组
        setParsedTools([])
      }
    } else {
      setParsedTools([])
    }
  }, [apiProduct])

  // 生成连接配置
  // 当产品切换时重置域名选择索引
  useEffect(() => {
    setSelectedDomainIndex(0);
    setSelectedAgentDomainIndex(0);
    setSelectedModelDomainIndex(0);
  }, [apiProduct.productId]);

  useEffect(() => {
    if (apiProduct.type === 'MCP_SERVER' && apiProduct.mcpConfig) {
      // 获取关联的MCP Server名称
      let mcpServerName = apiProduct.name // 默认使用产品名称

      if (linkedService) {
        // 从linkedService中获取真实的MCP Server名称
        if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'mcpServerName' in linkedService.apigRefConfig) {
          mcpServerName = linkedService.apigRefConfig.mcpServerName || apiProduct.name
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
          mcpServerName = linkedService.higressRefConfig.mcpServerName || apiProduct.name
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
          // 检查是否是 AdpAIGatewayModelItem 类型（有 modelApiName 属性）
          if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
            mcpServerName = linkedService.adpAIGatewayRefConfig.modelApiName || apiProduct.name
          } else {
            // APIGAIMCPItem 类型
            mcpServerName = linkedService.adpAIGatewayRefConfig.mcpServerName || apiProduct.name
          }
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
          if ('modelApiName' in linkedService.apsaraGatewayRefConfig) {
            mcpServerName = linkedService.apsaraGatewayRefConfig.modelApiName || apiProduct.name
          } else {
            mcpServerName = linkedService.apsaraGatewayRefConfig.mcpServerName || apiProduct.name
          }
        } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
          mcpServerName = linkedService.nacosRefConfig.mcpServerName || apiProduct.name
        }
      }

      generateConnectionConfig(
        apiProduct.mcpConfig.mcpServerConfig.domains,
        apiProduct.mcpConfig.mcpServerConfig.path,
        mcpServerName,
        apiProduct.mcpConfig.mcpServerConfig.rawConfig,
        apiProduct.mcpConfig.meta?.protocol,
        selectedDomainIndex
      )
    }
  }, [apiProduct, linkedService, selectedDomainIndex])

  // 生成域名选项的函数
  const getDomainOptions = (domains: Array<{ domain: string; port?: number; protocol: string; networkType?: string }>) => {
    return domains.map((domain, index) => {
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      return {
        value: index,
        label: `${domain.protocol}://${formattedDomain}`,
        domain: domain
      }
    })
  }

  // 解析YAML配置的函数
  const parseYamlConfig = (yamlString: string): {
    tools?: Array<{
      name: string;
      description: string;
      args?: Array<{
        name: string;
        description: string;
        type: string;
        required: boolean;
        position: string;
        default?: string;
        enum?: string[];
      }>;
    }>;
  } | null => {
    try {
      const parsed = yaml.load(yamlString) as {
        tools?: Array<{
          name: string;
          description: string;
          args?: Array<{
            name: string;
            description: string;
            type: string;
            required: boolean;
            position: string;
            default?: string;
            enum?: string[];
          }>;
        }>;
      };
      return parsed;
    } catch (error) {
      console.error('YAML解析失败:', error)
      return null
    }
  }

  // 生成连接配置
  const generateConnectionConfig = (
    domains: Array<{ domain: string; port?: number; protocol: string }> | null | undefined,
    path: string | null | undefined,
    serverName: string,
    localConfig?: unknown,
    protocolType?: string,
    domainIndex: number = 0
  ) => {
    // 互斥：优先判断本地模式
    if (localConfig) {
      const localConfigJson = JSON.stringify(localConfig, null, 2);
      setLocalJson(localConfigJson);
      setHttpJson("");
      setSseJson("");
      return;
    }

    // HTTP/SSE 模式
    if (domains && domains.length > 0 && path && domainIndex < domains.length) {
      const domain = domains[domainIndex]
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      const baseUrl = `${domain.protocol}://${formattedDomain}`;
      let fullUrl = `${baseUrl}${path || '/'}`;

      if (protocolType === 'SSE') {
        // 仅生成SSE配置，不追加/sse
        const sseConfig = {
          mcpServers: {
            [serverName]: {
              type: "sse",
              url: fullUrl
            }
          }
        }
        setSseJson(JSON.stringify(sseConfig, null, 2))
        setHttpJson("")
        setLocalJson("")
        return;
      } else if (protocolType === 'StreamableHTTP') {
        // 仅生成HTTP配置
        const httpConfig = {
          mcpServers: {
            [serverName]: {
              url: fullUrl
            }
          }
        }
        setHttpJson(JSON.stringify(httpConfig, null, 2))
        setSseJson("")
        setLocalJson("")
        return;
      } else {
        // protocol为null或其他值：生成两种配置
        const sseConfig = {
          mcpServers: {
            [serverName]: {
              type: "sse",
              url: `${fullUrl}/sse`
            }
          }
        }

        const httpConfig = {
          mcpServers: {
            [serverName]: {
              url: fullUrl
            }
          }
        }

        setSseJson(JSON.stringify(sseConfig, null, 2))
        setHttpJson(JSON.stringify(httpConfig, null, 2))
        setLocalJson("")
        return;
      }
    }

    // 无有效配置
    setHttpJson("");
    setSseJson("");
    setLocalJson("");
  }

  const handleCopy = async (text: string) => {
    try {
      await copyToClipboard(text);
      message.success("已复制到剪贴板");
    } catch {
      message.error("复制失败，请手动复制");
    }
  }

  const fetchGateways = async () => {
    setGatewayLoading(true)
    try {
      const res = await gatewayApi.getGateways({
        page: 1,
        size: 1000,
      })
      let result;
      if (apiProduct.type === 'REST_API') {
        // REST API 只支持 APIG_API 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_API');
      } else if (apiProduct.type === 'AGENT_API') {
        // Agent API 只支持 APIG_AI 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_AI');
      } else if (apiProduct.type === 'MODEL_API') {
        // Model API 支持 APIG_AI 网关、HIGRESS 网关、ADP AI 网关、APSARA 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_AI' || item.gatewayType === 'HIGRESS' || item.gatewayType === 'ADP_AI_GATEWAY' || item.gatewayType === 'APSARA_GATEWAY');
      } else {
        // MCP Server 支持 HIGRESS、APIG_AI、ADP AI 网关、APSARA 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'HIGRESS' || item.gatewayType === 'APIG_AI' || item.gatewayType === 'ADP_AI_GATEWAY' || item.gatewayType === 'APSARA_GATEWAY');
      }
      setGateways(result || [])
    } catch (error) {
      console.error('获取网关列表失败:', error)
    } finally {
      setGatewayLoading(false)
    }
  }

  const fetchNacosInstances = async () => {
    setNacosLoading(true)
    try {
      const res = await nacosApi.getNacos({
        page: 1,
        size: 1000 // 获取所有 Nacos 实例
      })
      setNacosInstances(res.data.content || [])
    } catch (error) {
      console.error('获取Nacos实例列表失败:', error)
    } finally {
      setNacosLoading(false)
    }
  }

  const handleSourceTypeChange = (value: 'GATEWAY' | 'NACOS') => {
    setSourceType(value)
    setSelectedGateway(null)
    setSelectedNacos(null)
    setSelectedNamespace(null)
    setNacosNamespaces([])
    setApiList([])
    form.setFieldsValue({
      gatewayId: undefined,
      nacosId: undefined,
      apiId: undefined
    })
  }

  const handleGatewayChange = async (gatewayId: string) => {
    const gateway = gateways.find(g => g.gatewayId === gatewayId)
    setSelectedGateway(gateway || null)

    if (!gateway) return

    setApiLoading(true)
    try {
      if (gateway.gatewayType === 'APIG_API') {
        // APIG_API类型：获取REST API列表
        const restRes = await gatewayApi.getGatewayRestApis(gatewayId, {})
        const restApis = (restRes.data?.content || []).map((api: any) => ({
          apiId: api.apiId,
          apiName: api.apiName,
          type: 'REST API'
        }))
        setApiList(restApis)
      } else if (gateway.gatewayType === 'HIGRESS') {
        // HIGRESS类型：对于Model API产品，获取Model API列表；其他情况获取MCP Server列表
        if (apiProduct.type === 'MODEL_API') {
          // HIGRESS类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 1000 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelRouteName: api.modelRouteName,
            fromGatewayType: 'HIGRESS' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
          // HIGRESS类型：获取MCP Server列表
          const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
            page: 1,
            size: 1000 // 获取所有MCP Server
          })
          const mcpServers = (res.data?.content || []).map((api: any) => ({
            mcpServerName: api.mcpServerName,
            fromGatewayType: 'HIGRESS' as const,
            type: 'MCP Server'
          }))
          setApiList(mcpServers)
        }
      } else if (gateway.gatewayType === 'APIG_AI') {
        if (apiProduct.type === 'AGENT_API') {
          // APIG_AI类型 + Agent API产品：获取Agent API列表
          const res = await gatewayApi.getGatewayAgentApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Agent API
          })
          const agentApis = (res.data?.content || []).map((api: any) => ({
            agentApiId: api.agentApiId,
            agentApiName: api.agentApiName,
            fromGatewayType: 'APIG_AI' as const,
            type: 'Agent API'
          }))
          setApiList(agentApis)
        } else if (apiProduct.type === 'MODEL_API') {
          // APIG_AI类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'APIG_AI' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
          // APIG_AI类型 + MCP Server产品：获取MCP Server列表
          const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
            page: 1,
            size: 500 // 获取所有MCP Server
          })
          const mcpServers = (res.data?.content || []).map((api: any) => ({
            mcpServerName: api.mcpServerName,
            fromGatewayType: 'APIG_AI' as const,
            mcpRouteId: api.mcpRouteId,
            apiId: api.apiId,
            mcpServerId: api.mcpServerId,
            type: 'MCP Server'
          }))
          setApiList(mcpServers)
        }
      } else if (gateway.gatewayType === 'ADP_AI_GATEWAY') {
        if (apiProduct.type === 'MODEL_API') {
          // ADP_AI_GATEWAY类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'ADP_AI_GATEWAY' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
          // ADP_AI_GATEWAY类型：获取MCP Server列表
          const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
            page: 1,
            size: 500 // 获取所有MCP Server
          })
          const mcpServers = (res.data?.content || []).map((api: any) => ({
            mcpServerName: api.mcpServerName || api.name,
            fromGatewayType: 'ADP_AI_GATEWAY' as const,
            mcpRouteId: api.mcpRouteId,
            mcpServerId: api.mcpServerId,
            type: 'MCP Server'
          }))
          setApiList(mcpServers)
        }
      } else if (gateway.gatewayType === 'APSARA_GATEWAY') {
        if (apiProduct.type === 'AGENT_API') {
          // APSARA_GATEWAY类型 + Agent API产品：获取Agent API列表
          const res = await gatewayApi.getGatewayAgentApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Agent API
          })
          const agentApis = (res.data?.content || []).map((api: any) => ({
            agentApiId: api.agentApiId,
            agentApiName: api.agentApiName,
            fromGatewayType: 'APSARA_GATEWAY' as const,
            type: 'Agent API'
          }))
          setApiList(agentApis)
        } else if (apiProduct.type === 'MODEL_API') {
          // APSARA_GATEWAY类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'APSARA_GATEWAY' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
          // APSARA_GATEWAY类型：获取MCP Server列表
          const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
            page: 1,
            size: 500 // 获取所有MCP Server
          })
          const mcpServers = (res.data?.content || []).map((api: any) => ({
            mcpServerName: api.mcpServerName || api.name,
            fromGatewayType: 'APSARA_GATEWAY' as const,
            mcpRouteId: api.mcpRouteId,
            mcpServerId: api.mcpServerId,
            type: 'MCP Server'
          }))
          setApiList(mcpServers)
        }
      }
    } catch (error) {
    } finally {
      setApiLoading(false)
    }
  }

  const handleNacosChange = async (nacosId: string) => {
    const nacos = nacosInstances.find(n => n.nacosId === nacosId)
    setSelectedNacos(nacos || null)
    setSelectedNamespace(null)
    setApiList([])
    setNacosNamespaces([])
    if (!nacos) return

    // 获取命名空间列表
    try {
      const nsRes = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 })
      const namespaces = (nsRes.data?.content || []).map((ns: any) => ({
        namespaceId: ns.namespaceId,
        namespaceName: ns.namespaceName || ns.namespaceId,
        namespaceDesc: ns.namespaceDesc
      }))
      setNacosNamespaces(namespaces)
    } catch (e) {
      console.error('获取命名空间失败', e)
    }
  }

  const handleNamespaceChange = async (namespaceId: string) => {
    setSelectedNamespace(namespaceId)
    setApiLoading(true)
    try {
      if (!selectedNacos) return

      // 根据产品类型获取不同的列表
      if (apiProduct.type === 'AGENT_API') {
        // 获取 Agent 列表
        const res = await nacosApi.getNacosAgents(selectedNacos.nacosId, {
          page: 1,
          size: 1000,
          namespaceId
        })
        const agents = (res.data?.content || []).map((api: any) => ({
          agentName: api.agentName,
          description: api.description,
          fromGatewayType: 'NACOS' as const,
          type: `Agent API (${namespaceId})`
        }))
        setApiList(agents)
      } else if (apiProduct.type === 'MCP_SERVER') {
        // 获取 MCP Server 列表（现有逻辑）
        const res = await nacosApi.getNacosMcpServers(selectedNacos.nacosId, {
          page: 1,
          size: 1000,
          namespaceId
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName,
          fromGatewayType: 'NACOS' as const,
          type: `MCP Server (${namespaceId})`
        }))
        setApiList(mcpServers)
      }
    } catch (e) {
      console.error('获取 Nacos 资源列表失败:', e)
    } finally {
      setApiLoading(false)
    }
  }


  // TODO
  const handleModalOk = () => {
    form.validateFields().then((values) => {
      const { sourceType, gatewayId, nacosId, apiId } = values
      const selectedApi = apiList.find((item: any) => {
        if ('apiId' in item) {
          // REST API或MCP server 会返回apiId和mcpRouteId，此时mcpRouteId为唯一值，apiId不是
          if ('mcpRouteId' in item) {
            return item.mcpRouteId === apiId
          } else {
            return item.apiId === apiId
          }
        } else if ('mcpServerName' in item) {
          return item.mcpServerName === apiId
        } else if ('agentApiId' in item || 'agentApiName' in item) {
          // Agent API: 匹配agentApiId或agentApiName
          return item.agentApiId === apiId || item.agentApiName === apiId
        } else if ('modelApiId' in item || 'modelApiName' in item) {
          // Model API (AI Gateway): 匹配modelApiId或modelApiName
          return item.modelApiId === apiId || item.modelApiName === apiId
        } else if ('modelRouteName' in item && item.fromGatewayType === 'HIGRESS') {
          // Model API (Higress): 匹配modelRouteName字段
          return item.modelRouteName === apiId
        } else if ('agentName' in item) {
          // Nacos Agent: 匹配agentName
          return item.agentName === apiId
        }
        return false
      })
      const newService: LinkedService = {
        gatewayId: sourceType === 'GATEWAY' ? gatewayId : undefined, // 对于 Nacos，使用 nacosId 作为 gatewayId
        nacosId: sourceType === 'NACOS' ? nacosId : undefined,
        sourceType,
        productId: apiProduct.productId,
        apigRefConfig: selectedApi && ('apiId' in selectedApi || 'agentApiId' in selectedApi || 'agentApiName' in selectedApi || 'modelApiId' in selectedApi || 'modelApiName' in selectedApi) && (!('fromGatewayType' in selectedApi) || (selectedApi.fromGatewayType !== 'HIGRESS' && selectedApi.fromGatewayType !== 'ADP_AI_GATEWAY' && selectedApi.fromGatewayType !== 'APSARA_GATEWAY')) ? selectedApi as RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem : undefined,
        higressRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'HIGRESS' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelRouteName: (selectedApi as any).modelRouteName, fromGatewayType: 'HIGRESS' as const }
            : { mcpServerName: (selectedApi as any).mcpServerName, fromGatewayType: 'HIGRESS' as const }
        ) : undefined,
        nacosRefConfig: sourceType === 'NACOS' && selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'NACOS' ? {
          ...selectedApi,
          namespaceId: selectedNamespace || 'public'
        } : undefined,
        adpAIGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'ADP_AI_GATEWAY' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelApiId: (selectedApi as any).modelApiId, modelApiName: (selectedApi as any).modelApiName, fromGatewayType: 'ADP_AI_GATEWAY' as const } as AdpAIGatewayModelItem
            : selectedApi as APIGAIMCPItem
        ) : undefined,
        apsaraGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'APSARA_GATEWAY' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelApiId: (selectedApi as any).modelApiId, modelApiName: (selectedApi as any).modelApiName, fromGatewayType: 'APSARA_GATEWAY' as const } as ApsaraGatewayModelItem
            : selectedApi as APIGAIMCPItem
        ) : undefined,
      }
      apiProductApi.createApiProductRef(apiProduct.productId, newService).then(async () => {
        message.success('关联成功')
        setIsModalVisible(false)

        // 重新获取关联信息并更新
        try {
          const res = await apiProductApi.getApiProductRef(apiProduct.productId)
          onLinkedServiceUpdate(res.data || null)
        } catch (error) {
          console.error('获取关联API失败:', error)
          onLinkedServiceUpdate(null)
        }

        // 重新获取产品详情（特别重要，因为关联API后apiProduct.apiConfig可能会更新）
        handleRefresh()

        form.resetFields()
        setSelectedGateway(null)
        setSelectedNacos(null)
        setApiList([])
        setSourceType('GATEWAY')
      }).catch(() => {
        message.error('关联失败')
      })
    })
  }

  const handleModalCancel = () => {
    setIsModalVisible(false)
    form.resetFields()
    setSelectedGateway(null)
    setSelectedNacos(null)
    setApiList([])
    setSourceType('GATEWAY')
  }


  const handleDelete = () => {
    if (!linkedService) return

    Modal.confirm({
      title: '确认解除关联',
      content: '确定要解除与当前API的关联吗？',
      icon: <ExclamationCircleOutlined />,
      onOk() {
        return apiProductApi.deleteApiProductRef(apiProduct.productId).then(() => {
          message.success('解除关联成功')
          onLinkedServiceUpdate(null)
          // 重新获取产品详情（解除关联后apiProduct.apiConfig可能会更新）
          handleRefresh()
        }).catch(() => {
          message.error('解除关联失败')
        })
      }
    })
  }

  const getServiceInfo = () => {
    if (!linkedService) return null

    let apiName = ''
    let apiType = ''
    let sourceInfo = ''
    let gatewayInfo = ''

    // 首先根据 Product 的 type 确定基本类型
    if (apiProduct.type === 'REST_API') {
      // REST API 类型产品 - 只能关联 API 网关上的 REST API
      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'apiName' in linkedService.apigRefConfig) {
        apiName = linkedService.apigRefConfig.apiName || '未命名'
        apiType = 'REST API'
        sourceInfo = 'API网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      }
    } else if (apiProduct.type === 'MCP_SERVER') {
      // MCP Server 类型产品 - 可以关联多种平台上的 MCP Server
      apiType = 'MCP Server'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'mcpServerName' in linkedService.apigRefConfig) {
        // AI网关上的MCP Server
        apiName = linkedService.apigRefConfig.mcpServerName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
        // Higress网关上的MCP Server
        apiName = linkedService.higressRefConfig.mcpServerName || '未命名'
        sourceInfo = 'Higress网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
        // 检查是否是 AdpAIGatewayModelItem 类型（有 modelApiName 属性）
        if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
          // 专有云AI网关上的Model API
          apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名'
          sourceInfo = '专有云AI网关'
          gatewayInfo = linkedService.gatewayId || '未知'
        } else {
          // 专有云AI网关上的MCP Server
          apiName = linkedService.adpAIGatewayRefConfig.mcpServerName || '未命名'
          sourceInfo = '专有云AI网关'
          gatewayInfo = linkedService.gatewayId || '未知'
        }
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        // 飞天企业版AI网关上的MCP Server
        if ('mcpServerName' in linkedService.apsaraGatewayRefConfig) {
          apiName = linkedService.apsaraGatewayRefConfig.mcpServerName || '未命名'
        } else {
          apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名'
        }
        sourceInfo = '飞天企业版AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
        // Nacos上的MCP Server
        apiName = linkedService.nacosRefConfig.mcpServerName || '未命名'
        sourceInfo = 'Nacos服务发现'
        gatewayInfo = linkedService.nacosId || '未知'
      }
    } else if (apiProduct.type === 'AGENT_API') {
      // Agent API 类型产品 - 可以关联 AI 网关或 Nacos 上的 Agent API
      apiType = 'Agent API'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'agentApiName' in linkedService.apigRefConfig) {
        // AI网关上的Agent API
        apiName = linkedService.apigRefConfig.agentApiName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'agentName' in linkedService.nacosRefConfig) {
        // Nacos 上的 Agent API
        apiName = linkedService.nacosRefConfig.agentName || '未命名'
        sourceInfo = 'Nacos Agent Registry'
        gatewayInfo = linkedService.nacosId || '未知'
      }
      // 注意：Agent API 不支持专有云AI网关（ADP_AI_GATEWAY）
    } else if (apiProduct.type === 'MODEL_API') {
      // Model API 类型产品 - 可以关联 AI 网关或 Higress 网关上的 Model API
      apiType = 'Model API'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'modelApiName' in linkedService.apigRefConfig) {
        // AI网关上的Model API
        apiName = linkedService.apigRefConfig.modelApiName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig && 'modelRouteName' in linkedService.higressRefConfig) {
        // Higress网关上的Model API（AI路由）
        apiName = linkedService.higressRefConfig.modelRouteName || '未命名'
        sourceInfo = 'Higress网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig && 'modelApiName' in linkedService.adpAIGatewayRefConfig) {
        // 专有云AI网关上的Model API
        apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名'
        sourceInfo = '专有云AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig && 'modelApiName' in linkedService.apsaraGatewayRefConfig) {
        // 飞天企业版AI网关上的Model API
        apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名'
        sourceInfo = '飞天企业版AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      }
    }

    return {
      apiName,
      apiType,
      sourceInfo,
      gatewayInfo
    }
  }

  const renderLinkInfo = () => {
    const serviceInfo = getServiceInfo()

    // 没有关联任何API
    if (!linkedService || !serviceInfo) {
      return (
        <Card className="mb-6">
          <div className="text-center py-8">
            <div className="text-gray-500 mb-4">暂未关联任何API</div>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalVisible(true)}>
              关联API
            </Button>
          </div>
        </Card>
      )
    }

    return (
      <Card
        className="mb-6"
        title="关联详情"
        extra={
          <Button type="primary" danger icon={<DeleteOutlined />} onClick={handleDelete}>
            解除关联
          </Button>
        }
      >
        <div>
          {/* 第一行：名称 + 类型 */}
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">名称:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiName || '未命名'}</span>
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiType}</span>
          </div>

          {/* 第二行：来源 + ID */}
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">来源:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.sourceInfo}</span>
            <span className="text-xs text-gray-600">
              {linkedService?.sourceType === 'NACOS' ? 'Nacos ID:' : '网关ID:'}
            </span>
            <span className="col-span-2 text-xs text-gray-700">{serviceInfo.gatewayInfo}</span>
          </div>
        </div>
      </Card>
    )
  }

  const renderApiConfig = () => {
    const isMcp = apiProduct.type === 'MCP_SERVER'
    const isOpenApi = apiProduct.type === 'REST_API'
    const isAgent = apiProduct.type === 'AGENT_API'
    const isModel = apiProduct.type === 'MODEL_API'

    // MCP Server类型：无论是否有linkedService都显示tools和连接点配置  
    if (isMcp && apiProduct.mcpConfig) {
      return (
        <ApiProductMcpConfigCard
          apiProduct={apiProduct}
          domainOptions={getDomainOptions(
            apiProduct.mcpConfig.mcpServerConfig.domains || []
          )}
          httpJson={httpJson}
          localJson={localJson}
          onCopy={handleCopy}
          onDomainChange={setSelectedDomainIndex}
          parsedTools={parsedTools}
          selectedDomainIndex={selectedDomainIndex}
          sseJson={sseJson}
        />
      )
    }

    // Agent API类型：显示协议支持和路由配置或 AgentCard
    if (isAgent && apiProduct.agentConfig?.agentAPIConfig) {
      return (
        <ApiProductAgentConfigCard
          agentAPIConfig={apiProduct.agentConfig.agentAPIConfig}
          onCopy={handleCopy}
          selectedDomainIndex={selectedAgentDomainIndex}
          onDomainChange={setSelectedAgentDomainIndex}
        />
      )
    }

    // Model API类型：显示协议支持和路由配置
    if (isModel && apiProduct.modelConfig?.modelAPIConfig) {
      return (
        <ApiProductModelConfigCard
          modelAPIConfig={apiProduct.modelConfig.modelAPIConfig}
          onCopy={handleCopy}
          selectedDomainIndex={selectedModelDomainIndex}
          onDomainChange={setSelectedModelDomainIndex}
        />
      )
    }

    // REST API类型：需要linkedService才显示
    if (!linkedService) {
      return null
    }

    return (
      <Card title="配置详情">

        {isOpenApi && apiProduct.apiConfig && apiProduct.apiConfig.spec && (
          <div>
            <h4 className="text-base font-medium mb-4">REST API接口文档</h4>
            <SwaggerUIWrapper apiSpec={apiProduct.apiConfig.spec} />
          </div>
        )}
      </Card>
    )
  }

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">API关联</h1>
        <p className="text-gray-600">管理Product关联的API</p>
      </div>

      {renderLinkInfo()}
      {renderApiConfig()}

      <ApiProductLinkApiModal
        apiList={apiList}
        apiLoading={apiLoading}
        apiProduct={apiProduct}
        form={form}
        gatewayLoading={gatewayLoading}
        gateways={gateways}
        isModalVisible={isModalVisible}
        isRelink={Boolean(linkedService)}
        nacosInstances={nacosInstances}
        nacosLoading={nacosLoading}
        nacosNamespaces={nacosNamespaces}
        onGatewayChange={handleGatewayChange}
        onModalCancel={handleModalCancel}
        onModalOk={handleModalOk}
        onNacosChange={handleNacosChange}
        onNamespaceChange={handleNamespaceChange}
        selectedGateway={selectedGateway}
        selectedNamespace={selectedNamespace}
        selectedNacos={selectedNacos}
        sourceType={sourceType}
        onSourceTypeChange={handleSourceTypeChange}
      />
    </div>
  )
}
