import { useState } from "react";
import {
  Button,
  Form,
  Input,
  Select,
  Switch,
  Table,
  Modal,
  Space,
  message,
  Divider,
  Steps,
  Card,
  Tabs,
  Collapse,
  Radio,
} from "antd";
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  MinusCircleOutlined,
  KeyOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
} from "@ant-design/icons";
import {
  ThirdPartyAuthConfig,
  AuthenticationType,
  GrantType,
  AuthCodeConfig,
  CasConfig,
  OAuth2Config,
  OidcConfig,
  PublicKeyFormat,
  LdapConfig,
} from "@/types";
import { portalApi } from "@/lib/api";

interface ThirdPartyAuthManagerProps {
  portalId?: string;
  configs: ThirdPartyAuthConfig[];
  onSave: (configs: ThirdPartyAuthConfig[]) => Promise<void>;
}

export function ThirdPartyAuthManager({
  portalId,
  configs,
  onSave,
}: ThirdPartyAuthManagerProps) {
  const [form] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editingConfig, setEditingConfig] =
    useState<ThirdPartyAuthConfig | null>(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedType, setSelectedType] = useState<AuthenticationType | null>(
    null
  );

  const formatCommaSeparated = (values?: string[]) => (values || []).join(", ");

  const parseCommaSeparated = (value?: string) =>
    String(value || "")
      .split(",")
      .map(item => item.trim())
      .filter(Boolean);

  const formatJsonObject = (value?: Record<string, string>) =>
    value ? JSON.stringify(value, null, 2) : "";

  const formatJsonArrayObject = (value?: Record<string, string[]>) =>
    value ? JSON.stringify(value, null, 2) : "";

  const parseStringMap = (value?: string) => {
    if (!value || !String(value).trim()) {
      return undefined;
    }
    const parsed = JSON.parse(value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("HTTP Request Headers 必须是 JSON 对象");
    }
    return Object.entries(parsed).reduce<Record<string, string>>(
      (result, [key, item]) => {
        if (typeof item === "string" && key.trim()) {
          result[key] = item;
        }
        return result;
      },
      {}
    );
  };

  const parseStringArrayMap = (value?: string, fieldLabel?: string) => {
    if (!value || !String(value).trim()) {
      return undefined;
    }
    const parsed = JSON.parse(value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error(`${fieldLabel || "属性规则"}必须是 JSON 对象`);
    }
    return Object.entries(parsed).reduce<Record<string, string[]>>(
      (result, [key, item]) => {
        if (!key.trim()) {
          return result;
        }
        if (Array.isArray(item)) {
          const values = item
            .map(entry => String(entry || "").trim())
            .filter(Boolean);
          if (values.length > 0) {
            result[key] = values;
          }
          return result;
        }
        if (typeof item === "string" && item.trim()) {
          result[key] = [item.trim()];
        }
        return result;
      },
      {}
    );
  };

  const parseOptionalNumber = (value?: string | number) => {
    if (value === undefined || value === null || value === "") {
      return undefined;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  // 添加新配置
  const handleAdd = () => {
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    setModalVisible(true);
    form.resetFields();
  };

  // 编辑配置
  const handleEdit = (config: ThirdPartyAuthConfig) => {
    setEditingConfig(config);
    setSelectedType(config.type);
    setCurrentStep(1); // 直接进入配置步骤
    form.resetFields();
    setModalVisible(true);

    // 根据类型设置表单值
    if (config.type === AuthenticationType.OIDC) {
      // OIDC配置：直接使用OidcConfig的字段
      const oidcConfig = config as OidcConfig & {
        type: AuthenticationType.OIDC;
      };

      // 检查是否是手动配置模式（有具体的端点地址）
      const hasManualEndpoints = !!(
        oidcConfig.authCodeConfig?.authorizationEndpoint &&
        oidcConfig.authCodeConfig?.tokenEndpoint &&
        oidcConfig.authCodeConfig?.userInfoEndpoint
      );

      form.setFieldsValue({
        provider: oidcConfig.provider,
        name: oidcConfig.name,
        enabled: oidcConfig.enabled,
        type: oidcConfig.type,
        configMode: hasManualEndpoints ? "manual" : "auto",
        ...oidcConfig.authCodeConfig,
        // 设置OIDC专用的授权模式字段
        oidcGrantType: oidcConfig.grantType || "AUTHORIZATION_CODE",
        // 身份映射字段可能在根级别或authCodeConfig中
        userIdField:
          oidcConfig.identityMapping?.userIdField ||
          oidcConfig.authCodeConfig?.identityMapping?.userIdField,
        userNameField:
          oidcConfig.identityMapping?.userNameField ||
          oidcConfig.authCodeConfig?.identityMapping?.userNameField,
        emailField:
          oidcConfig.identityMapping?.emailField ||
          oidcConfig.authCodeConfig?.identityMapping?.emailField,
      });
    } else if (config.type === AuthenticationType.OAUTH2) {
      // OAuth2配置：直接使用OAuth2Config的字段
      const oauth2Config = config as OAuth2Config & {
        type: AuthenticationType.OAUTH2;
      };
      const hasJwks = !!oauth2Config.jwtBearerConfig?.jwkSetUri;
      form.setFieldsValue({
        provider: oauth2Config.provider,
        name: oauth2Config.name,
        enabled: oauth2Config.enabled,
        type: oauth2Config.type,
        oauth2GrantType: oauth2Config.grantType || GrantType.JWT_BEARER, // 使用oauth2GrantType字段
        oauth2JwtValidationMode: hasJwks ? "JWKS" : "PUBLIC_KEYS",
        oauth2Issuer: oauth2Config.jwtBearerConfig?.issuer,
        oauth2JwkSetUri: oauth2Config.jwtBearerConfig?.jwkSetUri,
        oauth2Audiences: oauth2Config.jwtBearerConfig?.audiences || [],
        userIdField: oauth2Config.identityMapping?.userIdField,
        userNameField: oauth2Config.identityMapping?.userNameField,
        emailField: oauth2Config.identityMapping?.emailField,
        publicKeys: oauth2Config.jwtBearerConfig?.publicKeys || [],
      });
    } else if (config.type === AuthenticationType.CAS) {
      const casConfig = config as CasConfig & { type: AuthenticationType.CAS };
      form.setFieldsValue({
        provider: casConfig.provider,
        name: casConfig.name,
        enabled: casConfig.enabled,
        sloEnabled: casConfig.sloEnabled ?? false,
        type: casConfig.type,
        serverUrl: casConfig.serverUrl,
        loginEndpoint: casConfig.loginEndpoint,
        validateEndpoint: casConfig.validateEndpoint,
        logoutEndpoint: casConfig.logoutEndpoint,
        loginGateway: casConfig.login?.gateway ?? false,
        loginRenew: casConfig.login?.renew ?? false,
        loginWarn: casConfig.login?.warn ?? false,
        loginRememberMe: casConfig.login?.rememberMe ?? false,
        validationProtocolVersion:
          casConfig.validation?.protocolVersion || "CAS3",
        validationResponseFormat: casConfig.validation?.responseFormat || "XML",
        proxyEnabled: casConfig.proxy?.enabled ?? false,
        proxyCallbackPath: casConfig.proxy?.callbackPath,
        proxyCallbackUrlPattern: casConfig.proxy?.callbackUrlPattern,
        proxyEndpoint: casConfig.proxy?.proxyEndpoint,
        proxyTargetServicePattern: casConfig.proxy?.targetServicePattern,
        proxyPolicyMode: casConfig.proxy?.policyMode || "REGEX",
        proxyUseServiceId: casConfig.proxy?.useServiceId ?? false,
        proxyExactMatch: casConfig.proxy?.exactMatch ?? false,
        proxyPolicyEndpoint: casConfig.proxy?.policyEndpoint,
        proxyPolicyHeaders: formatJsonObject(casConfig.proxy?.policyHeaders),
        serviceDefinitionServiceIdPattern:
          casConfig.serviceDefinition?.serviceIdPattern,
        serviceDefinitionServiceId: casConfig.serviceDefinition?.serviceId,
        serviceDefinitionEvaluationOrder:
          casConfig.serviceDefinition?.evaluationOrder ?? 0,
        serviceDefinitionResponseType:
          casConfig.serviceDefinition?.responseType || "REDIRECT",
        serviceDefinitionLogoutType: casConfig.serviceDefinition?.logoutType,
        serviceDefinitionLogoutUrl: casConfig.serviceDefinition?.logoutUrl,
        accessStrategyEnabled: casConfig.accessStrategy?.enabled ?? true,
        accessStrategySsoEnabled: casConfig.accessStrategy?.ssoEnabled ?? true,
        accessStrategyUnauthorizedRedirectUrl:
          casConfig.accessStrategy?.unauthorizedRedirectUrl,
        accessStrategyStartingDateTime:
          casConfig.accessStrategy?.startingDateTime,
        accessStrategyEndingDateTime: casConfig.accessStrategy?.endingDateTime,
        accessStrategyZoneId: casConfig.accessStrategy?.zoneId,
        accessStrategyRequireAllAttributes:
          casConfig.accessStrategy?.requireAllAttributes ?? false,
        accessStrategyCaseInsensitive:
          casConfig.accessStrategy?.caseInsensitive ?? false,
        accessStrategyRequiredAttributes: formatJsonArrayObject(
          casConfig.accessStrategy?.requiredAttributes
        ),
        accessStrategyRejectedAttributes: formatJsonArrayObject(
          casConfig.accessStrategy?.rejectedAttributes
        ),
        delegatedAllowedProviders: formatCommaSeparated(
          casConfig.accessStrategy?.delegatedAuthenticationPolicy
            ?.allowedProviders
        ),
        delegatedPermitUndefined:
          casConfig.accessStrategy?.delegatedAuthenticationPolicy
            ?.permitUndefined ?? true,
        delegatedExclusive:
          casConfig.accessStrategy?.delegatedAuthenticationPolicy?.exclusive ??
          false,
        httpRequestIpAddressPattern:
          casConfig.accessStrategy?.httpRequest?.ipAddressPattern,
        httpRequestUserAgentPattern:
          casConfig.accessStrategy?.httpRequest?.userAgentPattern,
        httpRequestHeaders: formatJsonObject(
          casConfig.accessStrategy?.httpRequest?.headers
        ),
        attributeReleaseAllowedAttributes: formatCommaSeparated(
          casConfig.attributeRelease?.allowedAttributes
        ),
        attributeReleaseMode:
          casConfig.attributeRelease?.mode || "RETURN_ALLOWED",
        multifactorProviders: formatCommaSeparated(
          casConfig.multifactorPolicy?.providers
        ),
        multifactorFailureMode:
          casConfig.multifactorPolicy?.failureMode || "UNDEFINED",
        multifactorBypassEnabled:
          casConfig.multifactorPolicy?.bypassEnabled ?? false,
        multifactorBypassPrincipalAttributeName:
          casConfig.multifactorPolicy?.bypassPrincipalAttributeName,
        multifactorBypassPrincipalAttributeValue:
          casConfig.multifactorPolicy?.bypassPrincipalAttributeValue,
        multifactorBypassIfMissingPrincipalAttribute:
          casConfig.multifactorPolicy?.bypassIfMissingPrincipalAttribute ??
          false,
        multifactorForceExecution:
          casConfig.multifactorPolicy?.forceExecution ?? false,
        authenticationPolicyCriteriaMode:
          casConfig.authenticationPolicy?.criteriaMode || "ALLOWED",
        authenticationPolicyRequiredHandlers: formatCommaSeparated(
          casConfig.authenticationPolicy?.requiredAuthenticationHandlers
        ),
        authenticationPolicyExcludedHandlers: formatCommaSeparated(
          casConfig.authenticationPolicy?.excludedAuthenticationHandlers
        ),
        authenticationPolicyTryAll:
          casConfig.authenticationPolicy?.tryAll ?? false,
        expirationPolicyExpirationDate:
          casConfig.expirationPolicy?.expirationDate,
        expirationPolicyDeleteWhenExpired:
          casConfig.expirationPolicy?.deleteWhenExpired ?? false,
        expirationPolicyNotifyWhenExpired:
          casConfig.expirationPolicy?.notifyWhenExpired ?? false,
        expirationPolicyNotifyWhenDeleted:
          casConfig.expirationPolicy?.notifyWhenDeleted ?? false,
        userIdField: casConfig.identityMapping?.userIdField,
        userNameField: casConfig.identityMapping?.userNameField,
        emailField: casConfig.identityMapping?.emailField,
      });
    } else if (config.type === AuthenticationType.LDAP) {
      const ldapConfig = config as LdapConfig & {
        type: AuthenticationType.LDAP;
      };
      form.setFieldsValue({
        provider: ldapConfig.provider,
        name: ldapConfig.name,
        enabled: ldapConfig.enabled,
        type: ldapConfig.type,
        serverUrl: ldapConfig.serverUrl,
        baseDn: ldapConfig.baseDn,
        bindDn: ldapConfig.bindDn,
        bindPassword: ldapConfig.bindPassword,
        userSearchFilter: ldapConfig.userSearchFilter,
        userIdField: ldapConfig.identityMapping?.userIdField,
        userNameField: ldapConfig.identityMapping?.userNameField,
        emailField: ldapConfig.identityMapping?.emailField,
      });
    }
  };

  // 删除配置
  const handleDelete = async (provider: string, name: string) => {
    Modal.confirm({
      title: "确认删除",
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除第三方认证配置 "${name}" 吗？此操作不可恢复。`,
      okText: "确认删除",
      okType: "danger",
      cancelText: "取消",
      async onOk() {
        try {
          const updatedConfigs = configs.filter(
            config => config.provider !== provider
          );
          await onSave(updatedConfigs);
          message.success("第三方认证配置删除成功");
        } catch {
          message.error("删除第三方认证配置失败");
        }
      },
    });
  };

  const handlePreviewCasServiceDefinition = async (provider: string) => {
    if (!portalId) {
      message.error(
        "当前页面未提供 Portal 上下文，无法导出 CAS service definition"
      );
      return;
    }

    try {
      const definition = await portalApi.exportCasServiceDefinition(
        portalId,
        provider
      );
      Modal.info({
        title: `CAS Service Definition: ${provider}`,
        width: 860,
        okText: "关闭",
        content: (
          <pre className="max-h-[60vh] overflow-auto rounded bg-gray-50 p-4 text-xs leading-5 text-gray-800">
            {JSON.stringify(definition, null, 2)}
          </pre>
        ),
      });
    } catch {
      message.error("导出 CAS service definition 失败");
    }
  };

  // 下一步
  const handleNext = async () => {
    if (currentStep === 0) {
      try {
        const values = await form.validateFields(["type"]);
        setSelectedType(values.type);
        setCurrentStep(1);

        // 为不同类型设置默认值
        if (values.type === AuthenticationType.OAUTH2) {
          form.setFieldsValue({
            oauth2GrantType: GrantType.JWT_BEARER,
            oauth2JwtValidationMode: "PUBLIC_KEYS",
            enabled: true,
          });
        } else if (values.type === AuthenticationType.CAS) {
          form.setFieldsValue({
            enabled: true,
            validationProtocolVersion: "CAS3",
            validationResponseFormat: "XML",
            loginGateway: false,
            loginRenew: false,
            loginWarn: false,
            loginRememberMe: false,
            proxyEnabled: false,
            accessStrategyEnabled: true,
            accessStrategySsoEnabled: true,
            accessStrategyStartingDateTime: undefined,
            accessStrategyEndingDateTime: undefined,
            accessStrategyZoneId: undefined,
            accessStrategyRequireAllAttributes: false,
            accessStrategyCaseInsensitive: false,
            delegatedPermitUndefined: true,
            delegatedExclusive: false,
            attributeReleaseMode: "RETURN_ALLOWED",
            multifactorFailureMode: "UNDEFINED",
            multifactorBypassEnabled: false,
            multifactorBypassIfMissingPrincipalAttribute: false,
            multifactorForceExecution: false,
            authenticationPolicyCriteriaMode: "ALLOWED",
            authenticationPolicyTryAll: false,
            expirationPolicyDeleteWhenExpired: false,
            expirationPolicyNotifyWhenExpired: false,
            expirationPolicyNotifyWhenDeleted: false,
            serviceDefinitionResponseType: "REDIRECT",
            serviceDefinitionEvaluationOrder: 0,
          });
        } else if (values.type === AuthenticationType.LDAP) {
          form.setFieldsValue({
            enabled: true,
            userSearchFilter: "(uid={0})",
          });
        } else if (values.type === AuthenticationType.OIDC) {
          form.setFieldsValue({
            enabled: true,
            configMode: "auto",
          });
        }
      } catch {
        // 验证失败
      }
    }
  };

  // 上一步
  const handlePrevious = () => {
    setCurrentStep(0);
  };

  // 保存配置
  const handleSave = async () => {
    try {
      setLoading(true);

      const values = await form.validateFields();

      let newConfig: ThirdPartyAuthConfig;

      if (selectedType === AuthenticationType.OIDC) {
        // OIDC配置：根据配置模式创建不同的authCodeConfig
        let authCodeConfig: AuthCodeConfig;

        if (values.configMode === "auto") {
          // 自动发现模式：只保存issuer，端点置空（后端会通过issuer自动发现）
          authCodeConfig = {
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            scopes: values.scopes,
            issuer: values.issuer,
            authorizationEndpoint: "", // 自动发现模式下端点为空
            tokenEndpoint: "",
            userInfoEndpoint: "",
            jwkSetUri: "",
            // 可选的身份映射配置
            identityMapping:
              values.userIdField || values.userNameField || values.emailField
                ? {
                    userIdField: values.userIdField || null,
                    userNameField: values.userNameField || null,
                    emailField: values.emailField || null,
                  }
                : undefined,
          };
        } else {
          // 手动配置模式：保存具体的端点地址
          authCodeConfig = {
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            scopes: values.scopes,
            issuer: values.issuer || "", // 手动配置模式下issuer可选
            authorizationEndpoint: values.authorizationEndpoint,
            tokenEndpoint: values.tokenEndpoint,
            userInfoEndpoint: values.userInfoEndpoint,
            jwkSetUri: values.jwkSetUri || "",
            // 可选的身份映射配置
            identityMapping:
              values.userIdField || values.userNameField || values.emailField
                ? {
                    userIdField: values.userIdField || null,
                    userNameField: values.userNameField || null,
                    emailField: values.emailField || null,
                  }
                : undefined,
          };
        }

        newConfig = {
          provider: values.provider,
          name: values.name,
          logoUrl: null,
          enabled: values.enabled ?? true,
          grantType: values.oidcGrantType || ("AUTHORIZATION_CODE" as const), // 使用oidcGrantType字段
          authCodeConfig,
          // 根级别的身份映射（为兼容后端格式）
          identityMapping: authCodeConfig.identityMapping,
          type: AuthenticationType.OIDC,
        } as OidcConfig & { type: AuthenticationType.OIDC };
      } else if (selectedType === AuthenticationType.CAS) {
        newConfig = {
          provider: values.provider,
          name: values.name,
          enabled: values.enabled ?? true,
          sloEnabled: values.sloEnabled ?? false,
          serverUrl: values.serverUrl,
          loginEndpoint: values.loginEndpoint || "",
          validateEndpoint: values.validateEndpoint || "",
          logoutEndpoint: values.logoutEndpoint || "",
          login: {
            gateway: values.loginGateway ?? false,
            renew: values.loginRenew ?? false,
            warn: values.loginWarn ?? false,
            rememberMe: values.loginRememberMe ?? false,
          },
          validation: {
            protocolVersion: values.validationProtocolVersion || "CAS3",
            responseFormat: values.validationResponseFormat || "XML",
          },
          proxy: {
            enabled: values.proxyEnabled ?? false,
            callbackPath: values.proxyCallbackPath || undefined,
            callbackUrlPattern: values.proxyCallbackUrlPattern || undefined,
            proxyEndpoint: values.proxyEndpoint || undefined,
            targetServicePattern:
              values.proxyTargetServicePattern || undefined,
            policyMode: values.proxyPolicyMode || "REGEX",
            useServiceId: values.proxyUseServiceId ?? false,
            exactMatch: values.proxyExactMatch ?? false,
            policyEndpoint: values.proxyPolicyEndpoint || undefined,
            policyHeaders: parseStringMap(values.proxyPolicyHeaders),
          },
          serviceDefinition: {
            serviceIdPattern:
              values.serviceDefinitionServiceIdPattern || undefined,
            serviceId: parseOptionalNumber(values.serviceDefinitionServiceId),
            evaluationOrder:
              parseOptionalNumber(values.serviceDefinitionEvaluationOrder) ?? 0,
            responseType: values.serviceDefinitionResponseType || "REDIRECT",
            logoutType: values.serviceDefinitionLogoutType || undefined,
            logoutUrl: values.serviceDefinitionLogoutUrl || undefined,
          },
          accessStrategy: {
            enabled: values.accessStrategyEnabled ?? true,
            ssoEnabled: values.accessStrategySsoEnabled ?? true,
            unauthorizedRedirectUrl:
              values.accessStrategyUnauthorizedRedirectUrl || undefined,
            startingDateTime:
              values.accessStrategyStartingDateTime || undefined,
            endingDateTime: values.accessStrategyEndingDateTime || undefined,
            zoneId: values.accessStrategyZoneId || undefined,
            requireAllAttributes:
              values.accessStrategyRequireAllAttributes ?? false,
            caseInsensitive: values.accessStrategyCaseInsensitive ?? false,
            requiredAttributes: parseStringArrayMap(
              values.accessStrategyRequiredAttributes,
              "Required Attributes"
            ),
            rejectedAttributes: parseStringArrayMap(
              values.accessStrategyRejectedAttributes,
              "Rejected Attributes"
            ),
            delegatedAuthenticationPolicy: {
              allowedProviders: parseCommaSeparated(
                values.delegatedAllowedProviders
              ),
              permitUndefined: values.delegatedPermitUndefined ?? true,
              exclusive: values.delegatedExclusive ?? false,
            },
            httpRequest: {
              ipAddressPattern: values.httpRequestIpAddressPattern || undefined,
              userAgentPattern: values.httpRequestUserAgentPattern || undefined,
              headers: parseStringMap(values.httpRequestHeaders),
            },
          },
          attributeRelease: {
            mode: values.attributeReleaseMode || "RETURN_ALLOWED",
            allowedAttributes: parseCommaSeparated(
              values.attributeReleaseAllowedAttributes
            ),
          },
          multifactorPolicy: {
            providers: parseCommaSeparated(values.multifactorProviders),
            failureMode: values.multifactorFailureMode || "UNDEFINED",
            bypassEnabled: values.multifactorBypassEnabled ?? false,
            bypassPrincipalAttributeName:
              values.multifactorBypassPrincipalAttributeName || undefined,
            bypassPrincipalAttributeValue:
              values.multifactorBypassPrincipalAttributeValue || undefined,
            bypassIfMissingPrincipalAttribute:
              values.multifactorBypassIfMissingPrincipalAttribute ?? false,
            forceExecution: values.multifactorForceExecution ?? false,
          },
          authenticationPolicy: {
            criteriaMode:
              values.authenticationPolicyCriteriaMode || "ALLOWED",
            requiredAuthenticationHandlers: parseCommaSeparated(
              values.authenticationPolicyRequiredHandlers
            ),
            excludedAuthenticationHandlers: parseCommaSeparated(
              values.authenticationPolicyExcludedHandlers
            ),
            tryAll: values.authenticationPolicyTryAll ?? false,
          },
          expirationPolicy: {
            expirationDate: values.expirationPolicyExpirationDate || undefined,
            deleteWhenExpired:
              values.expirationPolicyDeleteWhenExpired ?? false,
            notifyWhenExpired:
              values.expirationPolicyNotifyWhenExpired ?? false,
            notifyWhenDeleted:
              values.expirationPolicyNotifyWhenDeleted ?? false,
          },
          identityMapping: {
            userIdField: values.userIdField || null,
            userNameField: values.userNameField || null,
            emailField: values.emailField || null,
          },
          type: AuthenticationType.CAS,
        } as CasConfig & { type: AuthenticationType.CAS };
      } else if (selectedType === AuthenticationType.LDAP) {
        newConfig = {
          provider: values.provider,
          name: values.name,
          enabled: values.enabled ?? true,
          serverUrl: values.serverUrl,
          baseDn: values.baseDn,
          bindDn: values.bindDn || "",
          bindPassword: values.bindPassword || "",
          userSearchFilter: values.userSearchFilter || "(uid={0})",
          identityMapping: {
            userIdField: values.userIdField || null,
            userNameField: values.userNameField || null,
            emailField: values.emailField || null,
          },
          type: AuthenticationType.LDAP,
        } as LdapConfig & { type: AuthenticationType.LDAP };
      } else {
        // OAuth2配置：直接创建OAuth2Config格式
        const grantType = values.oauth2GrantType || GrantType.JWT_BEARER; // 使用oauth2GrantType字段
        const validationMode = values.oauth2JwtValidationMode || "PUBLIC_KEYS";
        newConfig = {
          provider: values.provider,
          name: values.name,
          enabled: values.enabled ?? true,
          grantType: grantType,
          jwtBearerConfig:
            grantType === GrantType.JWT_BEARER
              ? validationMode === "JWKS"
                ? {
                    issuer: values.oauth2Issuer,
                    jwkSetUri: values.oauth2JwkSetUri,
                    audiences: values.oauth2Audiences || [],
                    publicKeys: [],
                  }
                : {
                    publicKeys: values.publicKeys || [],
                  }
              : undefined,
          identityMapping: {
            userIdField: values.userIdField || null,
            userNameField: values.userNameField || null,
            emailField: values.emailField || null,
          },
          type: AuthenticationType.OAUTH2,
        } as OAuth2Config & { type: AuthenticationType.OAUTH2 };
      }

      let updatedConfigs;
      if (editingConfig) {
        updatedConfigs = configs.map(config =>
          config.provider === editingConfig.provider ? newConfig : config
        );
      } else {
        updatedConfigs = [...configs, newConfig];
      }

      await onSave(updatedConfigs);

      message.success(
        editingConfig ? "第三方认证配置更新成功" : "第三方认证配置添加成功"
      );
      setModalVisible(false);
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : "保存第三方认证配置失败"
      );
    } finally {
      setLoading(false);
    }
  };

  // 取消
  const handleCancel = () => {
    setModalVisible(false);
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    form.resetFields();
  };

  // OIDC表格列定义（不包含类型列）
  const oidcColumns = [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      ),
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 150,
    },
    {
      title: "授权模式",
      key: "grantType",
      width: 120,
      render: () => <span className="text-gray-600">授权码模式</span>,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled
              className="text-green-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          ) : (
            <MinusCircleFilled
              className="text-gray-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          )}
          <span className="text-gray-700">{enabled ? "已启用" : "已停用"}</span>
        </div>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            onClick={() => handlePreviewCasServiceDefinition(record.provider)}
          >
            预览定义
          </Button>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  // OAuth2表格列定义（不包含类型列）
  const oauth2Columns = [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      ),
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 150,
    },
    {
      title: "授权模式",
      key: "grantType",
      width: 120,
      render: (record: ThirdPartyAuthConfig) => {
        if (record.type === AuthenticationType.OAUTH2) {
          const oauth2Config = record as OAuth2Config & {
            type: AuthenticationType.OAUTH2;
          };
          return (
            <span className="text-gray-600">
              {oauth2Config.grantType === GrantType.JWT_BEARER
                ? "JWT断言"
                : "授权码模式"}
            </span>
          );
        }
        return <span className="text-gray-600">授权码模式</span>;
      },
    },
    {
      title: "验签方式",
      key: "jwtValidationMode",
      width: 120,
      render: (record: ThirdPartyAuthConfig) => {
        if (record.type !== AuthenticationType.OAUTH2) {
          return <span className="text-gray-600">-</span>;
        }

        const oauth2Config = record as OAuth2Config & {
          type: AuthenticationType.OAUTH2;
        };
        if (!oauth2Config.jwtBearerConfig) {
          return <span className="text-gray-600">-</span>;
        }

        return (
          <span className="text-gray-600">
            {oauth2Config.jwtBearerConfig.jwkSetUri ? "JWKS" : "公钥"}
          </span>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled
              className="text-green-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          ) : (
            <MinusCircleFilled
              className="text-gray-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          )}
          <span className="text-gray-700">{enabled ? "已启用" : "已停用"}</span>
        </div>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const casColumns = [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      ),
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 150,
    },
    {
      title: "协议",
      key: "protocol",
      width: 120,
      render: () => <span className="text-gray-600">CAS Ticket</span>,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled
              className="text-green-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          ) : (
            <MinusCircleFilled
              className="text-gray-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          )}
          <span className="text-gray-700">{enabled ? "已启用" : "已停用"}</span>
        </div>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const ldapColumns = [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      ),
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 150,
    },
    {
      title: "协议",
      key: "protocol",
      width: 120,
      render: () => <span className="text-gray-600">LDAP Bind</span>,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled
              className="text-green-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          ) : (
            <MinusCircleFilled
              className="text-gray-500 mr-2"
              style={{ fontSize: "12px" }}
            />
          )}
          <span className="text-gray-700">{enabled ? "已启用" : "已停用"}</span>
        </div>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  // 渲染OIDC配置表单
  const renderOidcForm = () => (
    <div className="space-y-6">
      <Form.Item
        name="oidcGrantType"
        label="授权模式"
        initialValue="AUTHORIZATION_CODE"
      >
        <Select disabled>
          <Select.Option value="AUTHORIZATION_CODE">授权码模式</Select.Option>
        </Select>
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="clientId"
          label="Client ID"
          rules={[{ required: true, message: "请输入 Client ID" }]}
        >
          <Input placeholder="Client ID" />
        </Form.Item>
        <Form.Item
          name="clientSecret"
          label="Client Secret"
          rules={[{ required: true, message: "请输入 Client Secret" }]}
        >
          <Input.Password placeholder="Client Secret" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="scopes"
          label="授权范围"
          rules={[{ required: true, message: "请输入授权范围" }]}
        >
          <Input placeholder="如: openid profile email" />
        </Form.Item>
        <div></div>
      </div>

      <Divider />

      {/* 配置模式选择 */}
      <Form.Item name="configMode" label="端点配置" initialValue="auto">
        <Radio.Group>
          <Radio value="auto">自动发现</Radio>
          <Radio value="manual">手动配置</Radio>
        </Radio.Group>
      </Form.Item>

      {/* 根据配置模式显示不同字段 */}
      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) =>
          prevValues.configMode !== curValues.configMode
        }
      >
        {({ getFieldValue }) => {
          const configMode = getFieldValue("configMode") || "auto";

          if (configMode === "auto") {
            // 自动发现模式：只需要Issuer地址
            return (
              <Form.Item
                name="issuer"
                label="Issuer"
                rules={[
                  { required: true, message: "请输入Issuer地址" },
                  { type: "url", message: "请输入有效的URL" },
                ]}
              >
                <Input placeholder="如: https://accounts.google.com" />
              </Form.Item>
            );
          } else {
            // 手动配置模式：需要各个端点
            return (
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    name="authorizationEndpoint"
                    label="授权端点"
                    rules={[{ required: true, message: "请输入授权端点" }]}
                  >
                    <Input placeholder="Authorization 授权端点" />
                  </Form.Item>
                  <Form.Item
                    name="tokenEndpoint"
                    label="令牌端点"
                    rules={[{ required: true, message: "请输入令牌端点" }]}
                  >
                    <Input placeholder="Token 令牌端点" />
                  </Form.Item>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    name="userInfoEndpoint"
                    label="用户信息端点"
                    rules={[{ required: true, message: "请输入用户信息端点" }]}
                  >
                    <Input placeholder="UserInfo 端点" />
                  </Form.Item>
                  <Form.Item name="jwkSetUri" label="公钥端点">
                    <Input placeholder="可选" />
                  </Form.Item>
                </div>
              </div>
            );
          }
        }}
      </Form.Item>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? "rotate-90" : ""}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                clipRule="evenodd"
              />
            </svg>
          )}
          items={[
            {
              key: "advanced",
              label: (
                <div className="flex items-center text-gray-600">
                  <svg
                    className="w-4 h-4"
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path
                      fillRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      clipRule="evenodd"
                    />
                    <path
                      fillRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      clipRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">
                    策略、代理、身份映射
                  </span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-2 gap-4">
                    <Form.Item
                      name="proxyEnabled"
                      label="启用 Proxy"
                      valuePropName="checked"
                      extra="启用后允许申请 PGT/PT。"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="proxyEndpoint"
                      label="Proxy 端点"
                      extra="可选，默认由 serverUrl 推导 /proxy。"
                    >
                      <Input placeholder="如: https://cas.example.com/cas/proxy" />
                    </Form.Item>
                    <Form.Item
                      name="proxyCallbackPath"
                      label="Proxy Callback Path"
                      extra="可选，优先用于服务端 PGT 回调。"
                    >
                      <Input placeholder="如: /developers/cas/proxy-callback" />
                    </Form.Item>
                    <Form.Item
                      name="proxyCallbackUrlPattern"
                      label="Proxy Callback Pattern"
                      extra="可选，用于导出 CAS proxyPolicy 正则。"
                    >
                      <Input placeholder="如: ^https://portal.example.com/.*/proxy-callback$" />
                    </Form.Item>
                    <Form.Item
                      name="proxyTargetServicePattern"
                      label="Target Service Pattern"
                      extra="可选，用于限制可申请 PT 的目标服务正则。"
                    >
                      <Input placeholder="如: ^https://api.example.com/.*$" />
                    </Form.Item>
                    <Form.Item name="proxyPolicyMode" label="Proxy Policy Mode">
                      <Select>
                        <Select.Option value="REGEX">REGEX</Select.Option>
                        <Select.Option value="REST">REST</Select.Option>
                        <Select.Option value="REFUSE">REFUSE</Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="proxyUseServiceId"
                      label="Use Service ID"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="proxyExactMatch"
                      label="Exact Match"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="proxyPolicyEndpoint"
                      label="Policy Endpoint"
                      extra="REST 模式可选。"
                    >
                      <Input placeholder="如: https://proxy.example.com/policies" />
                    </Form.Item>
                    <Form.Item
                      name="proxyPolicyHeaders"
                      label="Policy Headers"
                      extra='REST 模式可选，JSON 对象，例如 {"X-Proxy-Policy":"enabled"}'
                    >
                      <Input.TextArea
                        rows={4}
                        placeholder='如: {"X-Proxy-Policy":"enabled"}'
                      />
                    </Form.Item>
                  </div>

                  <Divider className="my-2">Service Definition</Divider>
                  <div className="grid grid-cols-2 gap-4">
                    <Form.Item
                      name="serviceDefinitionServiceIdPattern"
                      label="Service ID Pattern"
                    >
                      <Input placeholder="可选，默认自动生成 callback 正则" />
                    </Form.Item>
                    <Form.Item
                      name="serviceDefinitionServiceId"
                      label="Service ID"
                    >
                      <Input placeholder="可选，默认按 portal/provider 派生" />
                    </Form.Item>
                    <Form.Item
                      name="serviceDefinitionEvaluationOrder"
                      label="Evaluation Order"
                    >
                      <Input placeholder="默认: 0" />
                    </Form.Item>
                    <Form.Item
                      name="serviceDefinitionResponseType"
                      label="Response Type"
                      extra="当前登录链仅支持 REDIRECT 和 POST；HEADER 已显式禁用。"
                    >
                      <Select>
                        <Select.Option value="REDIRECT">REDIRECT</Select.Option>
                        <Select.Option value="POST">POST</Select.Option>
                        <Select.Option value="HEADER" disabled>
                          HEADER (Unsupported)
                        </Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="serviceDefinitionLogoutType"
                      label="Logout Type"
                    >
                      <Select allowClear placeholder="默认按 SLO 配置推导">
                        <Select.Option value="NONE">NONE</Select.Option>
                        <Select.Option value="BACK_CHANNEL">
                          BACK_CHANNEL
                        </Select.Option>
                        <Select.Option value="FRONT_CHANNEL">
                          FRONT_CHANNEL
                        </Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="serviceDefinitionLogoutUrl"
                      label="Logout URL"
                    >
                      <Input placeholder="可选，覆盖默认登出跳转地址" />
                    </Form.Item>
                  </div>

                  <Divider className="my-2">访问策略</Divider>
                  <div className="grid grid-cols-2 gap-4">
                    <Form.Item
                      name="accessStrategyEnabled"
                      label="允许访问"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategySsoEnabled"
                      label="允许 SSO"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyUnauthorizedRedirectUrl"
                      label="Unauthorized Redirect URL"
                    >
                      <Input placeholder="如: https://portal.example.com/forbidden" />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyStartingDateTime"
                      label="Access Start DateTime"
                    >
                      <Input placeholder="如: 2026-01-01T09:00:00" />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyEndingDateTime"
                      label="Access End DateTime"
                    >
                      <Input placeholder="如: 2026-12-31T18:00:00" />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyZoneId"
                      label="Access Strategy ZoneId"
                    >
                      <Input placeholder="如: Asia/Shanghai" />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyRequireAllAttributes"
                      label="Require All Attributes"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyCaseInsensitive"
                      label="Access Strategy Case Insensitive"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyRequiredAttributes"
                      label="Required Attributes"
                      extra='JSON 对象，值可为字符串或字符串数组，例如 {"memberOf":["internal","ops"]}'
                    >
                      <Input.TextArea
                        rows={4}
                        placeholder='如: {"memberOf":["internal","ops"]}'
                      />
                    </Form.Item>
                    <Form.Item
                      name="accessStrategyRejectedAttributes"
                      label="Rejected Attributes"
                      extra='JSON 对象，值可为字符串或字符串数组，例如 {"status":["disabled"]}'
                    >
                      <Input.TextArea
                        rows={4}
                        placeholder='如: {"status":["disabled"]}'
                      />
                    </Form.Item>
                    <Form.Item
                      name="delegatedAllowedProviders"
                      label="Delegated Providers"
                      extra="逗号分隔，例如 GithubClient,OidcClient。"
                    >
                      <Input placeholder="如: GithubClient, OidcClient" />
                    </Form.Item>
                    <Form.Item
                      name="delegatedPermitUndefined"
                      label="允许未声明 Delegated Provider"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="delegatedExclusive"
                      label="Delegated Exclusive"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="httpRequestIpAddressPattern"
                      label="IP Address Regex"
                    >
                      <Input placeholder="如: ^127\\.0\\.0\\.1$" />
                    </Form.Item>
                    <Form.Item
                      name="httpRequestUserAgentPattern"
                      label="User-Agent Regex"
                    >
                      <Input placeholder="如: ^curl/.*$" />
                    </Form.Item>
                    <Form.Item
                      name="httpRequestHeaders"
                      label="Required Headers"
                      extra='JSON 对象，例如 {"X-Portal-Scope":"admin"}'
                    >
                      <Input.TextArea
                        rows={4}
                        placeholder='如: {"X-Portal-Scope":"admin"}'
                      />
                    </Form.Item>
                  </div>

                  <Divider className="my-2">属性与 MFA</Divider>
                  <div className="grid grid-cols-2 gap-4">
                    <Form.Item
                      name="attributeReleaseMode"
                      label="Attribute Release Mode"
                    >
                      <Select>
                        <Select.Option value="RETURN_ALLOWED">
                          RETURN_ALLOWED
                        </Select.Option>
                        <Select.Option value="RETURN_ALL">
                          RETURN_ALL
                        </Select.Option>
                        <Select.Option value="DENY_ALL">DENY_ALL</Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="attributeReleaseAllowedAttributes"
                      label="Allowed Attributes"
                      extra="仅在 RETURN_ALLOWED 下生效；为空时回落到身份映射字段。"
                    >
                      <Input placeholder="如: uid, mail, displayName" />
                    </Form.Item>
                    <Form.Item
                      name="multifactorProviders"
                      label="MFA Providers"
                      extra="逗号分隔，例如 mfa-duo,mfa-webauthn。"
                    >
                      <Input placeholder="如: mfa-duo, mfa-webauthn" />
                    </Form.Item>
                    <Form.Item
                      name="multifactorFailureMode"
                      label="MFA Failure Mode"
                    >
                      <Select>
                        <Select.Option value="UNDEFINED">UNDEFINED</Select.Option>
                        <Select.Option value="OPEN">OPEN</Select.Option>
                        <Select.Option value="CLOSED">CLOSED</Select.Option>
                        <Select.Option value="PHANTOM">PHANTOM</Select.Option>
                        <Select.Option value="NONE">NONE</Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="multifactorBypassEnabled"
                      label="MFA Bypass"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="multifactorBypassPrincipalAttributeName"
                      label="Bypass Principal Attribute"
                    >
                      <Input placeholder="如: memberOf" />
                    </Form.Item>
                    <Form.Item
                      name="multifactorBypassPrincipalAttributeValue"
                      label="Bypass Attribute Value"
                    >
                      <Input placeholder="如: internal" />
                    </Form.Item>
                    <Form.Item
                      name="multifactorBypassIfMissingPrincipalAttribute"
                      label="Bypass If Principal Attribute Missing"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="multifactorForceExecution"
                      label="强制执行 MFA"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="authenticationPolicyCriteriaMode"
                      label="Authentication Policy Criteria"
                    >
                      <Select>
                        <Select.Option value="ALLOWED">ALLOWED</Select.Option>
                        <Select.Option value="EXCLUDED">EXCLUDED</Select.Option>
                        <Select.Option value="ANY">ANY</Select.Option>
                        <Select.Option value="ALL">ALL</Select.Option>
                        <Select.Option value="NOT_PREVENTED">
                          NOT_PREVENTED
                        </Select.Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      name="authenticationPolicyRequiredHandlers"
                      label="Required Authentication Handlers"
                      extra="逗号分隔，例如 AcceptUsersAuthenticationHandler,LdapAuthenticationHandler。"
                    >
                      <Input placeholder="如: AcceptUsersAuthenticationHandler, LdapAuthenticationHandler" />
                    </Form.Item>
                    <Form.Item
                      name="authenticationPolicyExcludedHandlers"
                      label="Excluded Authentication Handlers"
                      extra="逗号分隔，例如 BlockedHandler。"
                    >
                      <Input placeholder="如: BlockedHandler" />
                    </Form.Item>
                    <Form.Item
                      name="authenticationPolicyTryAll"
                      label="Authentication Policy Try All"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="expirationPolicyExpirationDate"
                      label="Expiration Date"
                    >
                      <Input placeholder="如: 2030-12-31T23:59:59Z" />
                    </Form.Item>
                    <Form.Item
                      name="expirationPolicyDeleteWhenExpired"
                      label="Delete When Expired"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="expirationPolicyNotifyWhenExpired"
                      label="Notify When Expired"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="expirationPolicyNotifyWhenDeleted"
                      label="Notify When Deleted"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                  </div>

                  <Divider className="my-2">身份映射</Divider>
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item name="userIdField" label="开发者ID">
                      <Input placeholder="默认: sub" />
                    </Form.Item>
                    <Form.Item name="userNameField" label="开发者名称">
                      <Input placeholder="默认: name" />
                    </Form.Item>
                    <Form.Item name="emailField" label="邮箱">
                      <Input placeholder="默认: email" />
                    </Form.Item>
                  </div>

                  <div className="bg-blue-50 p-3 rounded-lg">
                    <div className="flex items-start space-x-2">
                      <div className="text-blue-600 mt-0.5">
                        <svg
                          className="w-4 h-4"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                            clipRule="evenodd"
                          />
                        </svg>
                      </div>
                      <div>
                        <h4 className="text-blue-800 font-medium text-sm">
                          配置说明
                        </h4>
                        <p className="text-blue-700 text-xs mt-1">
                          身份映射用于从OIDC令牌中提取用户信息。如果不填写，系统将使用OIDC标准字段。
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );

  // 渲染OAuth2配置表单
  const renderOAuth2Form = () => (
    <div className="space-y-6">
      <Form.Item
        name="oauth2GrantType"
        label="授权模式"
        initialValue={GrantType.JWT_BEARER}
        rules={[{ required: true }]}
      >
        <Select disabled>
          <Select.Option value={GrantType.JWT_BEARER}>JWT断言</Select.Option>
        </Select>
      </Form.Item>

      <Form.Item
        name="oauth2JwtValidationMode"
        label="验签方式"
        initialValue="PUBLIC_KEYS"
        rules={[{ required: true, message: "请选择验签方式" }]}
      >
        <Radio.Group>
          <Radio value="PUBLIC_KEYS">手工公钥</Radio>
          <Radio value="JWKS">JWKS</Radio>
        </Radio.Group>
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) =>
          prevValues?.oauth2JwtValidationMode !==
          curValues?.oauth2JwtValidationMode
        }
      >
        {({ getFieldValue }) => {
          const mode =
            getFieldValue("oauth2JwtValidationMode") || "PUBLIC_KEYS";
          if (mode === "JWKS") {
            return (
              <div className="space-y-4">
                <Form.Item
                  name="oauth2Issuer"
                  label="Issuer"
                  rules={[
                    { required: true, message: "请输入Issuer" },
                    { type: "url", message: "请输入有效的URL" },
                  ]}
                >
                  <Input placeholder="如: https://cas.example.com/cas/oauth2.0" />
                </Form.Item>
                <Form.Item
                  name="oauth2JwkSetUri"
                  label="JWKS 地址"
                  rules={[
                    { required: true, message: "请输入JWKS地址" },
                    { type: "url", message: "请输入有效的URL" },
                  ]}
                >
                  <Input placeholder="如: https://cas.example.com/cas/oauth2.0/jwks" />
                </Form.Item>
                <Form.Item
                  name="oauth2Audiences"
                  label="Audiences"
                  rules={[
                    { required: true, message: "请输入至少一个Audience" },
                    {
                      validator: (_, value) => {
                        if (!value || value.length === 0) {
                          return Promise.resolve();
                        }
                        const invalid = value.some(
                          (v: string) => !v || !v.trim()
                        );
                        return invalid
                          ? Promise.reject(new Error("Audience 不能为空"))
                          : Promise.resolve();
                      },
                    },
                  ]}
                >
                  <Select
                    mode="tags"
                    placeholder="输入一个或多个audience"
                    tokenSeparators={[",", " "]}
                  />
                </Form.Item>
              </div>
            );
          }

          return (
            <Form.List name="publicKeys">
              {(fields, { add, remove }) => (
                <div className="space-y-4">
                  {fields.length > 0 && (
                    <Collapse
                      size="small"
                      items={fields.map(({ key, name, ...restField }) => ({
                        key: key,
                        label: (
                          <div className="flex items-center">
                            <KeyOutlined className="mr-2" />
                            <span>公钥 {name + 1}</span>
                          </div>
                        ),
                        extra: (
                          <Button
                            type="link"
                            danger
                            size="small"
                            icon={<MinusCircleOutlined />}
                            onClick={e => {
                              e.stopPropagation();
                              remove(name);
                            }}
                          >
                            删除
                          </Button>
                        ),
                        children: (
                          <div className="space-y-4 px-4">
                            <div className="grid grid-cols-3 gap-4">
                              <Form.Item
                                {...restField}
                                name={[name, "kid"]}
                                label="Key ID"
                                rules={[
                                  { required: true, message: "请输入Key ID" },
                                ]}
                              >
                                <Input placeholder="公钥标识符" size="small" />
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[name, "algorithm"]}
                                label="签名算法"
                                rules={[
                                  { required: true, message: "请选择签名算法" },
                                ]}
                              >
                                <Select placeholder="选择签名算法" size="small">
                                  <Select.Option value="RS256">
                                    RS256
                                  </Select.Option>
                                  <Select.Option value="RS384">
                                    RS384
                                  </Select.Option>
                                  <Select.Option value="RS512">
                                    RS512
                                  </Select.Option>
                                  <Select.Option value="ES256">
                                    ES256
                                  </Select.Option>
                                  <Select.Option value="ES384">
                                    ES384
                                  </Select.Option>
                                  <Select.Option value="ES512">
                                    ES512
                                  </Select.Option>
                                </Select>
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[name, "format"]}
                                label="公钥格式"
                                rules={[
                                  { required: true, message: "请选择公钥格式" },
                                ]}
                              >
                                <Select placeholder="选择公钥格式" size="small">
                                  <Select.Option value={PublicKeyFormat.PEM}>
                                    PEM
                                  </Select.Option>
                                  <Select.Option value={PublicKeyFormat.JWK}>
                                    JWK
                                  </Select.Option>
                                </Select>
                              </Form.Item>
                            </div>

                            <Form.Item
                              noStyle
                              shouldUpdate={(prevValues, curValues) => {
                                const prevFormat =
                                  prevValues?.publicKeys?.[name]?.format;
                                const curFormat =
                                  curValues?.publicKeys?.[name]?.format;
                                return prevFormat !== curFormat;
                              }}
                            >
                              {({ getFieldValue }) => {
                                const format = getFieldValue([
                                  "publicKeys",
                                  name,
                                  "format",
                                ]);
                                return (
                                  <Form.Item
                                    {...restField}
                                    name={[name, "value"]}
                                    label="公钥内容"
                                    rules={[
                                      {
                                        required: true,
                                        message: "请输入公钥内容",
                                      },
                                    ]}
                                  >
                                    <Input.TextArea
                                      rows={6}
                                      placeholder={
                                        format === PublicKeyFormat.JWK
                                          ? 'JWK格式公钥，例如:\n{\n  \"kty\": \"RSA\",\n  \"kid\": \"key1\",\n  \"n\": \"...\",\n  \"e\": \"AQAB\"\n}'
                                          : "PEM格式公钥，例如:\n-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----"
                                      }
                                      style={{
                                        fontFamily: "monospace",
                                        fontSize: "12px",
                                      }}
                                    />
                                  </Form.Item>
                                );
                              }}
                            </Form.Item>
                          </div>
                        ),
                      }))}
                    />
                  )}
                  <Button
                    type="dashed"
                    onClick={() => add()}
                    block
                    icon={<PlusOutlined />}
                    size="small"
                  >
                    添加公钥
                  </Button>
                </div>
              )}
            </Form.List>
          );
        }}
      </Form.Item>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? "rotate-90" : ""}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                clipRule="evenodd"
              />
            </svg>
          )}
          items={[
            {
              key: "advanced",
              label: (
                <div className="flex items-center text-gray-600">
                  <svg
                    className="w-4 h-4"
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path
                      fillRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      clipRule="evenodd"
                    />
                    <path
                      fillRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      clipRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item
                      noStyle
                      shouldUpdate={(prevValues, curValues) =>
                        prevValues?.oauth2JwtValidationMode !==
                        curValues?.oauth2JwtValidationMode
                      }
                    >
                      {({ getFieldValue }) => {
                        const mode =
                          getFieldValue("oauth2JwtValidationMode") ||
                          "PUBLIC_KEYS";
                        const placeholder =
                          mode === "JWKS" ? "默认: sub" : "默认: userId";
                        return (
                          <Form.Item name="userIdField" label="开发者ID">
                            <Input placeholder={placeholder} />
                          </Form.Item>
                        );
                      }}
                    </Form.Item>
                    <Form.Item name="userNameField" label="开发者名称">
                      <Input placeholder="默认: name" />
                    </Form.Item>
                    <Form.Item name="emailField" label="邮箱">
                      <Input placeholder="默认: email" />
                    </Form.Item>
                  </div>

                  <div className="bg-blue-50 p-3 rounded-lg">
                    <div className="flex items-start space-x-2">
                      <div className="text-blue-600 mt-0.5">
                        <svg
                          className="w-4 h-4"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 0 100-2v-3a1 1 0 00-1-1H9z"
                            clipRule="evenodd"
                          />
                        </svg>
                      </div>
                      <div>
                        <h4 className="text-blue-800 font-medium text-sm">
                          配置说明
                        </h4>
                        <p className="text-blue-700 text-xs mt-1">
                          身份映射用于从JWT载荷中提取用户信息。如果不填写，系统将使用默认字段名。
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );

  const renderCasForm = () => (
    <div className="space-y-6">
      <Form.Item
        name="serverUrl"
        label="CAS 服务地址"
        rules={[
          { required: true, message: "请输入CAS服务地址" },
          { type: "url", message: "请输入有效的URL" },
        ]}
      >
        <Input placeholder="如: https://cas.example.com/cas" />
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item name="loginEndpoint" label="登录地址">
          <Input placeholder="可选，默认由服务地址推导 /login" />
        </Form.Item>
        <Form.Item name="validateEndpoint" label="票据校验地址">
          <Input placeholder="可选，默认由服务地址推导 /p3/serviceValidate" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="validationProtocolVersion"
          label="校验协议版本"
          initialValue="CAS3"
        >
          <Select>
            <Select.Option value="CAS1">CAS 1.0</Select.Option>
            <Select.Option value="CAS2">CAS 2.0</Select.Option>
            <Select.Option value="CAS3">CAS 3.0</Select.Option>
            <Select.Option value="SAML1">SAML 1.1</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="validationResponseFormat"
          label="校验响应格式"
          initialValue="XML"
        >
          <Select>
            <Select.Option value="XML">XML</Select.Option>
            <Select.Option value="JSON">JSON</Select.Option>
          </Select>
        </Form.Item>
      </div>

      <Form.Item name="logoutEndpoint" label="登出地址">
        <Input placeholder="可选，默认由服务地址推导 /logout" />
      </Form.Item>

      <Form.Item
        name="sloEnabled"
        label="单点登出"
        valuePropName="checked"
        extra="启用后，前端退出登录将跳转到 CAS 登出地址。"
      >
        <Switch />
      </Form.Item>

      <div className="grid grid-cols-4 gap-4">
        <Form.Item
          name="loginGateway"
          label="Gateway"
          valuePropName="checked"
          extra="启用无交互网关登录。"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="loginRenew"
          label="Renew"
          valuePropName="checked"
          extra="强制重新认证。"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="loginWarn"
          label="Warn"
          valuePropName="checked"
          extra="切换到 CAS 前要求确认。"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="loginRememberMe"
          label="Remember Me"
          valuePropName="checked"
          extra="请求 CAS 记住登录状态。"
        >
          <Switch />
        </Form.Item>
      </div>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? "rotate-90" : ""}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                clipRule="evenodd"
              />
            </svg>
          )}
          items={[
            {
              key: "advanced",
              label: (
                <div className="flex items-center text-gray-600">
                  <svg
                    className="w-4 h-4"
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path
                      fillRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      clipRule="evenodd"
                    />
                    <path
                      fillRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      clipRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item name="userIdField" label="开发者ID">
                      <Input placeholder="默认: user" />
                    </Form.Item>
                    <Form.Item name="userNameField" label="开发者名称">
                      <Input placeholder="默认: user" />
                    </Form.Item>
                    <Form.Item name="emailField" label="邮箱">
                      <Input placeholder="默认: mail" />
                    </Form.Item>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );

  const renderLdapForm = () => (
    <div className="space-y-6">
      <Form.Item
        name="serverUrl"
        label="LDAP 服务地址"
        rules={[
          { required: true, message: "请输入LDAP服务地址" },
          {
            validator: (_, value) => {
              const v = String(value || "").toLowerCase();
              return v.startsWith("ldap://") || v.startsWith("ldaps://")
                ? Promise.resolve()
                : Promise.reject(
                    new Error("请输入以 ldap:// 或 ldaps:// 开头的地址")
                  );
            },
          },
        ]}
      >
        <Input placeholder="如: ldap://ldap.example.com:389 或 ldaps://ldap.example.com:636" />
      </Form.Item>

      <Form.Item
        name="baseDn"
        label="Base DN"
        rules={[{ required: true, message: "请输入 Base DN" }]}
      >
        <Input placeholder="如: dc=example,dc=com" />
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="bindDn"
          label="Bind DN"
          extra="可选，用于查询用户。与 Bind 密码 配对使用。"
        >
          <Input placeholder="如: cn=admin,dc=example,dc=com" />
        </Form.Item>
        <Form.Item
          name="bindPassword"
          label="Bind 密码"
          extra="可选，与 Bind DN 配对使用。"
        >
          <Input.Password placeholder="可选" />
        </Form.Item>
      </div>

      <Form.Item
        name="userSearchFilter"
        label="用户搜索过滤器"
        extra="必须包含 {0} 占位符，例如 (uid={0}) 或 (sAMAccountName={0})."
        rules={[
          { required: true, message: "请输入用户搜索过滤器" },
          {
            validator: (_, value) =>
              value && String(value).includes("{0}")
                ? Promise.resolve()
                : Promise.reject(new Error("过滤器必须包含 {0} 占位符")),
          },
        ]}
      >
        <Input placeholder="默认: (uid={0})" />
      </Form.Item>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? "rotate-90" : ""}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                clipRule="evenodd"
              />
            </svg>
          )}
          items={[
            {
              key: "advanced",
              label: (
                <div className="flex items-center text-gray-600">
                  <svg
                    className="w-4 h-4"
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path
                      fillRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      clipRule="evenodd"
                    />
                    <path
                      fillRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      clipRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item name="userIdField" label="开发者ID">
                      <Input placeholder="默认: uid" />
                    </Form.Item>
                    <Form.Item name="userNameField" label="开发者名称">
                      <Input placeholder="默认: cn" />
                    </Form.Item>
                    <Form.Item name="emailField" label="邮箱">
                      <Input placeholder="默认: mail" />
                    </Form.Item>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );

  // 按类型分组配置
  const oidcConfigs = configs.filter(
    config => config.type === AuthenticationType.OIDC
  );
  const casConfigs = configs.filter(
    config => config.type === AuthenticationType.CAS
  );
  const ldapConfigs = configs.filter(
    config => config.type === AuthenticationType.LDAP
  );
  const oauth2Configs = configs.filter(
    config => config.type === AuthenticationType.OAUTH2
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-medium">第三方认证</h3>
          <p className="text-sm text-gray-500">管理外部身份认证配置</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          添加配置
        </Button>
      </div>

      <Tabs
        defaultActiveKey="oidc"
        items={[
          {
            key: "oidc",
            label: "OIDC配置",
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">
                    OIDC配置
                  </h4>
                  <p className="text-sm text-gray-500 mt-1">
                    支持OpenID Connect标准协议的身份提供商
                  </p>
                </div>
                <Table
                  columns={oidcColumns}
                  dataSource={oidcConfigs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: "暂无OIDC配置",
                  }}
                />
              </div>
            ),
          },
          {
            key: "cas",
            label: "CAS配置",
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">CAS配置</h4>
                  <p className="text-sm text-gray-500 mt-1">
                    支持兼容 CAS 协议的单点登录
                  </p>
                </div>
                <Table
                  columns={casColumns}
                  dataSource={casConfigs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: "暂无CAS配置",
                  }}
                />
              </div>
            ),
          },
          {
            key: "ldap",
            label: "LDAP配置",
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">
                    LDAP配置
                  </h4>
                  <p className="text-sm text-gray-500 mt-1">
                    支持基于 LDAP 的账号密码登录
                  </p>
                </div>
                <Table
                  columns={ldapColumns}
                  dataSource={ldapConfigs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: "暂无LDAP配置",
                  }}
                />
              </div>
            ),
          },
          {
            key: "oauth2",
            label: "OAuth2配置",
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">
                    OAuth2配置
                  </h4>
                  <p className="text-sm text-gray-500 mt-1">
                    支持OAuth 2.0标准协议的身份提供商
                  </p>
                </div>
                <Table
                  columns={oauth2Columns}
                  dataSource={oauth2Configs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: "暂无OAuth2配置",
                  }}
                />
              </div>
            ),
          },
        ]}
      />

      {/* 添加/编辑配置模态框 */}
      <Modal
        title={editingConfig ? "编辑第三方认证配置" : "添加第三方认证配置"}
        open={modalVisible}
        onCancel={handleCancel}
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
            // 第一步：选择类型
            <Card>
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

              <div className="flex justify-end">
                <Button type="primary" onClick={handleNext}>
                  下一步
                </Button>
              </div>
            </Card>
          ) : (
            // 第二步：配置详情
            <div>
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="provider"
                  label="提供商标识"
                  rules={[
                    { required: true, message: "请输入提供商标识" },
                    {
                      validator: (_, value) => {
                        if (!value) return Promise.resolve();

                        // 检查provider唯一性
                        const isDuplicate = configs.some(
                          config =>
                            config.provider === value &&
                            (!editingConfig || editingConfig.provider !== value)
                        );

                        if (isDuplicate) {
                          return Promise.reject(
                            new Error("该提供商标识已存在，请使用不同的标识")
                          );
                        }

                        return Promise.resolve();
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

              {/* 根据类型显示不同的配置表单 */}
              {selectedType === AuthenticationType.OIDC
                ? renderOidcForm()
                : selectedType === AuthenticationType.CAS
                  ? renderCasForm()
                  : selectedType === AuthenticationType.LDAP
                    ? renderLdapForm()
                    : renderOAuth2Form()}

              <div className="flex justify-between mt-6">
                <Button onClick={handlePrevious}>上一步</Button>
                <Space>
                  <Button onClick={handleCancel}>取消</Button>
                  <Button type="primary" loading={loading} onClick={handleSave}>
                    {editingConfig ? "更新" : "添加"}
                  </Button>
                </Space>
              </div>
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
}
