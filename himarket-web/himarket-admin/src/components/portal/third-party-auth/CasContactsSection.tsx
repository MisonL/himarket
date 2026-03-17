import { Form, Input } from "antd";

export function CasContactsSection() {
  return (
    <Form.Item
      name="serviceContacts"
      label="Service Contacts"
      extra='JSON 数组，例如 [{"name":"Portal SRE","email":"sre@example.com","type":"TECHNICAL"}]'
    >
      <Input.TextArea
        rows={5}
        placeholder='如: [{"name":"Portal SRE","email":"sre@example.com","type":"TECHNICAL"}]'
      />
    </Form.Item>
  );
}
