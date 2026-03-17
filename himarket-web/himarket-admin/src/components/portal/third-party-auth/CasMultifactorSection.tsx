import { Form, Input, Select, Switch } from "antd";

export function CasMultifactorSection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="multifactorProviders"
        label="MFA Providers"
        extra="逗号分隔，例如 mfa-duo,mfa-webauthn。"
      >
        <Input placeholder="如: mfa-duo, mfa-webauthn" />
      </Form.Item>
      <Form.Item name="multifactorFailureMode" label="MFA Failure Mode">
        <Select>
          <Select.Option value="UNDEFINED">UNDEFINED</Select.Option>
          <Select.Option value="OPEN">OPEN</Select.Option>
          <Select.Option value="CLOSED">CLOSED</Select.Option>
          <Select.Option value="PHANTOM">PHANTOM</Select.Option>
          <Select.Option value="NONE">NONE</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        name="multifactorBypassEnabled"
        label="MFA Bypass"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="multifactorBypassPrincipalAttributeName"
        label="Bypass Principal Attribute"
      >
        <Input placeholder="如: memberOf" />
      </Form.Item>
      <Form.Item
        name="multifactorBypassPrincipalAttributeValue"
        label="Bypass Attribute Value"
      >
        <Input placeholder="如: internal" />
      </Form.Item>
      <Form.Item
        name="multifactorBypassIfMissingPrincipalAttribute"
        label="Bypass If Principal Attribute Missing"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="multifactorForceExecution"
        label="强制执行 MFA"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
    </div>
  );
}
