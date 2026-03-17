import { Form, Input, Select } from "antd";

export function CasServiceDefinitionSection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="serviceDefinitionServiceIdPattern"
        label="Service ID Pattern"
      >
        <Input placeholder="可选，默认自动生成 callback 正则" />
      </Form.Item>
      <Form.Item name="serviceDefinitionServiceId" label="Service ID">
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
        extra="HEADER 适用于 CAS-aware API client；当前浏览器登录页不会把它作为交互式登录按钮展示。"
      >
        <Select>
          <Select.Option value="REDIRECT">REDIRECT</Select.Option>
          <Select.Option value="POST">POST</Select.Option>
          <Select.Option value="HEADER">HEADER</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item name="serviceDefinitionLogoutType" label="Logout Type">
        <Select allowClear placeholder="默认按 SLO 配置推导">
          <Select.Option value="NONE">NONE</Select.Option>
          <Select.Option value="BACK_CHANNEL">BACK_CHANNEL</Select.Option>
          <Select.Option value="FRONT_CHANNEL">FRONT_CHANNEL</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item name="serviceDefinitionLogoutUrl" label="Logout URL">
        <Input placeholder="可选，覆盖默认登出跳转地址" />
      </Form.Item>
    </div>
  );
}
