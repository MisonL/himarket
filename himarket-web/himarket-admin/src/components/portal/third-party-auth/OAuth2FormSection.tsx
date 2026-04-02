import {
  KeyOutlined,
  MinusCircleOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import { Button, Collapse, Divider, Form, Input, Radio, Select } from "antd";
import { GrantType, PublicKeyFormat } from "@/types";

function renderPublicKeyList() {
  return (
    <Form.List name="publicKeys">
      {(fields, { add, remove }) => (
        <div className="space-y-4">
          {fields.length > 0 && (
            <Collapse
              size="small"
              items={fields.map(({ key, name, ...restField }) => ({
                key,
                label: (
                  <div className="flex items-center">
                    <KeyOutlined className="mr-2" />
                    <span>公钥 {name + 1}</span>
                  </div>
                ),
                extra: (
                  <Button
                    type="link"
                    danger
                    size="small"
                    icon={<MinusCircleOutlined />}
                    onClick={event => {
                      event.stopPropagation();
                      remove(name);
                    }}
                  >
                    删除
                  </Button>
                ),
                children: (
                  <div className="space-y-4 px-4">
                    <div className="grid grid-cols-3 gap-4">
                      <Form.Item
                        {...restField}
                        name={[name, "kid"]}
                        label="Key ID"
                        rules={[{ required: true, message: "请输入Key ID" }]}
                      >
                        <Input placeholder="公钥标识符" size="small" />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, "algorithm"]}
                        label="签名算法"
                        rules={[{ required: true, message: "请选择签名算法" }]}
                      >
                        <Select placeholder="选择签名算法" size="small">
                          <Select.Option value="RS256">RS256</Select.Option>
                          <Select.Option value="RS384">RS384</Select.Option>
                          <Select.Option value="RS512">RS512</Select.Option>
                          <Select.Option value="ES256">ES256</Select.Option>
                          <Select.Option value="ES384">ES384</Select.Option>
                          <Select.Option value="ES512">ES512</Select.Option>
                        </Select>
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, "format"]}
                        label="公钥格式"
                        rules={[{ required: true, message: "请选择公钥格式" }]}
                      >
                        <Select placeholder="选择公钥格式" size="small">
                          <Select.Option value={PublicKeyFormat.PEM}>
                            PEM
                          </Select.Option>
                          <Select.Option value={PublicKeyFormat.JWK}>
                            JWK
                          </Select.Option>
                        </Select>
                      </Form.Item>
                    </div>

                    <Form.Item
                      noStyle
                      shouldUpdate={(prevValues, curValues) => {
                        const prevFormat = prevValues?.publicKeys?.[name]?.format;
                        const curFormat = curValues?.publicKeys?.[name]?.format;
                        return prevFormat !== curFormat;
                      }}
                    >
                      {({ getFieldValue }) => {
                        const format = getFieldValue(["publicKeys", name, "format"]);
                        return (
                          <Form.Item
                            {...restField}
                            name={[name, "value"]}
                            label="公钥内容"
                            rules={[{ required: true, message: "请输入公钥内容" }]}
                          >
                            <Input.TextArea
                              rows={6}
                              placeholder={
                                format === PublicKeyFormat.JWK
                                  ? 'JWK格式公钥，例如:\n{\n  "kty": "RSA",\n  "kid": "key1",\n  "n": "...",\n  "e": "AQAB"\n}'
                                  : "PEM格式公钥，例如:\n-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----"
                              }
                              style={{
                                fontFamily: "monospace",
                                fontSize: "12px",
                              }}
                            />
                          </Form.Item>
                        );
                      }}
                    </Form.Item>
                  </div>
                ),
              }))}
            />
          )}
          <Button
            type="dashed"
            onClick={() => add()}
            block
            icon={<PlusOutlined />}
            size="small"
          >
            添加公钥
          </Button>
        </div>
      )}
    </Form.List>
  );
}

function renderIdentityMappingSection() {
  return (
    <>
      <Divider orientation="left" className="!my-2">
        本地身份字段映射
      </Divider>
      <div className="grid grid-cols-3 gap-4">
        <Form.Item name="userIdField" label="用户 ID 字段">
          <Input placeholder="默认 sub" />
        </Form.Item>
        <Form.Item name="userNameField" label="用户名字段">
          <Input placeholder="默认 name" />
        </Form.Item>
        <Form.Item name="emailField" label="邮箱字段">
          <Input placeholder="默认 email" />
        </Form.Item>
      </div>
    </>
  );
}

function renderTrustedHeaderSection() {
  return (
    <div className="space-y-4">
      <Form.Item name="trustedProxyCidrs" label="可信代理 CIDR">
        <Select
          mode="tags"
          placeholder="如: 127.0.0.1/32, 172.16.0.0/16"
          tokenSeparators={[",", " "]}
        />
      </Form.Item>
      <Form.Item name="trustedProxyHosts" label="可信代理主机">
        <Select
          mode="tags"
          placeholder="如: ingress.internal, 127.0.0.1"
          tokenSeparators={[",", " "]}
        />
      </Form.Item>
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="trustedUserIdHeader"
          label="用户 ID Header"
          rules={[{ required: true, message: "请输入用户 ID Header" }]}
        >
          <Input placeholder="X-Forwarded-User" />
        </Form.Item>
        <Form.Item name="trustedUserNameHeader" label="用户名 Header">
          <Input placeholder="X-Forwarded-Name" />
        </Form.Item>
        <Form.Item name="trustedEmailHeader" label="邮箱 Header">
          <Input placeholder="X-Forwarded-Email" />
        </Form.Item>
        <Form.Item name="trustedValueSeparator" label="多值分隔符">
          <Input placeholder="," />
        </Form.Item>
        <Form.Item name="trustedGroupsHeader" label="组 Header">
          <Input placeholder="X-Forwarded-Groups" />
        </Form.Item>
        <Form.Item name="trustedRolesHeader" label="角色 Header">
          <Input placeholder="X-Forwarded-Roles" />
        </Form.Item>
      </div>
      {renderIdentityMappingSection()}
    </div>
  );
}

function renderJwtDirectSection(
  validationMode: string,
  acquireMode: string,
  identitySource: string
) {
  return (
    <div className="space-y-4">
      <Form.Item
        name="oauth2JwtValidationMode"
        label="验签方式"
        initialValue="PUBLIC_KEYS"
        rules={[{ required: true, message: "请选择验签方式" }]}
      >
        <Radio.Group>
          <Radio value="PUBLIC_KEYS">手工公钥</Radio>
          <Radio value="JWKS">JWKS</Radio>
        </Radio.Group>
      </Form.Item>

      {validationMode === "JWKS" ? (
        <div className="space-y-4">
          <Form.Item
            name="oauth2Issuer"
            label="Issuer"
            rules={[
              { required: true, message: "请输入Issuer" },
              { type: "url", message: "请输入有效的URL" },
            ]}
          >
            <Input placeholder="如: https://cas.example.com/cas/oauth2.0" />
          </Form.Item>
          <Form.Item
            name="oauth2JwkSetUri"
            label="JWKS 地址"
            rules={[
              { required: true, message: "请输入JWKS地址" },
              { type: "url", message: "请输入有效的URL" },
            ]}
          >
            <Input placeholder="如: https://cas.example.com/cas/oauth2.0/jwks" />
          </Form.Item>
          <Form.Item
            name="oauth2Audiences"
            label="Audiences"
            rules={[
              { required: true, message: "请输入至少一个Audience" },
              {
                validator: (_, value) => {
                  if (!value || value.length === 0) {
                    return Promise.reject(new Error("请输入至少一个Audience"));
                  }
                  const invalid = value.some((entry: string) => !entry || !entry.trim());
                  return invalid
                    ? Promise.reject(new Error("Audience 不能为空"))
                    : Promise.resolve();
                },
              },
            ]}
          >
            <Select
              mode="tags"
              placeholder="输入一个或多个audience"
              tokenSeparators={[",", " "]}
            />
          </Form.Item>
        </div>
      ) : (
        renderPublicKeyList()
      )}

      <Divider orientation="left" className="!my-2">
        JWT Direct 浏览器接入
      </Divider>
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="oauth2AuthorizationEndpoint"
          label="授权入口"
          rules={[{ type: "url", message: "请输入有效的URL" }]}
        >
          <Input placeholder="如: https://cas.example.com/login" />
        </Form.Item>
        <Form.Item name="oauth2AuthorizationServiceField" label="回调参数名">
          <Input placeholder="service" />
        </Form.Item>
        <Form.Item name="oauth2AcquireMode" label="票据获取模式">
          <Select>
            <Select.Option value="DIRECT">直接携带 JWT</Select.Option>
            <Select.Option value="TICKET_EXCHANGE">
              ticket_exchange
            </Select.Option>
            <Select.Option value="TICKET_VALIDATE">
              ticket_validate
            </Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="oauth2TokenSource" label="JWT 返回位置">
          <Select>
            <Select.Option value="QUERY">Query</Select.Option>
            <Select.Option value="FRAGMENT">Fragment</Select.Option>
            <Select.Option value="BODY">Body</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="oauth2IdentitySource" label="身份来源">
          <Select>
            <Select.Option value="CLAIMS">Claims</Select.Option>
            <Select.Option value="USERINFO">UserInfo</Select.Option>
          </Select>
        </Form.Item>
      </div>

      {(acquireMode === "TICKET_EXCHANGE" ||
        acquireMode === "TICKET_VALIDATE") && (
        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            name="oauth2TicketExchangeUrl"
            label="票据交换/校验地址"
            rules={[
              { required: true, message: "请输入票据交换/校验地址" },
              { type: "url", message: "请输入有效的URL" },
            ]}
          >
            <Input placeholder="如: https://cas.example.com/p3/serviceValidate" />
          </Form.Item>
          <Form.Item name="oauth2TicketExchangeMethod" label="请求方法">
            <Select>
              <Select.Option value="GET">GET</Select.Option>
              <Select.Option value="POST">POST</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="oauth2TicketExchangeTicketField" label="Ticket 参数名">
            <Input placeholder="ticket" />
          </Form.Item>
          <Form.Item name="oauth2TicketExchangeServiceField" label="Service 参数名">
            <Input placeholder="service" />
          </Form.Item>
          {acquireMode === "TICKET_EXCHANGE" && (
            <Form.Item name="oauth2TicketExchangeTokenField" label="JWT 返回字段">
              <Input placeholder="access_token" />
            </Form.Item>
          )}
        </div>
      )}

      {identitySource === "USERINFO" && (
        <Form.Item
          name="oauth2UserInfoEndpoint"
          label="UserInfo 地址"
          rules={[
            { required: true, message: "请输入UserInfo地址" },
            { type: "url", message: "请输入有效的URL" },
          ]}
        >
          <Input placeholder="如: https://cas.example.com/userinfo" />
        </Form.Item>
      )}

      {renderIdentityMappingSection()}
    </div>
  );
}

export function OAuth2FormSection() {
  return (
    <div className="space-y-6">
      <Form.Item
        name="oauth2GrantType"
        label="授权模式"
        initialValue={GrantType.JWT_BEARER}
        rules={[{ required: true }]}
      >
        <Select>
          <Select.Option value={GrantType.JWT_BEARER}>JWT断言</Select.Option>
          <Select.Option value={GrantType.TRUSTED_HEADER}>
            Trusted Header
          </Select.Option>
        </Select>
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) =>
          prevValues?.oauth2GrantType !== curValues?.oauth2GrantType ||
          prevValues?.oauth2JwtValidationMode !==
            curValues?.oauth2JwtValidationMode ||
          prevValues?.oauth2AcquireMode !== curValues?.oauth2AcquireMode ||
          prevValues?.oauth2IdentitySource !== curValues?.oauth2IdentitySource
        }
      >
        {({ getFieldValue }) => {
          const grantType =
            getFieldValue("oauth2GrantType") || GrantType.JWT_BEARER;
          const validationMode =
            getFieldValue("oauth2JwtValidationMode") || "PUBLIC_KEYS";
          const acquireMode = getFieldValue("oauth2AcquireMode") || "DIRECT";
          const identitySource =
            getFieldValue("oauth2IdentitySource") || "CLAIMS";

          if (grantType === GrantType.TRUSTED_HEADER) {
            return renderTrustedHeaderSection();
          }

          return renderJwtDirectSection(
            validationMode,
            acquireMode,
            identitySource
          );
        }}
      </Form.Item>
    </div>
  );
}
