import { Card, Form, Input, Switch, Divider, message } from "antd";
import { useMemo } from "react";
import {
  Portal,
  ThirdPartyAuthConfig,
  CasConfig,
  OidcConfig,
  OAuth2Config,
  LdapConfig,
} from "@/types";
import { portalApi } from "@/lib/api";
import { ThirdPartyAuthManager } from "./ThirdPartyAuthManager";

// 强制本地定义认证类型字符串，防止枚举引用异常
const AUTH_TYPES = {
  OIDC: "OIDC",
  CAS: "CAS",
  LDAP: "LDAP",
  OAUTH2: "OAUTH2",
};

interface PortalSecurityProps {
  portal: Portal;
  onRefresh?: () => void;
}

export function PortalSecurity({ portal, onRefresh }: PortalSecurityProps) {
  const [form] = Form.useForm();

  const handleSave = async () => {
    try {
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
      onRefresh?.();
    } catch (error) {
      message.error("保存安全设置失败");
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

      // 使用硬编码字符串过滤，防止类型不匹配
      const oidcConfigs = configs
        .filter(config => String(config.type) === AUTH_TYPES.OIDC)
        .map(config => {
          const { type, ...rest } = config;
          return rest;
        });

      const casConfigs = configs
        .filter(config => String(config.type) === AUTH_TYPES.CAS)
        .map(config => {
          const { type, ...rest } = config;
          return rest;
        });

      const oauth2Configs = configs
        .filter(config => String(config.type) === AUTH_TYPES.OAUTH2)
        .map(config => {
          const { type, ...rest } = config;
          return rest;
        });

      const ldapConfigs = configs
        .filter(config => String(config.type) === AUTH_TYPES.LDAP)
        .map(config => {
          const { type, ...rest } = config;
          return rest;
        });

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
        configs.push({ ...c, type: AUTH_TYPES.OIDC } as any);
      });
    }

    if (portal.portalSettingConfig?.casConfigs) {
      portal.portalSettingConfig.casConfigs.forEach(c => {
        configs.push({ ...c, type: AUTH_TYPES.CAS } as any);
      });
    }

    if (portal.portalSettingConfig?.oauth2Configs) {
      portal.portalSettingConfig.oauth2Configs.forEach(c => {
        configs.push({ ...c, type: AUTH_TYPES.OAUTH2 } as any);
      });
    }

    if (portal.portalSettingConfig?.ldapConfigs) {
      portal.portalSettingConfig.ldapConfigs.forEach(c => {
        configs.push({ ...c, type: AUTH_TYPES.LDAP } as any);
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
            <h3 className="text-lg font-medium">基本安全配置</h3>
            <div className="grid grid-cols-2 gap-6">
              <Form.Item
                name="builtinAuthEnabled"
                label="账号密码登录"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
              </Form.Item>
              <Form.Item
                name="autoApproveDevelopers"
                label="开发者自动审批"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
              </Form.Item>
              <Form.Item
                name="autoApproveSubscriptions"
                label="订阅自动审批"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
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
