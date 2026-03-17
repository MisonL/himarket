import { Form, Select } from "antd";

export function CasValidationSection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="validationProtocolVersion"
        label="校验协议版本"
        initialValue="CAS3"
      >
        <Select>
          <Select.Option value="CAS1">CAS 1.0</Select.Option>
          <Select.Option value="CAS2">CAS 2.0</Select.Option>
          <Select.Option value="CAS3">CAS 3.0</Select.Option>
          <Select.Option value="SAML1">SAML 1.1</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        name="validationResponseFormat"
        label="校验响应格式"
        initialValue="XML"
      >
        <Select>
          <Select.Option value="XML">XML</Select.Option>
          <Select.Option value="JSON">JSON</Select.Option>
        </Select>
      </Form.Item>
    </div>
  );
}
