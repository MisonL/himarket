import { Button, Card, Collapse, Select } from "antd";
import { CopyOutlined } from "@ant-design/icons";
import { formatDomainWithPort } from "@/lib/utils";

interface ApiProductModelConfigCardProps {
  modelAPIConfig: any;
  onCopy: (text: string) => void;
  selectedDomainIndex: number;
  onDomainChange: (value: number) => void;
}

function getMatchTypePrefix(matchType: string) {
  switch (matchType) {
    case "Exact":
      return "等于";
    case "Prefix":
      return "前缀是";
    case "Regex":
      return "正则是";
    default:
      return "等于";
  }
}

function getModelCategoryText(category: string) {
  switch (category) {
    case "Text":
      return "文本生成";
    case "Image":
      return "图片生成";
    case "Video":
      return "视频生成";
    case "Audio":
      return "语音合成";
    case "Embedding":
      return "向量化（Embedding）";
    case "Rerank":
      return "文本排序（Rerank）";
    case "Others":
      return "其他";
    default:
      return category || "未知";
  }
}

function getUniqueDomains(routes: any[]) {
  const domainsMap = new Map<
    string,
    { domain: string; port?: number; protocol: string }
  >();
  routes.forEach(route => {
    if (route.domains && route.domains.length > 0) {
      route.domains.forEach((domain: any) => {
        const key = `${domain.protocol}://${domain.domain}${domain.port ? `:${domain.port}` : ""}`;
        domainsMap.set(key, domain);
      });
    }
  });
  return Array.from(domainsMap.values());
}

function getRouteUrl(route: any, domains: any[], domainIndex: number) {
  if (domains.length > domainIndex) {
    const selectedDomain = domains[domainIndex];
    const formattedDomain = formatDomainWithPort(
      selectedDomain.domain,
      selectedDomain.port,
      selectedDomain.protocol
    );
    const path = route.match?.path?.value || "/";
    return `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`;
  }
  if (route.domains && route.domains.length > 0) {
    const domain = route.domains[0];
    const formattedDomain = formatDomainWithPort(
      domain.domain,
      domain.port,
      domain.protocol
    );
    const path = route.match?.path?.value || "/";
    return `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
  }
  return "";
}

function getRouteLabel(route: any, domains: any[], domainIndex: number) {
  if (!route.match) {
    return "Unknown Route";
  }
  const path = route.match.path?.value || "/";
  const pathType = route.match.path?.type;
  const suffix = pathType === "Prefix" ? "*" : pathType === "Regex" ? "~" : "";
  const description =
    route.description && route.description.trim()
      ? ` - ${route.description}`
      : "";
  return `${getRouteUrl(route, domains, domainIndex)}${suffix}${description}`;
}

export function ApiProductModelConfigCard({
  modelAPIConfig,
  onCopy,
  selectedDomainIndex,
  onDomainChange,
}: ApiProductModelConfigCardProps) {
  const routes = modelAPIConfig.routes || [];
  const protocols = modelAPIConfig.aiProtocols || [];
  const domains = getUniqueDomains(routes);
  const domainOptions = domains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(
      domain.domain,
      domain.port,
      domain.protocol
    );
    return {
      value: index,
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
    };
  });

  return (
    <Card title="配置详情">
      <div className="space-y-4">
        {modelAPIConfig.modelCategory ? (
          <div className="text-sm">
            <span className="text-gray-700">适用场景: </span>
            <span className="font-medium">
              {getModelCategoryText(modelAPIConfig.modelCategory)}
            </span>
          </div>
        ) : null}

        <div className="text-sm">
          <span className="text-gray-700">协议: </span>
          <span className="font-medium">{protocols.join(", ")}</span>
        </div>

        {routes.length > 0 ? (
          <div>
            <div className="text-sm text-gray-600 mb-3">路由配置:</div>
            {domainOptions.length > 0 ? (
              <div className="mb-2">
                <div className="flex items-stretch border border-gray-200 rounded-md overflow-hidden">
                  <div className="bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                    域名
                  </div>
                  <div className="flex-1">
                    <Select
                      value={selectedDomainIndex}
                      onChange={onDomainChange}
                      className="w-full"
                      placeholder="选择域名"
                      size="middle"
                      bordered={false}
                      style={{ fontSize: "12px", height: "100%" }}
                    >
                      {domainOptions.map(option => (
                        <Select.Option key={option.value} value={option.value}>
                          <span className="text-xs text-gray-900 font-mono">
                            {option.label}
                          </span>
                        </Select.Option>
                      ))}
                    </Select>
                  </div>
                </div>
              </div>
            ) : null}

            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <Collapse ghost expandIconPosition="end">
                {routes.map((route: any, index: number) => (
                  <Collapse.Panel
                    key={index}
                    header={
                      <div className="flex items-center justify-between py-3 px-4 hover:bg-gray-50">
                        <div className="flex-1">
                          <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                            {getRouteLabel(route, domains, selectedDomainIndex)}
                            {route.builtin ? (
                              <span className="ml-2 px-2 py-0.5 text-xs bg-green-100 text-green-800 rounded-full">
                                默认
                              </span>
                            ) : null}
                          </div>
                          <div className="text-xs text-gray-500">
                            方法:{" "}
                            <span className="font-medium text-gray-700">
                              {route.match?.methods
                                ? route.match.methods.join(", ")
                                : "ANY"}
                            </span>
                          </div>
                        </div>
                        <Button
                          size="small"
                          type="text"
                          onClick={e => {
                            e.stopPropagation();
                            onCopy(
                              getRouteUrl(route, domains, selectedDomainIndex)
                            );
                          }}
                        >
                          <CopyOutlined />
                        </Button>
                      </div>
                    }
                    style={{
                      borderBottom:
                        index < routes.length - 1
                          ? "1px solid #e5e7eb"
                          : "none",
                    }}
                  >
                    <div className="pl-4 space-y-3">
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <div className="text-xs text-gray-500">路径:</div>
                          <div className="font-mono">
                            {getMatchTypePrefix(route.match?.path?.type)}{" "}
                            {route.match?.path?.value}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-gray-500">方法:</div>
                          <div>
                            {route.match?.methods
                              ? route.match.methods.join(", ")
                              : "ANY"}
                          </div>
                        </div>
                      </div>
                    </div>
                  </Collapse.Panel>
                ))}
              </Collapse>
            </div>
          </div>
        ) : null}
      </div>
    </Card>
  );
}
