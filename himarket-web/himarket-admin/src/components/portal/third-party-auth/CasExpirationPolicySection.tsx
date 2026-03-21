import { Form, Input, Switch, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasExpirationPolicySection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="expirationPolicyExpirationDate"
          label={
            <span>
              配置过期日期&nbsp;
              <Tooltip title="[这是什么]: 此对接配置的硬性失效时间。[什么时候用]: 用于临时活动门户或者是即将废弃的旧 SSO 系统对接，确保到期后自动下线。格式如: 2030-12-31T23:59:59Z">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: 2030-12-31T23:59:59Z" />
        </Form.Item>
        <Form.Item
          name="expirationPolicyDeleteWhenExpired"
          label={
            <span>
              过期自动删除&nbsp;
              <Tooltip title="[这是什么]: 到达过期日期后是否物理删除此配置。[什么时候用]: 默认关闭。开启后，过期的配置将不再出现在列表中。">
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
          name="expirationPolicyNotifyWhenExpired"
          label={
            <span>
              过期时通知&nbsp;
              <Tooltip title="[这是什么]: 到达过期日期时是否通过邮件或消息通知管理员。[什么时候用]: 建议开启，防止由于配置意外过期导致的管理员登录失败。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="expirationPolicyNotifyWhenDeleted"
          label={
            <span>
              删除时通知&nbsp;
              <Tooltip title="[这是什么]: 当配置被系统物理删除时是否发送通知。[什么时候用]: 仅在开启了'过期自动删除'时有意义。">
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
