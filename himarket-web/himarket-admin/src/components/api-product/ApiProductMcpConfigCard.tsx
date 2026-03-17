import { Button, Card, Collapse, Row, Col, Select, Tabs } from 'antd'
import { CopyOutlined } from '@ant-design/icons'
import type { ApiProduct } from '@/types/api-product'

interface DomainOption {
  value: number
  label: string
}

interface ToolArgument {
  name: string
  description: string
  type: string
  required: boolean
  position: string
  default?: string
  enum?: string[]
}

interface ParsedTool {
  name: string
  description: string
  args?: ToolArgument[]
}

interface ApiProductMcpConfigCardProps {
  apiProduct: ApiProduct
  domainOptions: DomainOption[]
  httpJson: string
  localJson: string
  onCopy: (text: string) => void
  onDomainChange: (value: number) => void
  parsedTools: ParsedTool[]
  selectedDomainIndex: number
  sseJson: string
}

function renderConfigPanel(text: string, onCopy: (text: string) => void) {
  return (
    <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
      <Button
        size="small"
        icon={<CopyOutlined />}
        className="absolute top-2 right-2 z-10"
        onClick={() => onCopy(text)}
      />
      <div className="text-gray-800 font-mono text-xs overflow-x-auto">
        <pre className="whitespace-pre">{text}</pre>
      </div>
    </div>
  )
}

function buildConnectionTabs(
  localJson: string,
  sseJson: string,
  httpJson: string,
  onCopy: (text: string) => void
) {
  const tabs = []

  if (localJson) {
    tabs.push({
      key: 'local',
      label: 'Stdio',
      children: renderConfigPanel(localJson, onCopy),
    })
    return tabs
  }

  if (sseJson) {
    tabs.push({
      key: 'sse',
      label: 'SSE',
      children: renderConfigPanel(sseJson, onCopy),
    })
  }

  if (httpJson) {
    tabs.push({
      key: 'http',
      label: 'Streamable HTTP',
      children: renderConfigPanel(httpJson, onCopy),
    })
  }

  return tabs
}

export function ApiProductMcpConfigCard({
  apiProduct,
  domainOptions,
  httpJson,
  localJson,
  onCopy,
  onDomainChange,
  parsedTools,
  selectedDomainIndex,
  sseJson,
}: ApiProductMcpConfigCardProps) {
  return (
    <Card title="配置详情">
      <Row gutter={24}>
        <Col span={15}>
          <Card>
            <Tabs
              defaultActiveKey="tools"
              items={[
                {
                  key: 'tools',
                  label: `Tools (${parsedTools.length})`,
                  children:
                    parsedTools.length > 0 ? (
                      <div className="border border-gray-200 rounded-lg bg-gray-50">
                        {parsedTools.map((tool, idx) => (
                          <div
                            key={idx}
                            className={
                              idx < parsedTools.length - 1
                                ? 'border-b border-gray-200'
                                : ''
                            }
                          >
                            <Collapse
                              ghost
                              expandIconPosition="end"
                              items={[
                                {
                                  key: idx.toString(),
                                  label: tool.name,
                                  children: (
                                    <div className="px-4 pb-2">
                                      <div className="text-gray-600 mb-4">
                                        {tool.description}
                                      </div>
                                      {tool.args && tool.args.length > 0 ? (
                                        <div>
                                          <p className="font-medium text-gray-700 mb-3">
                                            输入参数:
                                          </p>
                                          {tool.args.map((arg, argIdx) => (
                                            <div key={argIdx} className="mb-3">
                                              <div className="flex items-center mb-2">
                                                <span className="font-medium text-gray-800 mr-2">
                                                  {arg.name}
                                                </span>
                                                <span className="text-xs bg-gray-200 text-gray-600 px-2 py-1 rounded mr-2">
                                                  {arg.type}
                                                </span>
                                                {arg.required ? (
                                                  <span className="text-red-500 text-xs mr-2">
                                                    *
                                                  </span>
                                                ) : null}
                                                {arg.description ? (
                                                  <span className="text-xs text-gray-500">
                                                    {arg.description}
                                                  </span>
                                                ) : null}
                                              </div>
                                              <input
                                                type="text"
                                                placeholder={
                                                  arg.description ||
                                                  `请输入${arg.name}`
                                                }
                                                className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent mb-2"
                                                defaultValue={
                                                  arg.default !== undefined
                                                    ? JSON.stringify(arg.default)
                                                    : ''
                                                }
                                              />
                                              {arg.enum ? (
                                                <div className="text-xs text-gray-500">
                                                  可选值:{' '}
                                                  {arg.enum.map(value => (
                                                    <code key={value} className="mr-1">
                                                      {value}
                                                    </code>
                                                  ))}
                                                </div>
                                              ) : null}
                                            </div>
                                          ))}
                                        </div>
                                      ) : null}
                                    </div>
                                  ),
                                },
                              ]}
                            />
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-gray-500 text-center py-8">
                        No tools available
                      </div>
                    ),
                },
              ]}
            />
          </Card>
        </Col>

        <Col span={9}>
          <Card>
            <div className="mb-4">
              <h3 className="text-sm font-semibold mb-3">连接点配置</h3>

              {apiProduct.mcpConfig?.mcpServerConfig?.domains &&
              apiProduct.mcpConfig.mcpServerConfig.domains.length > 0 ? (
                <div className="mb-2">
                  <div className="flex border border-gray-200 rounded-md overflow-hidden">
                    <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                      域名
                    </div>
                    <div className="flex-1 min-w-0">
                      <Select
                        value={selectedDomainIndex}
                        onChange={onDomainChange}
                        className="w-full"
                        placeholder="选择域名"
                        size="middle"
                        variant="borderless"
                        style={{
                          fontSize: '12px',
                          height: '100%',
                        }}
                      >
                        {domainOptions.map(option => (
                          <Select.Option key={option.value} value={option.value}>
                            <span
                              title={option.label}
                              className="text-xs text-gray-900 font-mono"
                            >
                              {option.label}
                            </span>
                          </Select.Option>
                        ))}
                      </Select>
                    </div>
                  </div>
                </div>
              ) : null}

              <Tabs
                size="small"
                defaultActiveKey={localJson ? 'local' : sseJson ? 'sse' : 'http'}
                items={buildConnectionTabs(localJson, sseJson, httpJson, onCopy)}
              />
            </div>
          </Card>
        </Col>
      </Row>
    </Card>
  )
}
