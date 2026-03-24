import { useState } from "react";
import { Button, Form, Modal, message } from "antd";
import { PlusOutlined, ExclamationCircleOutlined } from "@ant-design/icons";
import { ThirdPartyAuthConfig } from "@/types";
import { portalApi } from "@/lib/api";
import { ThirdPartyAuthConfigModal } from "./third-party-auth/ThirdPartyAuthConfigModal";
import { ThirdPartyAuthTabs } from "./third-party-auth/ThirdPartyAuthTabs";
import {
  createCasColumns,
  createLdapColumns,
  createOAuth2Columns,
  createOidcColumns,
} from "./third-party-auth/authConfigColumns";
import { buildDefaultFormValues } from "./third-party-auth/defaultFormValues";
import { buildFormFieldsFromConfig } from "./third-party-auth/formValueReaders";
import { buildConfigFromFormValues } from "./third-party-auth/formValueWriters";

// 强制本地定义认证类型字符串，防止枚举引用异常
const AUTH_TYPES = {
  OIDC: "OIDC",
  CAS: "CAS",
  LDAP: "LDAP",
  OAUTH2: "OAUTH2",
};

interface ThirdPartyAuthManagerProps {
  portalId?: string;
  configs: ThirdPartyAuthConfig[];
  onSave: (configs: ThirdPartyAuthConfig[]) => Promise<void>;
}

export function ThirdPartyAuthManager({
  portalId,
  configs = [],
  onSave,
}: ThirdPartyAuthManagerProps) {
  const [form] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editingConfig, setEditingConfig] =
    useState<ThirdPartyAuthConfig | null>(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedType, setSelectedType] = useState<string | null>(null);

  // 添加新配置
  const handleAdd = () => {
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    setModalVisible(true);
    form.resetFields();
  };

  // 编辑配置
  const handleEdit = (config: ThirdPartyAuthConfig) => {
    setEditingConfig(config);
    setSelectedType(config.type);
    setCurrentStep(1); // 直接进入配置步骤
    setModalVisible(true);
    form.setFieldsValue(buildFormFieldsFromConfig(config));
  };

  // 删除配置
  const handleDelete = async (provider: string, name: string) => {
    Modal.confirm({
      title: "确认删除",
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除第三方认证配置 "${name}" 吗？此操作不可恢复。`,
      okText: "确认删除",
      okType: "danger",
      cancelText: "取消",
      async onOk() {
        try {
          const updatedConfigs = configs.filter(
            config => config.provider !== provider
          );
          await onSave(updatedConfigs);
          message.success("第三方认证配置删除成功");
        } catch {
          message.error("删除第三方认证配置失败");
        }
      },
    });
  };

  const handlePreviewCasServiceDefinition = async (provider: string) => {
    if (!portalId) {
      message.error(
        "当前页面未提供 Portal 上下文，无法导出 CAS service definition"
      );
      return;
    }

    try {
      const definition = await portalApi.exportCasServiceDefinition(
        portalId,
        provider
      );
      Modal.info({
        title: `CAS Service Definition: ${provider}`,
        width: 860,
        okText: "关闭",
        content: (
          <pre className="max-h-[60vh] overflow-auto rounded bg-gray-50 p-4 text-xs leading-5 text-gray-800">
            {JSON.stringify(definition, null, 2)}
          </pre>
        ),
      });
    } catch {
      message.error("导出 CAS service definition 失败");
    }
  };

  // 下一步
  const handleNext = async () => {
    if (currentStep === 0) {
      try {
        const values = await form.validateFields(["type"]);
        setSelectedType(values.type);
        setCurrentStep(1);
        form.setFieldsValue(buildDefaultFormValues(values.type));
      } catch {
        // 验证失败
      }
    }
  };

  // 上一步
  const handlePrevious = () => {
    setCurrentStep(0);
  };

  // 保存配置
  const handleSave = async () => {
    try {
      setLoading(true);

      const values = await form.validateFields();
      const newConfig = buildConfigFromFormValues(selectedType as any, values);

      let updatedConfigs;
      if (editingConfig) {
        updatedConfigs = configs.map(config =>
          config.provider === editingConfig.provider ? newConfig : config
        );
      } else {
        updatedConfigs = [...configs, newConfig];
      }

      await onSave(updatedConfigs);

      message.success(
        editingConfig ? "第三方认证配置更新成功" : "第三方认证配置添加成功"
      );
      setModalVisible(false);
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : "保存第三方认证配置失败"
      );
    } finally {
      setLoading(false);
    }
  };

  // 取消
  const handleCancel = () => {
    setModalVisible(false);
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    form.resetFields();
  };

  const oidcColumns = createOidcColumns({
    onEdit: handleEdit,
    onDelete: handleDelete,
  });
  const casColumns = createCasColumns({
    onEdit: handleEdit,
    onDelete: handleDelete,
    onPreviewCasServiceDefinition: handlePreviewCasServiceDefinition,
  });
  const ldapColumns = createLdapColumns({
    onEdit: handleEdit,
    onDelete: handleDelete,
  });
  const oauth2Columns = createOAuth2Columns({
    onEdit: handleEdit,
    onDelete: handleDelete,
  });

  const oidcConfigs = configs.filter(
    config => String(config.type) === AUTH_TYPES.OIDC
  );
  const casConfigs = configs.filter(
    config => String(config.type) === AUTH_TYPES.CAS
  );
  const ldapConfigs = configs.filter(
    config => String(config.type) === AUTH_TYPES.LDAP
  );
  const oauth2Configs = configs.filter(
    config => String(config.type) === AUTH_TYPES.OAUTH2
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-medium">第三方认证</h3>
          <p className="text-sm text-gray-500">管理外部身份认证配置</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          添加配置
        </Button>
      </div>

      <ThirdPartyAuthTabs
        oidcColumns={oidcColumns}
        casColumns={casColumns}
        ldapColumns={ldapColumns}
        oauth2Columns={oauth2Columns}
        oidcConfigs={oidcConfigs}
        casConfigs={casConfigs}
        ldapConfigs={ldapConfigs}
        oauth2Configs={oauth2Configs}
      />
      <ThirdPartyAuthConfigModal
        configs={configs}
        editingConfig={editingConfig}
        form={form}
        loading={loading}
        modalVisible={modalVisible}
        currentStep={currentStep}
        selectedType={selectedType}
        onCancel={handleCancel}
        onNext={handleNext}
        onPrevious={handlePrevious}
        onSave={handleSave}
      />
    </div>
  );
}
