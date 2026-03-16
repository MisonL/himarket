import React, { Suspense, lazy, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Button, Space, Tabs, message } from "antd";
import { CopyOutlined, DownloadOutlined } from "@ant-design/icons";
import { ProductDetailLayout } from "../components/ProductDetailLayout";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";

const SwaggerUIWrapper = lazy(() =>
  import("../components/SwaggerUIWrapper").then(module => ({
    default: module.SwaggerUIWrapper,
  }))
);
const MarkdownRender = lazy(() => import("../components/MarkdownRender"));

function ApiDetailPage() {
  const { apiProductId } = useParams();
  const [, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [apiData, setApiData] = useState<IProductDetail>();
  const [baseUrl, setBaseUrl] = useState<string>("");
  const [examplePath, setExamplePath] = useState<string>("/{path}");
  const [exampleMethod, setExampleMethod] = useState<string>("GET");

  const loadOpenApiDocument = async (spec: string) => {
    try {
      const yaml = await import("js-yaml");
      return yaml.load(spec) as {
        servers?: Array<{ url?: string }>;
        paths?: Record<string, Record<string, unknown>>;
      };
    } catch {
      return JSON.parse(spec) as {
        servers?: Array<{ url?: string }>;
        paths?: Record<string, Record<string, unknown>>;
      };
    }
  };

  const fetchApiDetail = React.useCallback(async () => {
    setLoading(true);
    setError("");
    if (!apiProductId) return;
    try {
      const response = await APIs.getProduct({ id: apiProductId });
      if (response.code === "SUCCESS" && response.data) {
        setApiData(response.data);

        if (response.data.apiConfig?.spec) {
          try {
            const openApiDoc = await loadOpenApiDocument(response.data.apiConfig.spec);
            let serverUrl = openApiDoc?.servers?.[0]?.url || "";
            if (serverUrl && serverUrl.endsWith("/")) {
              serverUrl = serverUrl.slice(0, -1);
            }
            setBaseUrl(serverUrl);

            const paths = openApiDoc?.paths;
            if (paths && typeof paths === "object") {
              const pathEntries = Object.entries(paths);
              if (pathEntries.length > 0) {
                const [firstPath, pathMethods] = pathEntries[0];
                if (pathMethods && typeof pathMethods === "object") {
                  const methods = Object.keys(pathMethods);
                  if (methods.length > 0) {
                    setExamplePath(firstPath);
                    setExampleMethod(methods[0].toUpperCase());
                  }
                }
              }
            }
          } catch (parseError) {
            console.error("解析OpenAPI规范失败:", parseError);
          }
        }
      }
    } catch (fetchError) {
      console.error("获取API详情失败:", fetchError);
      setError("加载失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, [apiProductId]);

  useEffect(() => {
    if (!apiProductId) return;
    fetchApiDetail();
  }, [apiProductId, fetchApiDetail]);

  const leftContent = apiData ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6 pt-0">
      <Tabs
        size="large"
        defaultActiveKey="overview"
        items={[
          {
            key: "overview",
            label: "概览",
            children: apiData.document ? (
              <div className="min-h-[400px] prose prose-lg">
                <Suspense fallback={null}>
                  <MarkdownRender content={apiData.document} />
                </Suspense>
              </div>
            ) : (
              <div className="text-gray-500 text-center py-16">暂无概览信息</div>
            ),
          },
          {
            key: "openapi-spec",
            label: "OpenAPI 规范",
            children: (
              <div>
                {apiData.apiConfig?.spec ? (
                  <Suspense fallback={null}>
                    <SwaggerUIWrapper apiSpec={apiData.apiConfig.spec} />
                  </Suspense>
                ) : (
                  <div className="text-gray-500 text-center py-16">暂无OpenAPI规范</div>
                )}
              </div>
            ),
          },
        ]}
      />
    </div>
  ) : null;

  const rightContent = (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      <h3 className="text-base font-semibold mb-4 text-gray-900">快速开始</h3>
      <Tabs
        defaultActiveKey="curl"
        items={[
          {
            key: "curl",
            label: "cURL",
            children: (
              <div className="space-y-4">
                <div className="relative">
                  <pre className="bg-gray-900 text-gray-100 p-4 rounded-xl text-xs overflow-x-auto whitespace-pre-wrap border border-gray-700">
                    <code>{`curl -X ${exampleMethod} \\
  '${baseUrl || "https://api.example.com"}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`}</code>
                  </pre>
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined />}
                    className="absolute top-2 right-2 text-gray-400 hover:text-white"
                    onClick={() => {
                      const curlCommand = `curl -X ${exampleMethod} \\
  '${baseUrl || "https://api.example.com"}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`;
                      navigator.clipboard.writeText(curlCommand);
                      message.success("cURL命令已复制到剪贴板", 1);
                    }}
                  />
                </div>
              </div>
            ),
          },
          {
            key: "download",
            label: "下载",
            children: (
              <div className="space-y-4">
                <div className="text-xs text-gray-500 mb-3">
                  下载完整的OpenAPI规范文件，用于代码生成、API测试等场景
                </div>
                <Space direction="vertical" className="w-full">
                  <Button
                    block
                    type="primary"
                    icon={<DownloadOutlined />}
                    onClick={() => {
                      if (!apiData?.apiConfig?.spec) {
                        return;
                      }
                      const blob = new Blob([apiData.apiConfig.spec], {
                        type: "text/yaml",
                      });
                      const url = URL.createObjectURL(blob);
                      const link = document.createElement("a");
                      link.href = url;
                      link.download = `${apiData.name || "api"}-openapi.yaml`;
                      document.body.appendChild(link);
                      link.click();
                      document.body.removeChild(link);
                      URL.revokeObjectURL(url);
                      message.success("OpenAPI规范文件下载成功", 1);
                    }}
                  >
                    下载 YAML
                  </Button>
                  <Button
                    block
                    icon={<DownloadOutlined />}
                    onClick={async () => {
                      if (!apiData?.apiConfig?.spec) {
                        return;
                      }
                      try {
                        const doc = await loadOpenApiDocument(apiData.apiConfig.spec);
                        const jsonSpec = JSON.stringify(doc, null, 2);
                        const blob = new Blob([jsonSpec], {
                          type: "application/json",
                        });
                        const url = URL.createObjectURL(blob);
                        const link = document.createElement("a");
                        link.href = url;
                        link.download = `${apiData.name || "api"}-openapi.json`;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        URL.revokeObjectURL(url);
                        message.success("OpenAPI规范文件下载成功", 1);
                      } catch (downloadError) {
                        console.log(downloadError);
                        message.error("转换JSON格式失败");
                      }
                    }}
                  >
                    下载 JSON
                  </Button>
                </Space>
              </div>
            ),
          },
        ]}
      />
    </div>
  );

  return (
    <ProductDetailLayout
      loading={!apiData && !error}
      error={error || undefined}
      headerProps={
        apiData
          ? {
              name: apiData.name,
              description: apiData.description,
              icon: apiData.icon,
              defaultIcon: "/logo.svg",
              updatedAt: apiData.updatedAt,
              productType: "REST_API",
            }
          : undefined
      }
      leftContent={leftContent}
      rightContent={rightContent}
    />
  );
}

export default ApiDetailPage;
