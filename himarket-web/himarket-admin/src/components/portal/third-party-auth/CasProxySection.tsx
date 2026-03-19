import { Form, Input, Select, Switch, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasProxySection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="proxyEnabled"
        label={
          <span>
            启用代理认证 (Proxy)&nbsp;
            <Tooltip title="仅当 HiMarket 需要代表用户去访问其它受 CAS 保护的后端服务时开启。开启后会申请 PGT。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
        valuePropName="checked"
        extra="开启后，系统将尝试获取代理票据（Proxy Ticket）。"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="proxyEndpoint"
        label="Proxy 申请端点"
        extra="可选，默认推导为 [服务地址]/proxy。用于获取 PT 票据。"
      >
        <Input placeholder="如: https://cas.example.com/cas/proxy" />
      </Form.Item>
      <Form.Item
        name="proxyCallbackPath"
        label="代理回调路径"
        extra="后端用来接收 PGT 的接口路径。例如 /admins/cas/proxy-callback"
      >
        <Input placeholder="如: /admins/cas/proxy-callback" />
      </Form.Item>
      <Form.Item
        name="proxyCallbackUrlPattern"
        label="代理回调匹配模式"
        extra="导出 JSON 定义时使用，用于在 CAS 端配置回调白名单。"
      >
        <Input placeholder="如: ^https://api.example.com/.*/proxy-callback$" />
      </Form.Item>
      <Form.Item
        name="proxyTargetServicePattern"
        label="目标服务匹配模式"
        extra="限制当前代理票据允许访问的下游服务 URL 正则。"
      >
        <Input placeholder="如: ^https://api.example.com/.*$" />
      </Form.Item>
      <Form.Item name="proxyPolicyMode" label="代理策略模式">
        <Select>
          <Select.Option value="REGEX">正则表达式 (REGEX)</Select.Option>
          <Select.Option value="REST">远程 REST 接口 (REST)</Select.Option>
          <Select.Option value="REFUSE">拒绝所有代理 (REFUSE)</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        name="proxyUseServiceId"
        label="使用 Service ID"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="proxyExactMatch"
        label="精确匹配 URL"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="proxyPolicyEndpoint"
        label="策略校验接口"
        extra="仅在 REST 模式下有效。"
      >
        <Input placeholder="如: https://proxy.example.com/policies" />
      </Form.Item>
      <Form.Item
        name="proxyPolicyHeaders"
        label="策略校验请求头"
        extra='JSON 格式，例如 {"X-Proxy-Policy":"enabled"}'
      >
        <Input.TextArea
          rows={4}
          placeholder='如: {"X-Proxy-Policy":"enabled"}'
        />
      </Form.Item>
    </div>
  );
}
