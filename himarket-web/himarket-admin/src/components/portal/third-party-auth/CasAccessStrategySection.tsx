import { Form, Input, Switch } from "antd";

export function CasAccessStrategySection() {
  return (
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
      <Form.Item name="accessStrategyZoneId" label="Access Strategy ZoneId">
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
        <Input.TextArea rows={4} placeholder='如: {"status":["disabled"]}' />
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
      <Form.Item name="httpRequestIpAddressPattern" label="IP Address Regex">
        <Input placeholder="如: ^127\\.0\\.0\\.1$" />
      </Form.Item>
      <Form.Item name="httpRequestUserAgentPattern" label="User-Agent Regex">
        <Input placeholder="如: ^curl/.*$" />
      </Form.Item>
      <Form.Item
        name="httpRequestHeaders"
        label="Required Headers"
        extra='JSON 对象，例如 {"X-Portal-Scope":"admin"}'
      >
        <Input.TextArea rows={4} placeholder='如: {"X-Portal-Scope":"admin"}' />
      </Form.Item>
    </div>
  );
}
