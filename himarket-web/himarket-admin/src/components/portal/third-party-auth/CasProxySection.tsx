import { Form, Input, Select, Switch } from "antd";

export function CasProxySection() {
  return (
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
  );
}
