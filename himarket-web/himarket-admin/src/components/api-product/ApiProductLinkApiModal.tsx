import { Form, Modal, Select } from 'antd'
import type { FormInstance } from 'antd'
import type {
  ApiItem,
  ApiProduct,
  NacosMCPItem,
} from '@/types/api-product'
import type { Gateway, NacosInstance } from '@/types/gateway'
import { getGatewayTypeLabel } from '@/lib/constant'
import {
  getApiLabel,
  getApiOptionValue,
  getSupportedGateways,
} from './apiProductLinkApiModalUtils'

interface ApiProductLinkApiModalProps {
  apiList: ApiItem[] | NacosMCPItem[]
  apiLoading: boolean
  apiProduct: ApiProduct
  form: FormInstance
  gatewayLoading: boolean
  gateways: Gateway[]
  isModalVisible: boolean
  isRelink: boolean
  nacosInstances: NacosInstance[]
  nacosLoading: boolean
  nacosNamespaces: Array<{
    namespaceId: string
    namespaceName: string
  }>
  onGatewayChange: (gatewayId: string) => void
  onModalCancel: () => void
  onModalOk: () => void
  onNacosChange: (nacosId: string) => void
  onNamespaceChange: (namespaceId: string) => void
  selectedGateway: Gateway | null
  selectedNamespace: string | null
  selectedNacos: NacosInstance | null
  sourceType: 'GATEWAY' | 'NACOS'
  onSourceTypeChange: (value: 'GATEWAY' | 'NACOS') => void
}

export function ApiProductLinkApiModal({
  apiList,
  apiLoading,
  apiProduct,
  form,
  gatewayLoading,
  gateways,
  isModalVisible,
  isRelink,
  nacosInstances,
  nacosLoading,
  nacosNamespaces,
  onGatewayChange,
  onModalCancel,
  onModalOk,
  onNacosChange,
  onNamespaceChange,
  selectedGateway,
  selectedNamespace,
  selectedNacos,
  sourceType,
  onSourceTypeChange,
}: ApiProductLinkApiModalProps) {
  const apiLabel = getApiLabel(apiProduct)

  return (
    <Modal
      title={isRelink ? '重新关联API' : '关联新API'}
      open={isModalVisible}
      onOk={onModalOk}
      onCancel={onModalCancel}
      okText="关联"
      cancelText="取消"
      width={600}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="sourceType"
          label="来源类型"
          initialValue="GATEWAY"
          rules={[{ required: true, message: '请选择来源类型' }]}
        >
          <Select placeholder="请选择来源类型" onChange={onSourceTypeChange}>
            <Select.Option value="GATEWAY">网关</Select.Option>
            <Select.Option
              value="NACOS"
              disabled={
                apiProduct.type === 'REST_API' || apiProduct.type === 'MODEL_API'
              }
            >
              Nacos
            </Select.Option>
          </Select>
        </Form.Item>

        {sourceType === 'GATEWAY' && (
          <Form.Item
            name="gatewayId"
            label="网关实例"
            rules={[{ required: true, message: '请选择网关' }]}
          >
            <Select
              placeholder="请选择网关实例"
              loading={gatewayLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.value as unknown as string)
                  ?.toLowerCase()
                  .includes(input.toLowerCase())
              }
              onChange={onGatewayChange}
              optionLabelProp="label"
            >
              {getSupportedGateways(apiProduct, gateways).map(gateway => (
                  <Select.Option
                    key={gateway.gatewayId}
                    value={gateway.gatewayId}
                    label={gateway.gatewayName}
                  >
                    <div>
                      <div className="font-medium">{gateway.gatewayName}</div>
                      <div className="text-sm text-gray-500">
                        {gateway.gatewayId} -{' '}
                        {getGatewayTypeLabel(gateway.gatewayType as any)}
                      </div>
                    </div>
                  </Select.Option>
                ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && (
          <Form.Item
            name="nacosId"
            label="Nacos实例"
            rules={[{ required: true, message: '请选择Nacos实例' }]}
          >
            <Select
              placeholder="请选择Nacos实例"
              loading={nacosLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.value as unknown as string)
                  ?.toLowerCase()
                  .includes(input.toLowerCase())
              }
              onChange={onNacosChange}
              optionLabelProp="label"
            >
              {nacosInstances.map(nacos => (
                <Select.Option
                  key={nacos.nacosId}
                  value={nacos.nacosId}
                  label={nacos.nacosName}
                >
                  <div>
                    <div className="font-medium">{nacos.nacosName}</div>
                    <div className="text-sm text-gray-500">
                      {nacos.serverUrl}
                    </div>
                  </div>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}

        {sourceType === 'NACOS' && selectedNacos && (
          <Form.Item
            name="namespaceId"
            label="命名空间"
            rules={[{ required: true, message: '请选择命名空间' }]}
          >
            <Select
              placeholder="请选择命名空间"
              loading={apiLoading && nacosNamespaces.length === 0}
              onChange={onNamespaceChange}
              showSearch
              filterOption={(input, option) =>
                (option?.children as unknown as string)
                  ?.toLowerCase()
                  .includes(input.toLowerCase())
              }
              optionLabelProp="label"
            >
              {nacosNamespaces.map(ns => (
                <Select.Option
                  key={ns.namespaceId}
                  value={ns.namespaceId}
                  label={ns.namespaceName}
                >
                  <div>
                    <div className="font-medium">{ns.namespaceName}</div>
                    <div className="text-sm text-gray-500">{ns.namespaceId}</div>
                  </div>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )}

        {(selectedGateway || (selectedNacos && selectedNamespace)) && (
          <Form.Item
            name="apiId"
            label={`选择${apiLabel}`}
            rules={[{ required: true, message: `请选择${apiLabel}` }]}
          >
            <Select
              placeholder={`请选择${apiLabel}`}
              loading={apiLoading}
              showSearch
              filterOption={(input, option) =>
                (option?.value as unknown as string)
                  ?.toLowerCase()
                  .includes(input.toLowerCase())
              }
              optionLabelProp="label"
            >
              {apiList.map((api: any) => {
                const option = getApiOptionValue(apiProduct, api)
                return (
                  <Select.Option
                    key={option.key}
                    value={option.value}
                    label={option.displayName}
                  >
                    <div>
                      <div className="font-medium">{option.displayName}</div>
                      <div className="text-sm text-gray-500">
                        {api.type} - {api.description || option.key}
                      </div>
                    </div>
                  </Select.Option>
                )
              })}
            </Select>
          </Form.Item>
        )}
      </Form>
    </Modal>
  )
}
