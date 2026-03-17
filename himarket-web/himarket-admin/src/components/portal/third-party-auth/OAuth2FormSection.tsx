import {
  KeyOutlined,
  MinusCircleOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import { Button, Collapse, Form, Input, Radio, Select } from "antd";
import { GrantType, PublicKeyFormat } from "@/types";

export function OAuth2FormSection() {
  return (
    <div className="space-y-6">
      <Form.Item
        name="oauth2GrantType"
        label="授权模式"
        initialValue={GrantType.JWT_BEARER}
        rules={[{ required: true }]}
      >
        <Select disabled>
          <Select.Option value={GrantType.JWT_BEARER}>JWT断言</Select.Option>
        </Select>
      </Form.Item>

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

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) =>
          prevValues?.oauth2JwtValidationMode !==
          curValues?.oauth2JwtValidationMode
        }
      >
        {({ getFieldValue }) => {
          const mode =
            getFieldValue("oauth2JwtValidationMode") || "PUBLIC_KEYS";
          if (mode === "JWKS") {
            return (
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
                          return Promise.resolve();
                        }
                        const invalid = value.some(
                          (v: string) => !v || !v.trim()
                        );
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
            );
          }

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
                            onClick={e => {
                              e.stopPropagation();
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
                                rules={[
                                  { required: true, message: "请输入Key ID" },
                                ]}
                              >
                                <Input placeholder="公钥标识符" size="small" />
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[name, "algorithm"]}
                                label="签名算法"
                                rules={[
                                  { required: true, message: "请选择签名算法" },
                                ]}
                              >
                                <Select placeholder="选择签名算法" size="small">
                                  <Select.Option value="RS256">
                                    RS256
                                  </Select.Option>
                                  <Select.Option value="RS384">
                                    RS384
                                  </Select.Option>
                                  <Select.Option value="RS512">
                                    RS512
                                  </Select.Option>
                                  <Select.Option value="ES256">
                                    ES256
                                  </Select.Option>
                                  <Select.Option value="ES384">
                                    ES384
                                  </Select.Option>
                                  <Select.Option value="ES512">
                                    ES512
                                  </Select.Option>
                                </Select>
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[name, "format"]}
                                label="公钥格式"
                                rules={[
                                  { required: true, message: "请选择公钥格式" },
                                ]}
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
                                const prevFormat =
                                  prevValues?.publicKeys?.[name]?.format;
                                const curFormat =
                                  curValues?.publicKeys?.[name]?.format;
                                return prevFormat !== curFormat;
                              }}
                            >
                              {({ getFieldValue }) => {
                                const format = getFieldValue([
                                  "publicKeys",
                                  name,
                                  "format",
                                ]);
                                return (
                                  <Form.Item
                                    {...restField}
                                    name={[name, "value"]}
                                    label="公钥内容"
                                    rules={[
                                      {
                                        required: true,
                                        message: "请输入公钥内容",
                                      },
                                    ]}
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
        }}
      </Form.Item>
    </div>
  );
}
