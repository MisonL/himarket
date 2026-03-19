import { Form, Input, Select, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasAttributeReleaseSection() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="attributeReleaseMode"
          label={
            <span>
              属性释放模式&nbsp;
              <Tooltip title="[这是什么]: 决定 CAS 向 HiMarket 返回哪些用户属性。[什么时候用]: RETURN_ALLOWED(白名单): 仅返回下方指定的属性; RETURN_ALL(全量): 返回 CAS 拥有的所有信息。建议选 RETURN_ALLOWED。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
          initialValue="RETURN_ALLOWED"
        >
          <Select>
            <Select.Option value="RETURN_ALLOWED">允许列表 (RETURN_ALLOWED)</Select.Option>
            <Select.Option value="RETURN_ALL">全量返回 (RETURN_ALL)</Select.Option>
            <Select.Option value="DENY_ALL">全部拒绝 (DENY_ALL)</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="attributeReleaseAllowedAttributes"
          label={
            <span>
              允许的属性列表&nbsp;
              <Tooltip title="[这是什么]: 明确允许获取的 CAS 属性 Key。[什么时候用]: 用于过滤敏感信息。多个用逗号分隔，如: mail, cn, mobile">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input placeholder="如: mail, cn, display_name" />
        </Form.Item>
      </div>
    </div>
  );
}
