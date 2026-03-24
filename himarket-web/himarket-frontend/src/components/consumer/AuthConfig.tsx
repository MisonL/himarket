import React, { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  InfoCircleOutlined,
  PlusOutlined,
} from "@ant-design/icons";
import api from "../../lib/api";
import { modelStyles } from "../../lib/styles";
import { copyToClipboard } from "../../lib/utils";
import type { ApiResponse } from "../../types";
import type {
  APIKeyCredential,
  ConsumerCredential,
  ConsumerCredentialResult,
  CreateCredentialParam,
  CredentialType,
  HMACCredential,
  JWTCredentialConfig,
} from "../../types/consumer";

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const MODE_LABEL: Record<CredentialType, string> = {
  API_KEY: "API Key",
  HMAC: "HMAC",
  JWT: "JWT",
};

const DEFAULT_SOURCE = "Default";
const DEFAULT_KEY = "Authorization";

interface AuthConfigProps {
  consumerId: string;
}

const inferCredentialType = (
  config?: ConsumerCredentialResult | null
): CredentialType => {
  if (config?.credentialType) {
    return config.credentialType;
  }
  if (config?.jwtConfig?.issuer || config?.jwtConfig?.jwks) {
    return "JWT";
  }
  if ((config?.hmacConfig?.credentials?.length || 0) > 0) {
    return "HMAC";
  }
  return "API_KEY";
};

const splitTags = (values?: string[]) =>
  (values || []).filter(Boolean).map(value => value.trim()).filter(Boolean);

const inferSourceLabel = (source?: string, key?: string) =>
  source === DEFAULT_SOURCE ? "Authorization: Bearer <token>" : `${source}: ${key}`;

const buildJwtInitialValues = (config?: JWTCredentialConfig) => ({
  issuer: config?.issuer || "",
  jwks: config?.jwks || "",
  fromHeaders:
    config?.fromHeaders?.length && config.fromHeaders.length > 0
      ? config.fromHeaders
      : [{ name: "Authorization", valuePrefix: "Bearer " }],
  fromParams: splitTags(config?.fromParams),
  fromCookies: splitTags(config?.fromCookies),
  claimsToHeaders:
    config?.claimsToHeaders?.length && config.claimsToHeaders.length > 0
      ? config.claimsToHeaders
      : [],
  clockSkewSeconds: config?.clockSkewSeconds ?? 60,
  keepToken: config?.keepToken ?? true,
});

const sanitizeJwtConfig = (values: Record<string, unknown>): JWTCredentialConfig => ({
  issuer: String(values.issuer || "").trim(),
  jwks: String(values.jwks || "").trim(),
  fromHeaders: ((values.fromHeaders as Array<Record<string, unknown>>) || [])
    .map(item => ({
      name: String(item?.name || "").trim(),
      valuePrefix: String(item?.valuePrefix || ""),
    }))
    .filter(item => item.name),
  fromParams: splitTags(values.fromParams as string[]),
  fromCookies: splitTags(values.fromCookies as string[]),
  claimsToHeaders: ((values.claimsToHeaders as Array<Record<string, unknown>>) || [])
    .map(item => ({
      claim: String(item?.claim || "").trim(),
      header: String(item?.header || "").trim(),
      override: Boolean(item?.override),
    }))
    .filter(item => item.claim && item.header),
  clockSkewSeconds:
    values.clockSkewSeconds === undefined || values.clockSkewSeconds === null
      ? undefined
      : Number(values.clockSkewSeconds),
  keepToken: Boolean(values.keepToken),
});

export function AuthConfig({ consumerId }: AuthConfigProps) {
  const [currentConfig, setCurrentConfig] =
    useState<ConsumerCredentialResult | null>(null);
  const [activeType, setActiveType] = useState<CredentialType>("API_KEY");
  const [currentSource, setCurrentSource] = useState(DEFAULT_SOURCE);
  const [currentKey, setCurrentKey] = useState(DEFAULT_KEY);
  const [editingSource, setEditingSource] = useState(DEFAULT_SOURCE);
  const [editingKey, setEditingKey] = useState(DEFAULT_KEY);
  const [sourceModalVisible, setSourceModalVisible] = useState(false);
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [sourceForm] = Form.useForm();
  const [credentialForm] = Form.useForm();
  const [jwtForm] = Form.useForm();

  const savedType = useMemo(() => inferCredentialType(currentConfig), [currentConfig]);

  const fetchCurrentConfig = React.useCallback(async () => {
    try {
      const response: ApiResponse<ConsumerCredentialResult> = await api.get(
        `/consumers/${consumerId}/credentials`
      );
      const config = response?.code === "SUCCESS" && response.data ? response.data : {};
      setCurrentConfig(config);
      const resolvedType = inferCredentialType(config);
      setActiveType(resolvedType);
      const source = config.apiKeyConfig?.source || DEFAULT_SOURCE;
      const key = config.apiKeyConfig?.key || DEFAULT_KEY;
      setCurrentSource(source);
      setCurrentKey(key);
      jwtForm.setFieldsValue(buildJwtInitialValues(config.jwtConfig));
    } catch (error) {
      console.error("获取当前配置失败:", error);
    }
  }, [consumerId, jwtForm]);

  useEffect(() => {
    fetchCurrentConfig();
  }, [fetchCurrentConfig]);

  useEffect(() => {
    if (activeType === "JWT") {
      const initialValues =
        savedType === "JWT"
          ? buildJwtInitialValues(currentConfig?.jwtConfig)
          : buildJwtInitialValues();
      jwtForm.setFieldsValue(initialValues);
    }
  }, [activeType, currentConfig, jwtForm, savedType]);

  const currentApiKeyConfig =
    savedType === "API_KEY"
      ? currentConfig?.apiKeyConfig
      : { credentials: [], source: DEFAULT_SOURCE, key: DEFAULT_KEY };
  const currentHmacConfig =
    savedType === "HMAC" ? currentConfig?.hmacConfig : { credentials: [] };

  const modeChangeMessage =
    currentConfig &&
    savedType !== activeType &&
    ((savedType === "JWT" && currentConfig.jwtConfig?.issuer) ||
      currentApiKeyConfig?.credentials?.length ||
      currentHmacConfig?.credentials?.length)
      ? `切换并保存为 ${MODE_LABEL[activeType]} 后，将替换当前 ${MODE_LABEL[savedType]} 配置。`
      : null;

  const handleCopyCredential = async (text?: string) => {
    if (!text) {
      return;
    }
    await copyToClipboard(text);
    message.success("已复制到剪贴板");
  };

  const buildRandomCredential = (length: number) => {
    const chars =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    return Array.from({ length }, () =>
      chars.charAt(Math.floor(Math.random() * chars.length))
    ).join("");
  };

  const openCredentialModal = () => {
    credentialForm.resetFields();
    credentialForm.setFieldsValue({
      generationMethod: "SYSTEM",
      customApiKey: "",
      customAccessKey: "",
      customSecretKey: "",
    });
    setCredentialModalVisible(true);
  };

  const handleCreateCredential = async () => {
    const values = await credentialForm.validateFields();
    setSubmitting(true);
    try {
      const payload: CreateCredentialParam = { credentialType: activeType };

      if (activeType === "API_KEY") {
        const credential: APIKeyCredential = {
          apiKey:
            values.generationMethod === "CUSTOM"
              ? values.customApiKey
              : buildRandomCredential(32),
          mode: values.generationMethod,
        };
        payload.apiKeyConfig = {
          source: currentSource,
          key: currentKey,
          credentials: [...(currentApiKeyConfig?.credentials || []), credential],
        };
      }

      if (activeType === "HMAC") {
        const credential: HMACCredential = {
          ak:
            values.generationMethod === "CUSTOM"
              ? values.customAccessKey
              : buildRandomCredential(32),
          sk:
            values.generationMethod === "CUSTOM"
              ? values.customSecretKey
              : buildRandomCredential(64),
          mode: values.generationMethod,
        };
        payload.hmacConfig = {
          credentials: [...(currentHmacConfig?.credentials || []), credential],
        };
      }

      await api.put(`/consumers/${consumerId}/credentials`, payload);
      message.success("凭证保存成功");
      setCredentialModalVisible(false);
      await fetchCurrentConfig();
    } catch (error) {
      console.error("保存凭证失败:", error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteCredential = async (credential: ConsumerCredential) => {
    setSubmitting(true);
    try {
      const payload: CreateCredentialParam = { credentialType: activeType };
      if (activeType === "API_KEY") {
        payload.apiKeyConfig = {
          source: currentSource,
          key: currentKey,
          credentials: (currentApiKeyConfig?.credentials || []).filter(
            item => item.apiKey !== (credential as APIKeyCredential).apiKey
          ),
        };
      }
      if (activeType === "HMAC") {
        payload.hmacConfig = {
          credentials: (currentHmacConfig?.credentials || []).filter(
            item => item.ak !== (credential as HMACCredential).ak
          ),
        };
      }
      await api.put(`/consumers/${consumerId}/credentials`, payload);
      message.success("凭证删除成功");
      await fetchCurrentConfig();
    } catch (error) {
      console.error("删除凭证失败:", error);
    } finally {
      setSubmitting(false);
    }
  };

  const openSourceModal = () => {
    const nextSource = currentSource || DEFAULT_SOURCE;
    const nextKey = nextSource === DEFAULT_SOURCE ? DEFAULT_KEY : currentKey || "";
    setEditingSource(nextSource);
    setEditingKey(nextKey);
    sourceForm.setFieldsValue({ source: nextSource, key: nextKey });
    setSourceModalVisible(true);
  };

  const handleSaveSource = async () => {
    const values = await sourceForm.validateFields();
    setSubmitting(true);
    try {
      await api.put(`/consumers/${consumerId}/credentials`, {
        credentialType: "API_KEY",
        apiKeyConfig: {
          source: values.source,
          key: values.source === DEFAULT_SOURCE ? DEFAULT_KEY : values.key,
          credentials: currentApiKeyConfig?.credentials || [],
        },
      });
      setEditingSource(values.source);
      setEditingKey(values.source === DEFAULT_SOURCE ? DEFAULT_KEY : values.key);
      message.success("凭证来源更新成功");
      setSourceModalVisible(false);
      await fetchCurrentConfig();
    } catch (error) {
      console.error("更新凭证来源失败:", error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveJwtConfig = async () => {
    const values = await jwtForm.validateFields();
    setSubmitting(true);
    try {
      await api.put(`/consumers/${consumerId}/credentials`, {
        credentialType: "JWT",
        jwtConfig: sanitizeJwtConfig(values),
      });
      message.success("JWT 配置保存成功");
      await fetchCurrentConfig();
    } catch (error) {
      console.error("保存 JWT 配置失败:", error);
    } finally {
      setSubmitting(false);
    }
  };

  const apiKeyColumns = [
    {
      title: "API Key",
      dataIndex: "apiKey",
      key: "apiKey",
      render: (apiKey?: string) => (
        <Space>
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">
            {apiKey}
          </code>
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(apiKey)}
          />
        </Space>
      ),
    },
    {
      title: "模式",
      dataIndex: "mode",
      key: "mode",
      render: (mode?: string) => <Tag>{mode || "SYSTEM"}</Tag>,
    },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: ConsumerCredential) => (
        <Popconfirm
          title="确定要删除该 API Key 凭证吗？"
          onConfirm={() => handleDeleteCredential(record)}
        >
          <Button icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
    },
  ];

  const hmacColumns = [
    {
      title: "Access Key",
      dataIndex: "ak",
      key: "ak",
      render: (ak?: string) => (
        <Space>
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">
            {ak}
          </code>
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(ak)}
          />
        </Space>
      ),
    },
    {
      title: "Secret Key",
      dataIndex: "sk",
      key: "sk",
      render: (sk?: string) => (
        <Space>
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">
            {sk ? `${sk.slice(0, 4)}${"*".repeat(Math.max(sk.length - 8, 0))}${sk.slice(-4)}` : ""}
          </code>
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(sk)}
          />
        </Space>
      ),
    },
    {
      title: "模式",
      dataIndex: "mode",
      key: "mode",
      render: (mode?: string) => <Tag>{mode || "SYSTEM"}</Tag>,
    },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: ConsumerCredential) => (
        <Popconfirm
          title="确定要删除该 HMAC 凭证吗？"
          onConfirm={() => handleDeleteCredential(record)}
        >
          <Button icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div className="bg-white backdrop-blur-sm rounded-xl border border-white/60 shadow-sm overflow-hidden">
      <div className="p-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-base font-semibold text-gray-900 mb-1">认证模式</h3>
            <Text type="secondary">当前只允许一种 consumer 凭证模式生效。</Text>
          </div>
          <Tag color="blue">已生效: {MODE_LABEL[savedType]}</Tag>
        </div>

        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          value={activeType}
          onChange={event => setActiveType(event.target.value)}
          options={[
            { label: "API Key", value: "API_KEY" },
            { label: "HMAC", value: "HMAC" },
            { label: "JWT", value: "JWT" },
          ]}
        />

        {modeChangeMessage ? <Alert type="warning" showIcon message={modeChangeMessage} /> : null}

        {activeType === "API_KEY" ? (
          <div className="space-y-4">
            <div className="p-4 rounded-lg border border-[#e5e5e5]">
              <div className="flex flex-wrap items-center gap-3">
                <span className="font-medium">凭证来源</span>
                <Text type="secondary">{inferSourceLabel(currentSource, currentKey)}</Text>
                <Button type="text" icon={<EditOutlined />} onClick={openSourceModal}>
                  编辑
                </Button>
              </div>
            </div>
            <Button type="primary" icon={<PlusOutlined />} className="rounded-lg" onClick={openCredentialModal}>
              新增 API Key
            </Button>
            <Table
              columns={apiKeyColumns}
              dataSource={currentApiKeyConfig?.credentials || []}
              rowKey={record => record.apiKey || Math.random().toString()}
              pagination={false}
              locale={{ emptyText: "当前没有 API Key 凭证。" }}
            />
          </div>
        ) : null}

        {activeType === "HMAC" ? (
          <div className="space-y-4">
            <Paragraph className="mb-0 text-gray-500">
              HMAC 模式下使用 AK/SK 凭证访问产品。
            </Paragraph>
            <Button type="primary" icon={<PlusOutlined />} className="rounded-lg" onClick={openCredentialModal}>
              新增 AK/SK
            </Button>
            <Table
              columns={hmacColumns}
              dataSource={currentHmacConfig?.credentials || []}
              rowKey={record => record.ak || record.sk || Math.random().toString()}
              pagination={false}
              locale={{ emptyText: "当前没有 HMAC 凭证。" }}
            />
          </div>
        ) : null}

        {activeType === "JWT" ? (
          <div className="space-y-4">
            <Alert
              type="info"
              showIcon
              message="JWT 模式会把当前 consumer 作为单一 JWT 校验配置同步给后端。"
            />
            <Form form={jwtForm} layout="vertical" initialValues={buildJwtInitialValues()}>
              <Form.Item
                label="Issuer"
                name="issuer"
                rules={[{ required: true, message: "请输入 issuer" }]}
              >
                <Input placeholder="https://issuer.example.com" />
              </Form.Item>
              <Form.Item
                label="JWKS"
                name="jwks"
                rules={[{ required: true, message: "请输入 JWKS JSON" }]}
              >
                <TextArea rows={6} placeholder='{"keys":[...]}' />
              </Form.Item>
              <Form.List name="fromHeaders">
                {(fields, { add, remove }) => (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="font-medium">fromHeaders</span>
                      <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ name: "", valuePrefix: "" })}>
                        添加 Header
                      </Button>
                    </div>
                    {fields.map(field => (
                      <Space key={field.key} align="start" className="flex">
                        <Form.Item
                          {...field}
                          name={[field.name, "name"]}
                          rules={[{ required: true, message: "请输入 header 名称" }]}
                        >
                          <Input placeholder="Authorization" />
                        </Form.Item>
                        <Form.Item {...field} name={[field.name, "valuePrefix"]}>
                          <Input placeholder="Bearer " />
                        </Form.Item>
                        <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                      </Space>
                    ))}
                  </div>
                )}
              </Form.List>
              <Form.Item label="fromParams" name="fromParams">
                <Select mode="tags" tokenSeparators={[","]} placeholder="输入参数名后回车" />
              </Form.Item>
              <Form.Item label="fromCookies" name="fromCookies">
                <Select mode="tags" tokenSeparators={[","]} placeholder="输入 Cookie 名后回车" />
              </Form.Item>
              <Form.List name="claimsToHeaders">
                {(fields, { add, remove }) => (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="font-medium">claimsToHeaders</span>
                      <Button
                        type="dashed"
                        icon={<PlusOutlined />}
                        onClick={() => add({ claim: "", header: "", override: true })}
                      >
                        添加映射
                      </Button>
                    </div>
                    {fields.map(field => (
                      <div key={field.key} className="p-3 rounded-lg border border-[#e5e5e5]">
                        <Space align="start" className="flex">
                          <Form.Item
                            {...field}
                            name={[field.name, "claim"]}
                            rules={[{ required: true, message: "请输入 claim" }]}
                          >
                            <Input placeholder="sub" />
                          </Form.Item>
                          <Form.Item
                            {...field}
                            name={[field.name, "header"]}
                            rules={[{ required: true, message: "请输入 header" }]}
                          >
                            <Input placeholder="x-user-id" />
                          </Form.Item>
                          <Form.Item {...field} name={[field.name, "override"]} valuePropName="checked">
                            <Switch checkedChildren="覆盖" unCheckedChildren="追加" />
                          </Form.Item>
                          <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                        </Space>
                      </div>
                    ))}
                  </div>
                )}
              </Form.List>
              <Form.Item
                label="clockSkewSeconds"
                name="clockSkewSeconds"
                rules={[{ type: "number", min: 0, transform: value => Number(value) }]}
              >
                <Input type="number" min={0} />
              </Form.Item>
              <Form.Item label="keepToken" name="keepToken" valuePropName="checked">
                <Switch checkedChildren="保留" unCheckedChildren="移除" />
              </Form.Item>
            </Form>
            <div className="flex justify-end">
              <Button type="primary" loading={submitting} onClick={handleSaveJwtConfig}>
                保存 JWT 配置
              </Button>
            </div>
          </div>
        ) : null}
      </div>

      <Modal
        title={`新增 ${activeType === "API_KEY" ? "API Key" : "AK/SK"}`}
        open={credentialModalVisible}
        onCancel={() => setCredentialModalVisible(false)}
        onOk={handleCreateCredential}
        confirmLoading={submitting}
        okText="保存"
        cancelText="取消"
        styles={modelStyles}
      >
        <Form form={credentialForm} initialValues={{ generationMethod: "SYSTEM" }}>
          <Form.Item
            label="生成方式"
            name="generationMethod"
            rules={[{ required: true, message: "请选择生成方式" }]}
          >
            <Radio.Group>
              <Radio value="SYSTEM">系统生成</Radio>
              <Radio value="CUSTOM">自定义</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.generationMethod !== curr.generationMethod}>
            {({ getFieldValue }) =>
              getFieldValue("generationMethod") === "CUSTOM" ? (
                <>
                  {activeType === "API_KEY" ? (
                    <Form.Item
                      label="API Key"
                      name="customApiKey"
                      rules={[
                        { required: true, message: "请输入 API Key" },
                        { pattern: /^[A-Za-z0-9_-]+$/, message: "仅支持英文、数字、下划线和短横线" },
                        { min: 8, message: "长度至少 8 位" },
                        { max: 128, message: "长度不能超过 128 位" },
                      ]}
                    >
                      <Input placeholder="请输入 API Key" maxLength={128} />
                    </Form.Item>
                  ) : (
                    <>
                      <Form.Item
                        label="Access Key"
                        name="customAccessKey"
                        rules={[
                          { required: true, message: "请输入 Access Key" },
                          { pattern: /^[A-Za-z0-9_-]+$/, message: "仅支持英文、数字、下划线和短横线" },
                          { min: 8, message: "长度至少 8 位" },
                          { max: 128, message: "长度不能超过 128 位" },
                        ]}
                      >
                        <Input placeholder="请输入 Access Key" maxLength={128} />
                      </Form.Item>
                      <Form.Item
                        label="Secret Key"
                        name="customSecretKey"
                        rules={[
                          { required: true, message: "请输入 Secret Key" },
                          { pattern: /^[A-Za-z0-9_-]+$/, message: "仅支持英文、数字、下划线和短横线" },
                          { min: 8, message: "长度至少 8 位" },
                          { max: 128, message: "长度不能超过 128 位" },
                        ]}
                      >
                        <Input placeholder="请输入 Secret Key" maxLength={128} />
                      </Form.Item>
                    </>
                  )}
                </>
              ) : (
                <div className="text-sm text-gray-500 flex items-center gap-2">
                  <InfoCircleOutlined />
                  <span>系统将自动生成符合规范的凭证。</span>
                </div>
              )
            }
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑凭证来源"
        open={sourceModalVisible}
        onCancel={() => setSourceModalVisible(false)}
        onOk={handleSaveSource}
        confirmLoading={submitting}
        okText="保存"
        cancelText="取消"
        styles={modelStyles}
      >
        <Form form={sourceForm} layout="vertical" initialValues={{ source: editingSource, key: editingKey }}>
          <Form.Item
            label="凭证来源"
            name="source"
            rules={[{ required: true, message: "请选择凭证来源" }]}
          >
            <Select
              onChange={value =>
                sourceForm.setFieldsValue({ key: value === DEFAULT_SOURCE ? DEFAULT_KEY : "" })
              }
            >
              <Select.Option value="Header">Header</Select.Option>
              <Select.Option value="QueryString">QueryString</Select.Option>
              <Select.Option value="Default">默认</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.source !== curr.source}>
            {({ getFieldValue }) =>
              getFieldValue("source") !== DEFAULT_SOURCE ? (
                <Form.Item
                  label="键名"
                  name="key"
                  rules={[
                    { required: true, message: "请输入键名" },
                    { pattern: /^[A-Za-z0-9-_]+$/, message: "仅支持字母、数字、下划线和短横线" },
                  ]}
                >
                  <Input placeholder="请输入键名" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
