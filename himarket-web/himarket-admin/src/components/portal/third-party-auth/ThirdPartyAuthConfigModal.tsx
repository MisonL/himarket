import type { ReactNode } from "react";
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
  type FormInstance,
} from "antd";
import { AuthenticationType, ThirdPartyAuthConfig } from "@/types";
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
import { RightOutlined } from "@ant-design/icons";

interface ThirdPartyAuthConfigModalProps {
  configs: ThirdPartyAuthConfig[];
  editingConfig: ThirdPartyAuthConfig | null;
  form: FormInstance;
  loading: boolean;
  modalVisible: boolean;
  currentStep: number;
  selectedType: AuthenticationType | null;
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
  const renderTypeSelector = () => (
    <Form.Item
      name="type"
      label="认证类型"
      rules={[{ required: true, message: "请选择认证类型" }]}
    >
      <Select placeholder="请选择认证方式" size="large">
        <Select.Option value={AuthenticationType.OIDC}>
          <div className="py-2">
            <div className="font-medium">
              OIDC（适用于支持OpenID Connect的身份提供商认证）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AuthenticationType.CAS}>
          <div className="py-2">
            <div className="font-medium">
              CAS（适用于兼容 CAS 协议的单点登录）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AuthenticationType.LDAP}>
          <div className="py-2">
            <div className="font-medium">
              LDAP（适用于企业目录服务登录）
            </div>
          </div>
        </Select.Option>
        <Select.Option value={AuthenticationType.OAUTH2}>
          <div className="py-2">
            <div className="font-medium">
              OAuth2（适用于服务间集成）
            </div>
          </div>
        </Select.Option>
      </Select>
    </Form.Item>
  );

  const renderSelectedForm = (): ReactNode => {
    if (selectedType === AuthenticationType.OIDC) {
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
    if (selectedType === AuthenticationType.CAS) {
      return (
        <div className="space-y-6">
          <CasCoreSection />
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
                    高级配置 (协议细节、身份映射、策略等)
                  </span>
                ),
                children: (
                  <div className="space-y-8 py-4">
                    <CasValidationSection />
                    <CasLoginBehaviorSection />
                    <CasIdentityMappingSection />
                    <CasServiceDefinitionSection />
                    <CasAccessStrategySection />
                    <CasProxySection />
                    <CasAttributeReleaseSection />
                    <CasMultifactorSection />
                    <CasAuthenticationPolicySection />
                    <CasExpirationPolicySection />
                    <CasContactsSection />
                    <CasAdvancedSection />
                  </div>
                ),
              },
            ]}
          />
        </div>
      );
    }
    if (selectedType === AuthenticationType.LDAP) {
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
                  placeholder="如: google, company-sso"
                  disabled={editingConfig !== null}
                />
              </Form.Item>
              <Form.Item
                name="name"
                label="显示名称"
                rules={[{ required: true, message: "请输入显示名称" }]}
              >
                <Input placeholder="如: Google登录、公司SSO" />
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
