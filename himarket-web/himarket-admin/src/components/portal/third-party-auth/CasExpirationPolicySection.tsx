import { Form, Input, Switch } from "antd";

export function CasExpirationPolicySection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="expirationPolicyExpirationDate"
        label="Expiration Date"
      >
        <Input placeholder="如: 2030-12-31T23:59:59Z" />
      </Form.Item>
      <Form.Item
        name="expirationPolicyDeleteWhenExpired"
        label="Delete When Expired"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="expirationPolicyNotifyWhenExpired"
        label="Notify When Expired"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
      <Form.Item
        name="expirationPolicyNotifyWhenDeleted"
        label="Notify When Deleted"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>
    </div>
  );
}
