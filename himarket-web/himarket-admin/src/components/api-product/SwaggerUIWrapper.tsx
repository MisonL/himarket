import React, { useEffect, useMemo, useRef, useState } from "react";
import "./SwaggerUIWrapper.css";
import * as yaml from "js-yaml";
import { message } from "antd";
import { copyToClipboard } from "@/lib/utils";

interface SwaggerUIWrapperProps {
  apiSpec: string;
}

const swaggerBundleUrl = new URL(
  "../../../node_modules/swagger-ui-react/swagger-ui-bundle.js",
  import.meta.url
).href;
const swaggerCssUrl = new URL(
  "../../../node_modules/swagger-ui-react/swagger-ui.css",
  import.meta.url
).href;

interface SwaggerUIBundleConfig {
  spec: unknown;
  domNode: HTMLElement;
  docExpansion: string;
  displayRequestDuration: boolean;
  tryItOutEnabled: boolean;
  filter: boolean;
  showRequestHeaders: boolean;
  showCommonExtensions: boolean;
  defaultModelsExpandDepth: number;
  defaultModelExpandDepth: number;
  displayOperationId: boolean;
  supportedSubmitMethods: string[];
  deepLinking: boolean;
  showMutatedRequest: boolean;
  requestInterceptor: (request: unknown) => unknown;
  responseInterceptor: (response: unknown) => unknown;
  onComplete: () => void;
  syntaxHighlight: {
    activated: boolean;
    theme: string;
  };
  requestSnippetsEnabled: boolean;
  requestSnippets: {
    generators: Record<
      string,
      {
        title: string;
        syntax: string;
      }
    >;
  };
}

type SwaggerUIBundleFactory = (config: SwaggerUIBundleConfig) => unknown;

declare global {
  interface Window {
    SwaggerUIBundle?: SwaggerUIBundleFactory;
  }
}

let swaggerBundlePromise: Promise<SwaggerUIBundleFactory> | null = null;

function ensureSwaggerCss() {
  const existing = document.querySelector(
    'link[data-himarket-swagger-ui="true"]'
  );
  if (existing) {
    return;
  }

  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = swaggerCssUrl;
  link.dataset.himarketSwaggerUi = "true";
  document.head.appendChild(link);
}

function loadSwaggerBundle() {
  if (window.SwaggerUIBundle) {
    return Promise.resolve(window.SwaggerUIBundle);
  }

  if (!swaggerBundlePromise) {
    swaggerBundlePromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = swaggerBundleUrl;
      script.async = true;
      script.dataset.himarketSwaggerUi = "true";
      script.onload = () => {
        if (window.SwaggerUIBundle) {
          resolve(window.SwaggerUIBundle);
          return;
        }
        reject(new Error("Swagger UI bundle loaded without SwaggerUIBundle"));
      };
      script.onerror = () => {
        reject(new Error("Failed to load Swagger UI bundle"));
      };
      document.body.appendChild(script);
    });
  }

  return swaggerBundlePromise;
}

function parseSwaggerSpec(apiSpec: string) {
  let swaggerSpec: any;

  try {
    try {
      swaggerSpec = yaml.load(apiSpec);
    } catch {
      swaggerSpec = JSON.parse(apiSpec);
    }

    if (!swaggerSpec || !swaggerSpec.paths) {
      throw new Error("Invalid OpenAPI specification");
    }

    Object.keys(swaggerSpec.paths).forEach(path => {
      const pathItem = swaggerSpec.paths[path];
      Object.keys(pathItem).forEach(method => {
        const operation = pathItem[method];
        if (operation && typeof operation === "object" && !operation.tags) {
          operation.tags = ["接口列表"];
        }
      });
    });

    return { spec: swaggerSpec, error: null };
  } catch (error) {
    return {
      spec: null,
      error: error instanceof Error ? error : new Error(String(error)),
    };
  }
}

export const SwaggerUIWrapper: React.FC<SwaggerUIWrapperProps> = ({
  apiSpec,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const parsed = useMemo(() => parseSwaggerSpec(apiSpec), [apiSpec]);

  useEffect(() => {
    if (!parsed.spec || !containerRef.current) {
      return;
    }

    let cancelled = false;
    const container = containerRef.current;
    container.innerHTML = "";
    setLoadError(null);

    ensureSwaggerCss();

    loadSwaggerBundle()
      .then(SwaggerUIBundle => {
        if (cancelled || !containerRef.current) {
          return;
        }

        SwaggerUIBundle({
          spec: parsed.spec,
          domNode: containerRef.current,
          docExpansion: "list",
          displayRequestDuration: true,
          tryItOutEnabled: true,
          filter: false,
          showRequestHeaders: true,
          showCommonExtensions: true,
          defaultModelsExpandDepth: 0,
          defaultModelExpandDepth: 0,
          displayOperationId: true,
          supportedSubmitMethods: [
            "get",
            "post",
            "put",
            "delete",
            "patch",
            "head",
            "options",
          ],
          deepLinking: false,
          showMutatedRequest: true,
          requestInterceptor: request => {
            console.log("Request:", request);
            return request;
          },
          responseInterceptor: response => {
            console.log("Response:", response);
            return response;
          },
          onComplete: () => {
            setTimeout(() => {
              const serversContainer = document.querySelector(
                ".swagger-ui .servers"
              );
              if (
                !serversContainer ||
                serversContainer.querySelector(".copy-server-btn")
              ) {
                return;
              }

              const copyBtn = document.createElement("button");
              copyBtn.className = "copy-server-btn";
              copyBtn.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
                </svg>
              `;
              copyBtn.title = "复制服务器地址";
              copyBtn.style.cssText = `
                position: absolute;
                right: 12px;
                top: 50%;
                transform: translateY(-50%);
                background: transparent;
                border: none;
                border-radius: 4px;
                padding: 6px 8px;
                cursor: pointer;
                color: #666;
                transition: all 0.2s;
                z-index: 10;
                display: flex;
                align-items: center;
                justify-content: center;
              `;

              copyBtn.addEventListener("click", async () => {
                const serverSelect = serversContainer.querySelector(
                  "select"
                ) as HTMLSelectElement | null;
                if (serverSelect?.value) {
                  try {
                    await copyToClipboard(serverSelect.value);
                    message.success("服务器地址已复制到剪贴板", 1);
                  } catch {
                    message.error("复制失败，请手动复制");
                  }
                }
              });

              copyBtn.addEventListener("mouseenter", () => {
                copyBtn.style.background = "#f5f5f5";
                copyBtn.style.color = "#1890ff";
              });

              copyBtn.addEventListener("mouseleave", () => {
                copyBtn.style.background = "transparent";
                copyBtn.style.color = "#666";
              });

              serversContainer.appendChild(copyBtn);

              const serverSelect = serversContainer.querySelector(
                "select"
              ) as HTMLSelectElement | null;
              if (serverSelect) {
                serverSelect.style.paddingRight = "50px";
              }
            }, 1000);
          },
          syntaxHighlight: {
            activated: true,
            theme: "agate",
          },
          requestSnippetsEnabled: true,
          requestSnippets: {
            generators: {
              curl_bash: {
                title: "cURL (bash)",
                syntax: "bash",
              },
              curl_powershell: {
                title: "cURL (PowerShell)",
                syntax: "powershell",
              },
              curl_cmd: {
                title: "cURL (CMD)",
                syntax: "bash",
              },
            },
          },
        });
      })
      .catch(error => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : String(error));
        }
      });

    return () => {
      cancelled = true;
      container.innerHTML = "";
    };
  }, [parsed.spec]);

  if (parsed.error) {
    return (
      <div className="text-center text-gray-500 py-8 bg-gray-50 rounded-lg">
        <p>无法解析OpenAPI规范</p>
        <div className="text-sm text-gray-400 mt-2">
          请检查API配置格式是否正确
        </div>
        <div className="text-xs text-gray-400 mt-1">
          错误详情: {parsed.error.message}
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="text-center text-gray-500 py-8 bg-gray-50 rounded-lg">
        <p>Swagger UI 加载失败</p>
        <div className="text-xs text-gray-400 mt-1">错误详情: {loadError}</div>
      </div>
    );
  }

  return <div ref={containerRef} className="swagger-ui-wrapper" />;
};
