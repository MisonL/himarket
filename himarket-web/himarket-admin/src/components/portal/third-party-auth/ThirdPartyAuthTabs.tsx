import { Badge, Tabs, type TableProps } from "antd";
import { useEffect, useMemo, useState } from "react";
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
  const preferredKey = useMemo(
    () =>
      [
        { key: "oidc", count: oidcConfigs.length },
        { key: "cas", count: casConfigs.length },
        { key: "ldap", count: ldapConfigs.length },
        { key: "oauth2", count: oauth2Configs.length },
      ].find(item => item.count > 0)?.key || "oidc",
    [
      oidcConfigs.length,
      casConfigs.length,
      ldapConfigs.length,
      oauth2Configs.length,
    ]
  );

  const items = useMemo(
    () => [
      {
        key: "oidc",
        label: (
          <span>
            OIDC配置 <Badge count={oidcConfigs.length} size="small" />
          </span>
        ),
        configCount: oidcConfigs.length,
        children: (
          <AuthConfigTableCard
            title="OIDC配置"
            description="支持 OpenID Connect 标准协议的身份提供商。"
            emptyText="暂无OIDC配置"
            columns={oidcColumns}
            dataSource={oidcConfigs}
          />
        ),
      },
      {
        key: "cas",
        label: (
          <span>
            CAS配置 <Badge count={casConfigs.length} size="small" />
          </span>
        ),
        configCount: casConfigs.length,
        children: (
          <AuthConfigTableCard
            title="CAS配置"
            description="支持兼容 CAS 协议的单点登录，包括标准 Ticket、SAML1、Header 等模式。"
            emptyText="暂无CAS配置"
            columns={casColumns}
            dataSource={casConfigs}
          />
        ),
      },
      {
        key: "ldap",
        label: (
          <span>
            LDAP配置 <Badge count={ldapConfigs.length} size="small" />
          </span>
        ),
        configCount: ldapConfigs.length,
        children: (
          <AuthConfigTableCard
            title="LDAP配置"
            description="支持基于 LDAP 的账号密码登录。"
            emptyText="暂无LDAP配置"
            columns={ldapColumns}
            dataSource={ldapConfigs}
          />
        ),
      },
      {
        key: "oauth2",
        label: (
          <span>
            OAuth2配置 <Badge count={oauth2Configs.length} size="small" />
          </span>
        ),
        configCount: oauth2Configs.length,
        children: (
          <AuthConfigTableCard
            title="OAuth2配置"
            description="支持 JWT Direct、Ticket Exchange、Trusted Header 等企业 SSO 接入。"
            emptyText="暂无OAuth2配置"
            columns={oauth2Columns}
            dataSource={oauth2Configs}
          />
        ),
      },
    ],
    [
      casColumns,
      casConfigs,
      ldapColumns,
      ldapConfigs,
      oauth2Columns,
      oauth2Configs,
      oidcColumns,
      oidcConfigs,
    ]
  );
  const [activeKey, setActiveKey] = useState(preferredKey);
  const [hasUserSelectedTab, setHasUserSelectedTab] = useState(false);

  useEffect(() => {
    if (!hasUserSelectedTab) {
      setActiveKey(preferredKey);
    }
  }, [hasUserSelectedTab, preferredKey]);

  const handleChange = (nextActiveKey: string) => {
    setHasUserSelectedTab(true);
    setActiveKey(nextActiveKey);
  };

  return <Tabs activeKey={activeKey} onChange={handleChange} items={items} />;
}
