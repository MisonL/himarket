import { Form, Input, InputNumber, Select, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasServiceDefinitionSection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="serviceDefinitionServiceId"
          label={
            <span>
              服务数字ID&nbsp;
              <Tooltip title="[这是什么]: 在 CAS 服务器中注册的唯一数字标识。[什么时候用]: 仅在需要显式匹配 CAS 服务端特定配置记录时填写。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <InputNumber placeholder="如: 1001" className="w-full" />
        </Form.Item>
        <Form.Item
          name="serviceDefinitionEvaluationOrder"
          label={
            <span>
              评价顺序&nbsp;
              <Tooltip title="[这是什么]: 当 CAS 服务端存在多个匹配规则时，此 Service 的优先级。[什么时候用]: 数字越小优先级越高。用于精细化控制多个 Portal 间的匹配权重。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue={0}
        >
          <InputNumber placeholder="如: 0" className="w-full" />
        </Form.Item>
      </div>

      <Form.Item
        name="serviceDefinitionServiceIdPattern"
        label={
          <span>
            服务匹配正则 (Service ID Pattern)&nbsp;
            <Tooltip title="[这是什么]: 定义 CAS 服务端识别此 HiMarket 实例的匹配表达式。[什么时候用]: 默认自动生成。如果需要支持动态域名或者是特殊的 URL 匹配规则，请手动修改。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
      >
        <Input placeholder="如: ^https://portal\\.example\\.com/.*$" />
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="serviceDefinitionResponseType"
          label={
            <span>
              响应类型&nbsp;
              <Tooltip title="[这是什么]: CAS 服务器向 HiMarket 发送 Ticket 的方式。[什么时候用]: REDIRECT(跳转): 最通用; POST(表单): 安全性更高，防止 Ticket 在 URL 中泄露。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue="REDIRECT"
        >
          <Select>
            <Select.Option value="REDIRECT">跳转 (REDIRECT)</Select.Option>
            <Select.Option value="POST">表单提交 (POST)</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="serviceDefinitionLogoutType"
          label={
            <span>
              注销类型&nbsp;
              <Tooltip title="[这是什么]: 发生全局注销时，CAS 通知 HiMarket 的方式。[什么时候用]: BACK_CHANNEL(后端通知): 服务器直连，更可靠; FRONT_CHANNEL(前端跳转): 浏览器端完成。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue="BACK_CHANNEL"
        >
          <Select>
            <Select.Option value="BACK_CHANNEL">后端通知 (BACK_CHANNEL)</Select.Option>
            <Select.Option value="FRONT_CHANNEL">前端跳转 (FRONT_CHANNEL)</Select.Option>
            <Select.Option value="NONE">不通知 (NONE)</Select.Option>
          </Select>
        </Form.Item>
      </div>

      <Form.Item
        name="serviceDefinitionLogoutUrl"
        label={
          <span>
            自定义注销回调地址&nbsp;
            <Tooltip title="[这是什么]: CAS 服务器在执行退出操作时，显式回调的 HiMarket 路径。[什么时候用]: 仅当默认的注销路径无法满足复杂的集群负载均衡需求时设置。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
      >
        <Input placeholder="如: https://api.example.com/logout-callback" />
      </Form.Item>
    </div>
  );
}
