import { Form, Input, Select } from "antd";

export function CasAttributeReleaseSection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item name="attributeReleaseMode" label="Attribute Release Mode">
        <Select>
          <Select.Option value="RETURN_ALLOWED">RETURN_ALLOWED</Select.Option>
          <Select.Option value="RETURN_ALL">RETURN_ALL</Select.Option>
          <Select.Option value="DENY_ALL">DENY_ALL</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        name="attributeReleaseAllowedAttributes"
        label="Allowed Attributes"
        extra="仅在 RETURN_ALLOWED 下生效；为空时回落到身份映射字段。"
      >
        <Input placeholder="如: uid, mail, displayName" />
      </Form.Item>
    </div>
  );
}
