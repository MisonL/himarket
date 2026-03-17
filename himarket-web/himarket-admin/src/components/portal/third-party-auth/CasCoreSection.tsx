import { Form, Input, Switch } from "antd";

export function CasCoreSection() {
  return (
    <>
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
    </>
  );
}
