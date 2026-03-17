import { Form, Input, Select, Switch } from "antd";

export function CasAuthenticationPolicySection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="authenticationPolicyCriteriaMode"
        label="Authentication Policy Criteria"
      >
        <Select>
          <Select.Option value="ALLOWED">ALLOWED</Select.Option>
          <Select.Option value="EXCLUDED">EXCLUDED</Select.Option>
          <Select.Option value="ANY">ANY</Select.Option>
          <Select.Option value="ALL">ALL</Select.Option>
          <Select.Option value="NOT_PREVENTED">NOT_PREVENTED</Select.Option>
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
    </div>
  );
}
