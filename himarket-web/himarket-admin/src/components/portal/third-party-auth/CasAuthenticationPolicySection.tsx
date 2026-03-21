import { Form, Select, Input, Switch, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasAuthenticationPolicySection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="authenticationPolicyCriteriaMode"
          label={
            <span>
              策略匹配模式&nbsp;
              <Tooltip title="[这是什么]: 决定如何匹配认证处理器。[什么时候用]: 如果希望只有特定的认证源（如LDAP）能访问，选 ALLOWED；如果希望排除某些认证源，选 EXCLUDED。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue="ALLOWED"
        >
          <Select>
            <Select.Option value="ALLOWED">允许 (ALLOWED)</Select.Option>
            <Select.Option value="EXCLUDED">排除 (EXCLUDED)</Select.Option>
            <Select.Option value="REQUIRED">强校验 (REQUIRED)</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="authenticationPolicyRequiredHandlers"
          label={
            <span>
              必须的认证处理器&nbsp;
              <Tooltip title="[这是什么]: 指定必须通过的 CAS 认证处理器名。[什么时候用]: 当公司要求此 Portal 必须走特定的认证逻辑（如双因素验证）时设置。多个用逗号分隔。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: LdapAuthenticationHandler" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="authenticationPolicyExcludedHandlers"
          label={
            <span>
              排除的认证处理器&nbsp;
              <Tooltip title="[这是什么]: 明确禁止用于此 Portal 的认证处理器。[什么时候用]: 当你不希望某些低安全等级的登录方式（如临时测试账号）进入此生产环境门户时设置。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: TestAuthenticationHandler" />
        </Form.Item>
        <Form.Item
          name="authenticationPolicyTryAll"
          label={
            <span>
              尝试所有处理器&nbsp;
              <Tooltip title="[这是什么]: 是否在认证失败时继续尝试下一个处理器。[什么时候用]: 默认关闭。仅当你有多个互相依赖的身份源且需要逐一探测时开启。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
      </div>
    </div>
  );
}
