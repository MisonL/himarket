import { Card, Divider, Form, Input, Spin, Switch, message } from "antd";
import { useMemo, useState } from "react";
import {
  AuthenticationType,
  Portal,
  ThirdPartyAuthConfig,
  CasConfig,
  OidcConfig,
  OAuth2Config,
  LdapConfig,
} from "@/types";
import { portalApi } from "@/lib/api";
import { ThirdPartyAuthManager } from "./ThirdPartyAuthManager";

interface PortalSecurityProps {
  portal: Portal;
  onRefresh?: () => void;
}

function omitType<T extends ThirdPartyAuthConfig>(config: T): Omit<T, "type"> {
  const rest = { ...config };
  delete rest.type;
  return rest;
}

export function PortalSecurity({ portal, onRefresh }: PortalSecurityProps) {
  const [form] = Form.useForm();
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsStatusText, setSettingsStatusText] =
    useState("修改后会自动保存。");

  const handleSave = async () => {
    try {
      setSavingSettings(true);
      setSettingsStatusText("正在保存安全配置...");
      const values = await form.validateFields();

      await portalApi.updatePortal(portal.portalId, {
        name: portal.name,
        description: portal.description,
        portalSettingConfig: {
          ...portal.portalSettingConfig,
          builtinAuthEnabled: values.builtinAuthEnabled,
          autoApproveDevelopers: values.autoApproveDevelopers,
          autoApproveSubscriptions: values.autoApproveSubscriptions,
          frontendRedirectUrl: values.frontendRedirectUrl?.trim() || undefined,
        },
        portalDomainConfig: portal.portalDomainConfig,
        portalUiConfig: portal.portalUiConfig,
      });

      message.success("安全设置保存成功");
      setSettingsStatusText("安全配置已保存。");
      onRefresh?.();
    } catch {
      message.error("保存安全设置失败");
      setSettingsStatusText("保存失败，请检查输入后重试。");
    } finally {
      setSavingSettings(false);
    }
  };

  const handleSettingUpdate = () => {
    handleSave();
  };

  const handleSaveThirdPartyAuth = async (configs: ThirdPartyAuthConfig[]) => {
    try {
      const currentValues = form.getFieldsValue([
        "builtinAuthEnabled",
        "autoApproveDevelopers",
        "autoApproveSubscriptions",
        "frontendRedirectUrl",
      ]);
      const frontendRedirectUrl =
        (currentValues.frontendRedirectUrl as string | undefined)?.trim() ||
        undefined;

      const oidcConfigs = configs
        .filter(
          (config): config is OidcConfig & { type: AuthenticationType.OIDC } =>
            config.type === AuthenticationType.OIDC
        )
        .map(omitType);

      const casConfigs = configs
        .filter(
          (config): config is CasConfig & { type: AuthenticationType.CAS } =>
            config.type === AuthenticationType.CAS
        )
        .map(omitType);

      const oauth2Configs = configs
        .filter(
          (
            config
          ): config is OAuth2Config & { type: AuthenticationType.OAUTH2 } =>
            config.type === AuthenticationType.OAUTH2
        )
        .map(omitType);

      const ldapConfigs = configs
        .filter(
          (config): config is LdapConfig & { type: AuthenticationType.LDAP } =>
            config.type === AuthenticationType.LDAP
        )
        .map(omitType);

      const updateData = {
        ...portal,
        portalSettingConfig: {
          ...portal.portalSettingConfig,
          builtinAuthEnabled: currentValues.builtinAuthEnabled,
          autoApproveDevelopers: currentValues.autoApproveDevelopers,
          autoApproveSubscriptions: currentValues.autoApproveSubscriptions,
          frontendRedirectUrl: frontendRedirectUrl,
          oidcConfigs: oidcConfigs as OidcConfig[],
          casConfigs: casConfigs as CasConfig[],
          ldapConfigs: ldapConfigs as LdapConfig[],
          oauth2Configs: oauth2Configs as OAuth2Config[],
        },
      };

      await portalApi.updatePortal(portal.portalId, updateData);
      onRefresh?.();
    } catch (error) {
      throw error;
    }
  };

  const thirdPartyAuthConfigs = useMemo((): ThirdPartyAuthConfig[] => {
    const configs: ThirdPartyAuthConfig[] = [];

    if (portal.portalSettingConfig?.oidcConfigs) {
      portal.portalSettingConfig.oidcConfigs.forEach(c => {
        configs.push({ ...c, type: AuthenticationType.OIDC });
      });
    }

    if (portal.portalSettingConfig?.casConfigs) {
      portal.portalSettingConfig.casConfigs.forEach(c => {
        configs.push({ ...c, type: AuthenticationType.CAS });
      });
    }

    if (portal.portalSettingConfig?.oauth2Configs) {
      portal.portalSettingConfig.oauth2Configs.forEach(c => {
        configs.push({ ...c, type: AuthenticationType.OAUTH2 });
      });
    }

    if (portal.portalSettingConfig?.ldapConfigs) {
      portal.portalSettingConfig.ldapConfigs.forEach(c => {
        configs.push({ ...c, type: AuthenticationType.LDAP });
      });
    }

    return configs;
  }, [portal.portalSettingConfig]);

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">Portal安全配置</h1>
        <p className="text-gray-600">配置Portal的认证与审批方式</p>
      </div>

      <Form
        form={form}
        layout="vertical"
        initialValues={{
          builtinAuthEnabled: portal.portalSettingConfig?.builtinAuthEnabled,
          autoApproveDevelopers:
            portal.portalSettingConfig?.autoApproveDevelopers,
          autoApproveSubscriptions:
            portal.portalSettingConfig?.autoApproveSubscriptions,
          frontendRedirectUrl: portal.portalSettingConfig?.frontendRedirectUrl,
        }}
      >
        <Card>
          <div className="space-y-6">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h3 className="text-lg font-medium">基本安全配置</h3>
                <p className="mt-1 text-sm text-gray-500">
                  {settingsStatusText}
                </p>
              </div>
              {savingSettings ? <Spin size="small" /> : null}
            </div>
            <div className="grid grid-cols-2 gap-6">
              <Form.Item
                name="builtinAuthEnabled"
                label="账号密码登录"
                valuePropName="checked"
              >
                <Switch
                  disabled={savingSettings}
                  onChange={() => handleSettingUpdate()}
                />
              </Form.Item>
              <Form.Item
                name="autoApproveDevelopers"
                label="开发者自动审批"
                valuePropName="checked"
              >
                <Switch
                  disabled={savingSettings}
                  onChange={() => handleSettingUpdate()}
                />
              </Form.Item>
              <Form.Item
                name="autoApproveSubscriptions"
                label="订阅自动审批"
                valuePropName="checked"
              >
                <Switch
                  disabled={savingSettings}
                  onChange={() => handleSettingUpdate()}
                />
              </Form.Item>
            </div>

            <Form.Item
              name="frontendRedirectUrl"
              label="前端回调基址"
              extra="OIDC 和 CAS 回调将基于该地址生成，需填写 Portal 对外访问的前端域名基址，例如 https://portal.example.com"
              rules={[{ type: "url", message: "请输入有效的 URL" }]}
            >
              <Input
                placeholder="https://portal.example.com"
                disabled={savingSettings}
                onBlur={() => handleSettingUpdate()}
              />
            </Form.Item>

            <Divider />

            <ThirdPartyAuthManager
              portalId={portal.portalId}
              configs={thirdPartyAuthConfigs}
              onSave={handleSaveThirdPartyAuth}
            />
          </div>
        </Card>
      </Form>
    </div>
  );
}
