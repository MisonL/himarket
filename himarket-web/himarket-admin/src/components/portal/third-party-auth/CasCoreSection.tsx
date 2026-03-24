import { Form, Input, Switch, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasCoreSection() {
  return (
    <>
      <Form.Item
        name="serverUrl"
        label={
          <span>
            CAS 服务地址&nbsp;
            <Tooltip title="CAS 服务器的根访问地址，例如 https://sso.example.com/cas。系统将基于此地址自动推导登录和校验路径。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
        rules={[
          { required: true, message: "请输入CAS服务地址" },
          { type: "url", message: "请输入有效的URL" },
        ]}
      >
        <Input id="cas_server_url" autoComplete="off" placeholder="如: https://cas.example.com/cas" />
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="loginEndpoint"
          label={
            <span>
              登录地址&nbsp;
              <Tooltip title="用户点击登录按钮后跳转的 CAS 登录页面地址。不设置则默认推导为 [服务地址]/login">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input id="cas_login_endpoint" autoComplete="off" placeholder="可选，默认由服务地址推导 /login" />
        </Form.Item>
        <Form.Item
          name="validateEndpoint"
          label={
            <span>
              票据校验地址&nbsp;
              <Tooltip title="后端服务器用来验证 CAS Ticket 合法性的接口。不设置则默认推导为 [服务地址]/p3/serviceValidate">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input id="cas_validate_endpoint" autoComplete="off" placeholder="可选，默认由服务地址推导 /p3/serviceValidate" />
        </Form.Item>
      </div>

      <Form.Item
        name="logoutEndpoint"
        label={
          <span>
            登出地址&nbsp;
            <Tooltip title="注销登录时跳转的 CAS 登出接口。不设置则默认推导为 [服务地址]/logout">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
      >
        <Input id="cas_logout_endpoint" autoComplete="off" placeholder="可选，默认由服务地址推导 /logout" />
      </Form.Item>

      <Form.Item
        name="sloEnabled"
        label="单点登出 (SLO)"
        valuePropName="checked"
        extra="开启后，在 HiMarket 退出登录时，系统会尝试通知 CAS 服务器注销该用户的全局会话。"
      >
        <Switch />
      </Form.Item>
    </>
  );
}
