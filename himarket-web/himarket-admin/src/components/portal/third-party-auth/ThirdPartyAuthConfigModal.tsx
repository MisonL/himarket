import type { ReactNode } from "react";
import { useState, useMemo } from "react";
import {
  Select,
  Button,
  Card,
  Divider,
  Form,
  Input,
  Modal,
  Space,
  Steps,
  Switch,
  Collapse,
  Empty,
  type FormInstance,
} from "antd";
import { SearchOutlined, RightOutlined } from "@ant-design/icons";
import { ThirdPartyAuthConfig } from "@/types";
import { CasAdvancedSection } from "./CasAdvancedSection";
import { CasCoreSection } from "./CasCoreSection";
import { CasLoginBehaviorSection } from "./CasLoginBehaviorSection";
import { CasValidationSection } from "./CasValidationSection";
import { LdapFormSection } from "./LdapFormSection";
import { OAuth2FormSection } from "./OAuth2FormSection";
import { OidcFormSection } from "./OidcFormSection";
import { OidcIdentityMappingSection } from "./OidcIdentityMappingSection";
import { CasIdentityMappingSection } from "./CasIdentityMappingSection";
import { CasServiceDefinitionSection } from "./CasServiceDefinitionSection";
import { CasAccessStrategySection } from "./CasAccessStrategySection";
import { CasAttributeReleaseSection } from "./CasAttributeReleaseSection";
import { CasMultifactorSection } from "./CasMultifactorSection";
import { CasAuthenticationPolicySection } from "./CasAuthenticationPolicySection";
import { CasExpirationPolicySection } from "./CasExpirationPolicySection";
import { CasContactsSection } from "./CasContactsSection";
import { CasProxySection } from "./CasProxySection";

// 强制本地定义认证类型字符串，防止枚举引用异常
const AUTH_TYPES = {
  OIDC: "OIDC",
  CAS: "CAS",
  LDAP: "LDAP",
  OAUTH2: "OAUTH2",
};

interface ThirdPartyAuthConfigModalProps {
  configs: ThirdPartyAuthConfig[];
  editingConfig: ThirdPartyAuthConfig | null;
  form: FormInstance;
  loading: boolean;
  modalVisible: boolean;
  currentStep: number;
  selectedType: string | null; // 使用 string 增强兼容性
  onCancel: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSave: () => void;
}

export function ThirdPartyAuthConfigModal({
  configs,
  editingConfig,
  form,
  loading,
  modalVisible,
  currentStep,
  selectedType,
  onCancel,
  onNext,
  onPrevious,
  onSave,
}: ThirdPartyAuthConfigModalProps) {
  const [searchText, setSearchText] = useState("");

  const renderTypeSelector = () => (
    <Form.Item
      name="type"
      label="认证类型"
      rules={[{ required: true, message: "请选择认证类型" }]}
    >
      <Select placeholder="请选择认证方式" size="large">
        <Select.Option value={AUTH_TYPES.OIDC}>
          <div className="py-2">
            <div className="font-medium">
              OIDC（适用于支持OpenID Connect的身份提供商认证）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AUTH_TYPES.CAS}>
          <div className="py-2">
            <div className="font-medium">
              CAS（适用于兼容 CAS 协议的单点登录）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AUTH_TYPES.LDAP}>
          <div className="py-2">
            <div className="font-medium">
              LDAP（适用于企业目录服务登录）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AUTH_TYPES.OAUTH2}>
          <div className="py-2">
            <div className="font-medium">
              OAuth2（适用于服务间集成）
            </div>
          </div>
        </Select.Option>
      </Select>
    </Form.Item>
  );

  const casSections = useMemo(
    () => [
      {
        id: "validation",
        label: "校验与协议细节",
        keywords: ["validation", "format", "version", "xml", "json", "ticket"],
        component: <CasValidationSection />,
      },
      {
        id: "behavior",
        label: "登录行为控制",
        keywords: ["gateway", "renew", "warn", "remember", "login"],
        component: <CasLoginBehaviorSection />,
      },
      {
        id: "mapping",
        label: "用户身份映射",
        keywords: ["mapping", "user", "id", "email", "name", "role", "sync"],
        component: <CasIdentityMappingSection />,
      },
      {
        id: "definition",
        label: "CAS 服务定义 (Metadata)",
        keywords: ["definition", "evaluation", "order", "logout"],
        component: <CasServiceDefinitionSection />,
      },
      {
        id: "proxy",
        label: "代理认证 (Proxy / PGT)",
        keywords: ["proxy", "callback", "pgt", "ticket"],
        component: <CasProxySection />,
      },
      {
        id: "strategy",
        label: "访问访问策略 (Access)",
        keywords: ["strategy", "access", "unauthorized", "redirect"],
        component: <CasAccessStrategySection />,
      },
      {
        id: "release",
        label: "属性释放策略",
        keywords: ["release", "attribute", "allow", "deny"],
        component: <CasAttributeReleaseSection />,
      },
      {
        id: "mfa",
        label: "多因子认证 (MFA)",
        keywords: ["mfa", "multifactor", "provider", "bypass"],
        component: <CasMultifactorSection />,
      },
      {
        id: "policy",
        label: "认证策略",
        keywords: ["policy", "criteria", "handler", "excluded"],
        component: <CasAuthenticationPolicySection />,
      },
      {
        id: "expiration",
        label: "配置有效期",
        keywords: ["expiration", "date", "notify", "expired"],
        component: <CasExpirationPolicySection />,
      },
      {
        id: "contacts",
        label: "技术联系人",
        keywords: ["contact", "email", "phone", "sre"],
        component: <CasContactsSection />,
      },
      {
        id: "advanced",
        label: "其它扩展参数",
        keywords: ["custom", "advanced", "extra"],
        component: <CasAdvancedSection />,
      },
    ],
    []
  );

  const filteredCasSections = useMemo(() => {
    if (!searchText) {
      return casSections;
    }
    const lowerSearch = searchText.toLowerCase();
    return casSections.filter(
      s =>
        s.label.toLowerCase().includes(lowerSearch) ||
        s.keywords.some(k => k.includes(lowerSearch))
    );
  }, [searchText, casSections]);

  const renderSelectedForm = (): ReactNode => {
    const typeStr = String(selectedType);
    if (typeStr === AUTH_TYPES.OIDC) {
      return (
        <div className="space-y-6">
          <OidcFormSection />
          <Collapse
            ghost
            expandIcon={({ isActive }) => (
              <RightOutlined rotate={isActive ? 90 : 0} />
            )}
            items={[
              {
                key: "advanced",
                label: (
                  <span className="font-medium text-blue-600">
                    高级配置 (身份映射、作用域等)
                  </span>
                ),
                children: <OidcIdentityMappingSection />,
              },
            ]}
          />
        </div>
      );
    }
    if (typeStr === AUTH_TYPES.CAS) {
      return (
        <div className="space-y-6">
          <CasCoreSection />
          <Collapse
            ghost
            defaultActiveKey={searchText ? ["advanced"] : []}
            expandIcon={({ isActive }) => (
              <RightOutlined rotate={isActive ? 90 : 0} />
            )}
            items={[
              {
                key: "advanced",
                label: (
                  <span className="font-medium text-blue-600">
                    高级配置 (支持关键词搜索)
                  </span>
                ),
                children: (
                  <div className="space-y-4">
                    <Input
                      placeholder="搜索配置项 (例如: proxy, mapping, ticket...)"
                      prefix={<SearchOutlined className="text-gray-400" />}
                      allowClear
                      size="large"
                      className="mb-4"
                      value={searchText}
                      onChange={e => setSearchText(e.target.value)}
                    />
                    <div className="space-y-8 py-2">
                      {filteredCasSections.length > 0 ? (
                        filteredCasSections.map(s => (
                          <div
                            key={s.id}
                            className="p-4 border border-gray-100 rounded-lg bg-gray-50/30"
                          >
                            <div className="mb-4 font-bold text-gray-700 border-l-4 border-blue-500 pl-3">
                              {s.label}
                            </div>
                            {s.component}
                          </div>
                        ))
                      ) : (
                        <Empty description="未找到匹配的配置项" />
                      )}
                    </div>
                  </div>
                ),
              },
            ]}
          />
        </div>
      );
    }
    if (typeStr === AUTH_TYPES.LDAP) {
      return <LdapFormSection />;
    }
    return <OAuth2FormSection />;
  };

  return (
    <Modal
      title={editingConfig ? "编辑第三方认证配置" : "添加第三方认证配置"}
      open={modalVisible}
      onCancel={onCancel}
      width={800}
      footer={null}
    >
      <Steps
        current={currentStep}
        className="mb-6"
        items={[
          {
            title: "选择类型",
            description: "选择认证协议类型",
          },
          {
            title: "配置认证",
            description: "填写认证参数",
          },
        ]}
      />

      <Form form={form} layout="vertical">
        {currentStep === 0 ? (
          <Card>
            {renderTypeSelector()}
            <div className="flex justify-end">
              <Button type="primary" onClick={onNext}>
                下一步
              </Button>
            </div>
          </Card>
        ) : (
          <div>
            <div className="grid grid-cols-2 gap-4">
              <Form.Item
                name="provider"
                label="提供商标识"
                rules={[
                  { required: true, message: "请输入提供商标识" },
                  {
                    validator: (_, value) => {
                      if (!value) {
                        return Promise.resolve();
                      }

                      const isDuplicate = configs.some(
                        config =>
                          config.provider === value &&
                          (!editingConfig || editingConfig.provider !== value)
                      );

                      return isDuplicate
                        ? Promise.reject(
                            new Error("该提供商标识已存在，请使用不同的标识")
                          )
                        : Promise.resolve();
                    },
                  },
                ]}
              >
                <Input
                  id="auth_config_provider"
                  autoComplete="off"
                  placeholder="如: google, company-sso"
                  disabled={editingConfig !== null}
                />
              </Form.Item>
              <Form.Item
                name="name"
                label="显示名称"
                rules={[{ required: true, message: "请输入显示名称" }]}
              >
                <Input id="auth_config_name" autoComplete="off" placeholder="如: Google登录、公司SSO" />
              </Form.Item>
            </div>

            <Form.Item
              name="enabled"
              label="启用状态"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>

            <Divider />

            {selectedType ? renderSelectedForm() : null}

            <div className="flex justify-between mt-6">
              <Button onClick={onPrevious}>上一步</Button>
              <Space>
                <Button onClick={onCancel}>取消</Button>
                <Button type="primary" loading={loading} onClick={onSave}>
                  {editingConfig ? "更新" : "添加"}
                </Button>
              </Space>
            </div>
          </div>
        )}
      </Form>
    </Modal>
  );
}
