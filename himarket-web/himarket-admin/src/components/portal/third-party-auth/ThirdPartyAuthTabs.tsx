import { Tabs, type TableProps } from "antd";
import { ThirdPartyAuthConfig } from "@/types";
import { AuthConfigTableCard } from "./AuthConfigTableCard";

interface ThirdPartyAuthTabsProps {
  oidcColumns: NonNullable<TableProps<ThirdPartyAuthConfig>["columns"]>;
  casColumns: NonNullable<TableProps<ThirdPartyAuthConfig>["columns"]>;
  ldapColumns: NonNullable<TableProps<ThirdPartyAuthConfig>["columns"]>;
  oauth2Columns: NonNullable<TableProps<ThirdPartyAuthConfig>["columns"]>;
  oidcConfigs: ThirdPartyAuthConfig[];
  casConfigs: ThirdPartyAuthConfig[];
  ldapConfigs: ThirdPartyAuthConfig[];
  oauth2Configs: ThirdPartyAuthConfig[];
}

export function ThirdPartyAuthTabs({
  oidcColumns,
  casColumns,
  ldapColumns,
  oauth2Columns,
  oidcConfigs,
  casConfigs,
  ldapConfigs,
  oauth2Configs,
}: ThirdPartyAuthTabsProps) {
  return (
    <Tabs
      defaultActiveKey="oidc"
      items={[
        {
          key: "oidc",
          label: "OIDC配置",
          children: (
            <AuthConfigTableCard
              title="OIDC配置"
              description="支持OpenID Connect标准协议的身份提供商"
              emptyText="暂无OIDC配置"
              columns={oidcColumns}
              dataSource={oidcConfigs}
            />
          ),
        },
        {
          key: "cas",
          label: "CAS配置",
          children: (
            <AuthConfigTableCard
              title="CAS配置"
              description="支持兼容 CAS 协议的单点登录"
              emptyText="暂无CAS配置"
              columns={casColumns}
              dataSource={casConfigs}
            />
          ),
        },
        {
          key: "ldap",
          label: "LDAP配置",
          children: (
            <AuthConfigTableCard
              title="LDAP配置"
              description="支持基于 LDAP 的账号密码登录"
              emptyText="暂无LDAP配置"
              columns={ldapColumns}
              dataSource={ldapConfigs}
            />
          ),
        },
        {
          key: "oauth2",
          label: "OAuth2配置",
          children: (
            <AuthConfigTableCard
              title="OAuth2配置"
              description="支持OAuth 2.0标准协议的身份提供商"
              emptyText="暂无OAuth2配置"
              columns={oauth2Columns}
              dataSource={oauth2Configs}
            />
          ),
        },
      ]}
    />
  );
}
