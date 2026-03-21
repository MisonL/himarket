import { useEffect, useState, useCallback, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import {
  Alert,
  Button,
  message,
  Tabs,
  Collapse,
  Select,
  Spin,
  Tooltip,
} from "antd";
import { ArrowLeftOutlined, CopyOutlined } from "@ant-design/icons";
import { ProductType } from "../types";
import * as yaml from "js-yaml";
import type { IMCPConfig } from "../lib/apis/typing";
import type { IMcpTool, IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";
import MarkdownRender from "../components/MarkdownRender";
import { copyToClipboard, formatDomainWithPort } from "../lib/utils";
import { useAuth } from "../hooks/useAuth";

type ParsedYamlTool = {
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
};

function McpDetail() {
  const { mcpProductId } = useParams();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<IProductDetail>();
  const [mcpConfig, setMcpConfig] = useState<IMCPConfig>();
  const [toolPreviews, setToolPreviews] = useState<IMcpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [toolsError, setToolsError] = useState("");
  const [httpJson, setHttpJson] = useState("");
  const [sseJson, setSseJson] = useState("");
  const [localJson, setLocalJson] = useState("");
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0);

  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();

  const buildToolPreviewFromYaml = useCallback((yamlString: string): IMcpTool[] => {
    try {
      const parsed = yaml.load(yamlString) as {
        tools?: ParsedYamlTool[];
      };
      if (!Array.isArray(parsed?.tools)) {
        return [];
      }
      return parsed.tools
        .filter(tool => Boolean(tool?.name))
        .map(tool => {
          const properties =
            tool.args?.reduce<Record<string, NonNullable<IMcpTool["inputSchema"]["properties"]>[string]>>(
              (acc, arg) => {
                acc[arg.name] = {
                  type: arg.type,
                  description: arg.description,
                  enum: arg.enum,
                };
                return acc;
              },
              {}
            ) || {};

          return {
            name: tool.name,
            description: tool.description,
            inputSchema: {
              type: "object",
              properties,
              required:
                tool.args
                  ?.filter(arg => arg.required)
                  .map(arg => arg.name) || [],
              additionalProperties: false,
            },
          };
        });
    } catch (error) {
      console.warn("解析YAML配置失败:", error);
      return [];
    }
  }, []);

  const generateConnectionConfig = useCallback(
    (
      domains:
        | Array<{ domain: string; port?: number; protocol: string }>
        | null
        | undefined,
      path: string | null | undefined,
      serverName: string,
      localConfig?: unknown,
      protocolType?: string,
      domainIndex: number = 0
    ) => {
      if (localConfig) {
        const localConfigJson = JSON.stringify(localConfig, null, 2);
        setLocalJson(localConfigJson);
        setHttpJson("");
        setSseJson("");
        return;
      }

      // HTTP/SSE 模式
      if (
        domains &&
        domains.length > 0 &&
        path &&
        domainIndex < domains.length
      ) {
        const domain = domains[domainIndex];
        const formattedDomain = formatDomainWithPort(
          domain.domain,
          domain.port,
          domain.protocol
        );
        const baseUrl = `${domain.protocol}://${formattedDomain}`;
        let endpoint = `${baseUrl}${path}`;

        if (protocolType === "SSE") {
          const sseConfig = `{
  "mcpServers": {
    "${serverName}": {
      "type": "sse",
      "url": "${endpoint}"
    }
  }
}`;
          setSseJson(sseConfig);
          setHttpJson("");
          setLocalJson("");
          return;
        } else if (protocolType === "StreamableHTTP") {
          const httpConfig = `{
  "mcpServers": {
    "${serverName}": {
      "url": "${endpoint}"
    }
  }
}`;
          setHttpJson(httpConfig);
          setSseJson("");
          setLocalJson("");
          return;
        } else {
          const httpConfig = `{
  "mcpServers": {
    "${serverName}": {
      "url": "${endpoint}"
    }
  }
}`;

          const sseConfig = `{
  "mcpServers": {
    "${serverName}": {
      "type": "sse",
      "url": "${endpoint}/sse"
    }
  }
}`;

          setHttpJson(httpConfig);
          setSseJson(sseConfig);
          setLocalJson("");
          return;
        }
      }

      setHttpJson("");
      setSseJson("");
      setLocalJson("");
    },
    [mcpConfig]
  );

  useEffect(() => {
    const fetchDetail = async () => {
      if (!mcpProductId) {
        return;
      }

      setLoading(true);
      setError("");
      try {
        const response = await APIs.getProduct({ id: mcpProductId });
        if (response.code === "SUCCESS" && response.data) {
          setData(response.data);

          // 处理MCP配置（统一使用新结构 mcpConfig）
          if (response.data.type === ProductType.MCP_SERVER) {
            const mcpProduct = response.data;

            if (mcpProduct.mcpConfig) {
              setMcpConfig(mcpProduct.mcpConfig);
              if (mcpProduct.mcpConfig.tools) {
                setToolPreviews(
                  buildToolPreviewFromYaml(mcpProduct.mcpConfig.tools)
                );
              }
            }
          }
        } else {
          setError(response.message || "数据加载失败");
        }
      } catch (error) {
        console.error("API请求失败:", error);
        setError("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [buildToolPreviewFromYaml, mcpProductId]);

  useEffect(() => {
    const fetchTools = async () => {
      if (!mcpProductId) {
        return;
      }

      setToolsLoading(true);
      setToolsError("");
      try {
        const response = await APIs.getMcpTools({ productId: mcpProductId });
        if (response.code === "SUCCESS" && Array.isArray(response.data?.tools)) {
          setToolPreviews(response.data.tools);
        } else if (toolPreviews.length === 0) {
          setToolsError(response.message || "工具预览暂时不可用");
        }
      } catch (fetchError) {
        console.error("获取工具预览失败:", fetchError);
        if (toolPreviews.length === 0) {
          setToolsError("工具预览暂时不可用");
        }
      } finally {
        setToolsLoading(false);
      }
    };

    fetchTools();
  }, [mcpProductId, toolPreviews.length]);

  useEffect(() => {
    if (mcpConfig && data) {
      generateConnectionConfig(
        mcpConfig.mcpServerConfig.domains,
        mcpConfig.mcpServerConfig.path,
        mcpConfig.mcpServerName || data.name,
        mcpConfig.mcpServerConfig.rawConfig,
        mcpConfig.meta?.protocol,
        selectedDomainIndex
      );
    }
  }, [mcpConfig, generateConnectionConfig, selectedDomainIndex, data]);

  const getDomainOptions = (
    domains: Array<{
      domain: string;
      port?: number;
      protocol: string;
      networkType?: string;
    }>
  ) => {
    return domains.map((domain, index) => {
      const formattedDomain = formatDomainWithPort(
        domain.domain,
        domain.port,
        domain.protocol
      );
      return {
        value: index,
        label: `${domain.protocol}://${formattedDomain}`,
        domain: domain,
      };
    });
  };

  const handleCopy = async (text: string) => {
    copyToClipboard(text).then(() => {
      message.success("已复制到剪贴板");
    });
  };

  const getToolParameters = (tool: IMcpTool) => {
    const properties = tool.inputSchema?.properties || {};
    return Object.entries(properties).map(([name, property]) => ({
      name,
      type: property.type || "string",
      description: property.description || "暂无说明",
      required: tool.inputSchema?.required?.includes(name) || false,
      enum: property.enum,
    }));
  };

  const domainOptions = useMemo(() => {
    return getDomainOptions(mcpConfig?.mcpServerConfig?.domains || []);
  }, [mcpConfig?.mcpServerConfig?.domains]);

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  if (!data) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  const { name, description } = data;
  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);

  return (
    <Layout>
      {/* 头部 */}
      <div className="mb-8">
        <button
          onClick={() => navigate(-1)}
          className="
            flex items-center gap-2 mb-4 px-4 py-2 rounded-xl
            text-gray-600 hover:text-colorPrimary
            hover:bg-colorPrimaryBgHover
            transition-all duration-200
          "
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>
        <ProductHeader
          name={name}
          description={description}
          icon={data.icon}
          defaultIcon="/MCP.svg"
          mcpConfig={mcpConfig}
          updatedAt={data.updatedAt}
          productType="MCP_SERVER"
        />
      </div>

      {/* 主要内容区域 - 左右布局 */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧内容 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
            <Tabs
              defaultActiveKey="overview"
              // className="model-detail-tabs"
              items={[
                {
                  key: "overview",
                  label: "概览",
                  children: data.document ? (
                    <div className="min-h-[400px] prose prose-lg">
                      <MarkdownRender content={data.document} />
                    </div>
                  ) : (
                    <div className="py-8 text-center text-gray-500">
                      暂无概览内容
                    </div>
                  ),
                },
                {
                  key: "tools",
                  label: `工具预览 (${toolPreviews.length})`,
                  children:
                    toolsLoading ? (
                      <div className="flex min-h-[240px] items-center justify-center">
                        <Spin tip="加载工具预览中..." />
                      </div>
                    ) : toolsError ? (
                      <Alert
                        message="工具预览暂时不可用"
                        description={toolsError}
                        type="warning"
                        showIcon
                      />
                    ) : toolPreviews.length > 0 ? (
                      <div className="space-y-4">
                        <Alert
                          type="info"
                          showIcon
                          className="rounded-2xl border border-[#dbe7ff] bg-[#f7faff]"
                          message="先看清单，再决定是否订阅"
                          description={
                            isLoggedIn
                              ? "你现在可以查看工具和参数结构。订阅后即可用自己的消费凭证正式接入。"
                              : "游客可以先查看工具和参数结构，登录后再申请订阅并接入自己的 MCP 客户端。"
                          }
                        />
                        <div className="rounded-lg border border-gray-200 bg-gray-50">
                          {toolPreviews.map((tool, idx) => {
                            const parameters = getToolParameters(tool);
                            return (
                              <div
                                key={tool.name}
                                className={
                                  idx < toolPreviews.length - 1
                                    ? "border-b border-gray-200"
                                    : ""
                                }
                              >
                                <Collapse
                                  ghost
                                  expandIconPosition="end"
                                  items={[
                                    {
                                      key: tool.name,
                                      label: (
                                        <div className="flex items-center gap-3">
                                          <span className="font-medium text-gray-900">
                                            {tool.name}
                                          </span>
                                          <span className="rounded-full bg-[#eef2f8] px-2 py-1 text-[11px] text-gray-500">
                                            {parameters.length} 个参数
                                          </span>
                                        </div>
                                      ),
                                      children: (
                                        <div className="px-4 pb-2">
                                          <div className="mb-4 text-gray-600">
                                            {tool.description}
                                          </div>

                                          {parameters.length > 0 ? (
                                            <div>
                                              <p className="mb-3 font-medium text-gray-700">
                                                输入参数:
                                              </p>
                                              {parameters.map((arg, argIdx) => (
                                                <div key={argIdx} className="mb-3">
                                                  <div className="mb-2 flex flex-wrap items-center gap-2">
                                                    <span className="font-medium text-gray-800">
                                                      {arg.name}
                                                    </span>
                                                    <span className="rounded bg-gray-200 px-2 py-1 text-xs text-gray-600">
                                                      {arg.type}
                                                    </span>
                                                    {arg.required && (
                                                      <span className="rounded bg-[#fff1f0] px-2 py-1 text-xs text-[#cf5b56]">
                                                        必填
                                                      </span>
                                                    )}
                                                    {arg.enum &&
                                                      arg.enum.length > 0 && (
                                                        <span className="rounded bg-[#f5f5f5] px-2 py-1 text-xs text-gray-500">
                                                          {`枚举: ${arg.enum.join(" / ")}`}
                                                        </span>
                                                      )}
                                                  </div>
                                                  <div className="rounded-xl border border-[#ececec] bg-white px-3 py-2 text-sm leading-6 text-gray-500">
                                                    {arg.description}
                                                  </div>
                                                </div>
                                              ))}
                                            </div>
                                          ) : (
                                            <div className="text-sm text-gray-500">
                                              这个工具不需要额外参数
                                            </div>
                                          )}
                                        </div>
                                      ),
                                    },
                                  ]}
                                />
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ) : (
                      <div className="py-8 text-center text-gray-500">
                        暂无可预览工具
                      </div>
                    ),
                },
              ]}
            />
          </div>
        </div>

        {/* 右侧连接指导 */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2">
          {mcpConfig && (
            <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
              <div className="flex items-center gap-2 mb-4">
                <h3 className="text-base font-semibold  text-gray-900">
                  连接点配置
                </h3>
                <CopyOutlined
                  className="ml-1 text-sm text-subTitle"
                  onClick={() => {
                    copyToClipboard(
                      domainOptions[selectedDomainIndex].label
                    ).then(() => {
                      message.success("域名已复制");
                    });
                  }}
                />
              </div>

              {/* 域名选择器 */}
              {mcpConfig?.mcpServerConfig?.domains &&
                mcpConfig.mcpServerConfig.domains.length > 0 && (
                  <div className="mb-2">
                    <div className="flex border border-gray-200 rounded-md overflow-hidden">
                      <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                        域名
                      </div>
                      <div className="flex-1 min-w-0">
                        <Select
                          value={selectedDomainIndex}
                          onChange={setSelectedDomainIndex}
                          className="w-full"
                          placeholder="选择域名"
                          size="middle"
                          variant="borderless"
                          style={{
                            fontSize: "12px",
                            height: "100%",
                          }}
                          // options={getDomainOptions(mcpConfig.mcpServerConfig.domains)}
                        >
                          {domainOptions.map(option => (
                            <Select.Option
                              key={option.value}
                              value={option.value}
                            >
                              <Tooltip
                                classNames={{ root: "bg-white" }}
                                title={
                                  <span className="text-gray-900 bg-white">
                                    {option.label}
                                  </span>
                                }
                              >
                                <span className="text-xs text-gray-900 font-mono">
                                  {option.label}
                                </span>
                              </Tooltip>
                            </Select.Option>
                          ))}
                        </Select>
                      </div>
                    </div>
                  </div>
                )}

              <Tabs
                size="small"
                defaultActiveKey={
                  hasLocalConfig ? "local" : sseJson ? "sse" : "http"
                }
                items={(() => {
                  const tabs = [];

                  if (hasLocalConfig) {
                    tabs.push({
                      key: "local",
                      label: "Stdio",
                      children: (
                        <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                          <Button
                            type="text"
                            size="small"
                            icon={<CopyOutlined />}
                            className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                            onClick={() => handleCopy(localJson)}
                          />
                          <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                            <pre className="whitespace-pre p-3">
                              {localJson}
                            </pre>
                          </div>
                        </div>
                      ),
                    });
                  } else {
                    if (sseJson) {
                      tabs.push({
                        key: "sse",
                        label: "SSE",
                        children: (
                          <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                              onClick={() => handleCopy(sseJson)}
                            />
                            <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre p-3">
                                {sseJson}
                              </pre>
                            </div>
                          </div>
                        ),
                      });
                    }

                    if (httpJson) {
                      tabs.push({
                        key: "http",
                        label: "Streamable HTTP",
                        children: (
                          <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                              onClick={() => handleCopy(httpJson)}
                            />
                            <div className="bg-gray-900  text-gray-100 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre p-3">
                                {httpJson}
                              </pre>
                            </div>
                          </div>
                        ),
                      });
                    }
                  }

                  return tabs;
                })()}
              />
              <div className="mt-4 rounded-xl border border-[#ececec] bg-[#fafafa] px-3 py-3 text-xs leading-5 text-colorTextSecondaryCustom">
                先复制连接配置到支持 MCP 的客户端中，再使用你自己的订阅凭证完成正式接入。
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}

export default McpDetail;
