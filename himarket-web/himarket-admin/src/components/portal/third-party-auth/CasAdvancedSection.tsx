import { Form, Input, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasAdvancedSection() {
  return (
    <div className="space-y-6">
      <p className="text-xs text-gray-500 mb-4">
        [这是什么]: 此处存放 CAS 规范中不常改动或 HiMarket 后端自定义的扩展属性。[什么时候用]: 绝大多数情况下请保持默认。仅在需要精细化微调认证引擎行为时，由开发人员指导填写。
      </p>
      <Form.Item
        name="customFields"
        label={
          <span>
            自定义 JSON 配置&nbsp;
            <Tooltip title="支持 Key-Value 格式的额外配置项。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
      >
        <Input.TextArea
          rows={4}
          placeholder='如: {"extra_option": "value"}'
        />
      </Form.Item>
    </div>
  );
}
