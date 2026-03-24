import { Form, Input, Switch, Tooltip, Divider } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasAccessStrategySection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="accessStrategyEnabled"
          label={
            <span>
              启用访问策略&nbsp;
              <Tooltip title="[这是什么]: 是否对此 Portal 开启额外的准入规则检查。[什么时候用]: 如果你希望对不同 Portal 设置不同的访问门槛（如只有特定部门能进），请开启。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
          initialValue={true}
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="accessStrategySsoEnabled"
          label={
            <span>
              SSO 参与检查&nbsp;
              <Tooltip title="[这是什么]: 是否允许此 Service 参与 CAS 的单点登录会话。[什么时候用]: 建议开启。关闭后，即使用户在别处登录过 CAS，进入此 Portal 仍需重新认证。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
          initialValue={true}
        >
          <Switch />
        </Form.Item>
      </div>

      <Form.Item
        name="accessStrategyUnauthorizedRedirectUrl"
        label={
          <span>
            未授权重定向地址&nbsp;
            <Tooltip title="[这是什么]: 当用户 CAS 验证通过但没有权限进入此 Portal 时，跳转的提示页面。[什么时候用]: 用于引导无权限用户去申请权限。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
      >
        <Input placeholder="如: https://example.com/no-access" />
      </Form.Item>

      <Divider plain>时间与属性限制</Divider>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="accessStrategyStartingDateTime"
          label={
            <span>
              开始访问时间&nbsp;
              <Tooltip title="[这是什么]: 此对接配置生效的起始时间。[什么时候用]: 针对限时开放的活动门户设置。格式如: 2024-01-01T00:00:00Z">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="ISO 格式时间" />
        </Form.Item>
        <Form.Item
          name="accessStrategyEndingDateTime"
          label={
            <span>
              截止访问时间&nbsp;
              <Tooltip title="[这是什么]: 此对接配置失效的截止时间。[什么时候用]: 配合开始时间，定义一个访问窗口。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="ISO 格式时间" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="accessStrategyRequireAllAttributes"
          label={
            <span>
              满足所有属性&nbsp;
              <Tooltip title="[这是什么]: 当配置了多个'必要属性'时，是否要求用户必须全部满足。[什么时候用]: 设置为'是'代表'与'逻辑，设置为'否'代表'或'逻辑。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        <Form.Item
          name="accessStrategyCaseInsensitive"
          label={
            <span>
              属性大小写不敏感&nbsp;
              <Tooltip title="[这是什么]: 匹配用户属性值时是否区分大小写。[什么时候用]: 建议开启，防止因数据源录入不规范导致的拦截。">
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
