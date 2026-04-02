import {
  CheckCircleFilled,
  DeleteOutlined,
  EditOutlined,
  MinusCircleFilled,
} from "@ant-design/icons";
import { Button, Space } from "antd";
import {
  AuthenticationType,
  CasConfig,
  GrantType,
  OAuth2Config,
  ThirdPartyAuthConfig,
} from "@/types";

interface AuthColumnActions {
  onEdit: (config: ThirdPartyAuthConfig) => void;
  onDelete: (provider: string, name: string) => void;
  onPreviewCasServiceDefinition?: (provider: string) => void;
}

function renderProvider(provider: string) {
  return <span className="font-medium text-gray-700">{provider}</span>;
}

function renderStatus(enabled: boolean) {
  return (
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
  );
}

function renderActions(
  record: ThirdPartyAuthConfig,
  actions: AuthColumnActions
) {
  return (
    <Space>
      {actions.onPreviewCasServiceDefinition ? (
        <Button
          type="link"
          onClick={() =>
            actions.onPreviewCasServiceDefinition?.(record.provider)
          }
        >
          预览定义
        </Button>
      ) : null}
      <Button
        type="link"
        icon={<EditOutlined />}
        onClick={() => actions.onEdit(record)}
      >
        编辑
      </Button>
      <Button
        type="link"
        danger
        icon={<DeleteOutlined />}
        onClick={() => actions.onDelete(record.provider, record.name)}
      >
        删除
      </Button>
    </Space>
  );
}

function renderCasProtocol(record: ThirdPartyAuthConfig) {
  if (record.type !== AuthenticationType.CAS) {
    return <span className="text-gray-600">-</span>;
  }

  const casConfig = record as CasConfig & { type: AuthenticationType.CAS };
  if (casConfig.serviceDefinition?.responseType === "HEADER") {
    return <span className="text-gray-600">Header</span>;
  }

  const protocolVersion = casConfig.validation?.protocolVersion || "CAS3";
  const responseFormat = casConfig.validation?.responseFormat;
  return (
    <span className="text-gray-600">
      {responseFormat
        ? `${protocolVersion} / ${responseFormat}`
        : protocolVersion}
    </span>
  );
}

export function createOidcColumns(actions: AuthColumnActions) {
  return [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: renderProvider,
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
      render: renderStatus,
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) =>
        renderActions(record, actions),
    },
  ];
}

export function createOAuth2Columns(actions: AuthColumnActions) {
  return [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: renderProvider,
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
              {oauth2Config.grantType === GrantType.TRUSTED_HEADER
                ? "Trusted Header"
                : oauth2Config.grantType === GrantType.JWT_BEARER
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
          return (
            <span className="text-gray-600">
              {oauth2Config.grantType === GrantType.TRUSTED_HEADER
                ? "无需验签"
                : "-"}
            </span>
          );
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
      render: renderStatus,
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) =>
        renderActions(record, actions),
    },
  ];
}

export function createCasColumns(actions: AuthColumnActions) {
  return [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: renderProvider,
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
      render: (record: ThirdPartyAuthConfig) => renderCasProtocol(record),
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: renderStatus,
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      render: (_: unknown, record: ThirdPartyAuthConfig) =>
        renderActions(record, actions),
    },
  ];
}

export function createLdapColumns(actions: AuthColumnActions) {
  return [
    {
      title: "提供商",
      dataIndex: "provider",
      key: "provider",
      width: 120,
      render: renderProvider,
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
      render: renderStatus,
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: ThirdPartyAuthConfig) =>
        renderActions(record, actions),
    },
  ];
}
