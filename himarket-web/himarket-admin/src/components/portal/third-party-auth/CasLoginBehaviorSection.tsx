import { Form, Switch, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasLoginBehaviorSection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="loginGateway"
          label={
            <span>
              Gateway (无交互登录)&nbsp;
              <Tooltip title="[这是什么]: 是否开启 CAS 的静默检测登录模式。[什么时候用]: 默认关闭。开启后，系统会尝试探测用户是否已登录 CAS，而不显示登录页。建议保持关闭以防重定向循环。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="loginRenew"
          label={
            <span>
              Renew (强制重新认证)&nbsp;
              <Tooltip title="[这是什么]: 是否强制用户每次进入都必须重新输入密码。[什么时候用]: 针对高安全级别应用。开启后将忽略 CAS 服务器现有的单点登录会话。">
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
          name="loginWarn"
          label={
            <span>
              Warn (确认提醒)&nbsp;
              <Tooltip title="[这是什么]: 是否在重定向回 HiMarket 前让用户点一下确认。[什么时候用]: 默认关闭。开启后用户会看到一个过渡页，通常用于提醒用户正在跨站跳转。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="loginRememberMe"
          label={
            <span>
              Remember Me (记住我)&nbsp;
              <Tooltip title="[这是什么]: 是否允许用户在 CAS 登录页勾选'记住我'。[什么时候用]: 建议开启，提升用户体验。">
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
