import { Form, Input, Switch, Select, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasMultifactorSection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="multifactorProviders"
          label={
            <span>
              MFA 提供商&nbsp;
              <Tooltip title="[这是什么]: 指定 CAS 端配置的多因子认证提供商标识 (如 mfa-duo, mfa-gauth)。[什么时候用]: 当此门户属于高安全级别，必须进行二次验证时填入。多个用逗号分隔。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: mfa-duo" />
        </Form.Item>
        <Form.Item
          name="multifactorFailureMode"
          label={
            <span>
              MFA 失败处理模式&nbsp;
              <Tooltip title="[这是什么]: 当 MFA 服务不可用时的行为。[什么时候用]: CLOSED(严控): MFA 挂了则禁止登录; OPEN(宽松): MFA 挂了则跳过验证。建议生产选 CLOSED。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue="UNDEFINED"
        >
          <Select>
            <Select.Option value="UNDEFINED">未定义 (默认)</Select.Option>
            <Select.Option value="CLOSED">严控 (CLOSED)</Select.Option>
            <Select.Option value="NOT_SET">未设置 (NOT_SET)</Select.Option>
            <Select.Option value="OPEN">宽松 (OPEN)</Select.Option>
            <Select.Option value="PHANTOM">影子模式 (PHANTOM)</Select.Option>
          </Select>
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="multifactorBypassEnabled"
          label={
            <span>
              启用 MFA 旁路&nbsp;
              <Tooltip title="[这是什么]: 是否允许满足特定条件的用户跳过 MFA。[什么时候用]: 比如内网用户免 MFA，或者特定高管账号免 MFA。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="multifactorForceExecution"
          label={
            <span>
              强制执行 MFA&nbsp;
              <Tooltip title="[这是什么]: 是否忽略 CAS 端已有的 MFA 信任 Session。[什么时候用]: 如果设为开启，用户每次登录都必须重新过一遍 MFA，安全性最高。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="multifactorBypassPrincipalAttributeName"
          label={
            <span>
              旁路匹配属性名&nbsp;
              <Tooltip title="[这是什么]: 用于判断是否跳过 MFA 的用户属性字段。[什么时候用]: 比如填 memberOf，根据用户所属组来决定。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: memberOf" />
        </Form.Item>
        <Form.Item
          name="multifactorBypassPrincipalAttributeValue"
          label={
            <span>
              旁路匹配属性值&nbsp;
              <Tooltip title="[这是什么]: 属性名对应的具体值。[什么时候用]: 如果属性名填 memberOf，此处填 internal，则 internal 组用户跳过 MFA。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: internal" />
        </Form.Item>
      </div>
    </div>
  );
}
