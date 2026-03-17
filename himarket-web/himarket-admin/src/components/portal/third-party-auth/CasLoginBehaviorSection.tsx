import { Form, Switch } from "antd";

export function CasLoginBehaviorSection() {
  return (
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
  );
}
