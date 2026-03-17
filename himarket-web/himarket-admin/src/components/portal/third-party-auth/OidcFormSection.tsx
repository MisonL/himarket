import { Divider, Form, Input, Radio, Select } from "antd";
import { OidcIdentityMappingSection } from "./OidcIdentityMappingSection";

export function OidcFormSection() {
  return (
    <div className="space-y-6">
      <Form.Item
        name="oidcGrantType"
        label="授权模式"
        initialValue="AUTHORIZATION_CODE"
      >
        <Select disabled>
          <Select.Option value="AUTHORIZATION_CODE">授权码模式</Select.Option>
        </Select>
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="clientId"
          label="Client ID"
          rules={[{ required: true, message: "请输入 Client ID" }]}
        >
          <Input placeholder="Client ID" />
        </Form.Item>
        <Form.Item
          name="clientSecret"
          label="Client Secret"
          rules={[{ required: true, message: "请输入 Client Secret" }]}
        >
          <Input.Password placeholder="Client Secret" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="scopes"
          label="授权范围"
          rules={[{ required: true, message: "请输入授权范围" }]}
        >
          <Input placeholder="如: openid profile email" />
        </Form.Item>
        <div></div>
      </div>

      <Divider />

      <Form.Item name="configMode" label="端点配置" initialValue="auto">
        <Radio.Group>
          <Radio value="auto">自动发现</Radio>
          <Radio value="manual">手动配置</Radio>
        </Radio.Group>
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) =>
          prevValues.configMode !== curValues.configMode
        }
      >
        {({ getFieldValue }) => {
          const configMode = getFieldValue("configMode") || "auto";

          if (configMode === "auto") {
            return (
              <Form.Item
                name="issuer"
                label="Issuer"
                rules={[
                  { required: true, message: "请输入Issuer地址" },
                  { type: "url", message: "请输入有效的URL" },
                ]}
              >
                <Input placeholder="如: https://accounts.google.com" />
              </Form.Item>
            );
          }

          return (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="authorizationEndpoint"
                  label="授权端点"
                  rules={[{ required: true, message: "请输入授权端点" }]}
                >
                  <Input placeholder="Authorization 授权端点" />
                </Form.Item>
                <Form.Item
                  name="tokenEndpoint"
                  label="令牌端点"
                  rules={[{ required: true, message: "请输入令牌端点" }]}
                >
                  <Input placeholder="Token 令牌端点" />
                </Form.Item>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="userInfoEndpoint"
                  label="用户信息端点"
                  rules={[{ required: true, message: "请输入用户信息端点" }]}
                >
                  <Input placeholder="UserInfo 端点" />
                </Form.Item>
                <Form.Item name="jwkSetUri" label="公钥端点">
                  <Input placeholder="可选" />
                </Form.Item>
              </div>
            </div>
          );
        }}
      </Form.Item>

      <OidcIdentityMappingSection />
    </div>
  );
}
