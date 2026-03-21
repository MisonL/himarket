import { Form, Input, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasContactsSection() {
  return (
    <div className="space-y-6">
      <div className="bg-gray-50 p-4 rounded-lg">
        <p className="text-xs text-gray-500 mb-4">
          [这是什么]: 在此填写的联系信息将包含在生成的 Service Definition 中。[什么时候用]: 用于 CAS 服务端的元数据管理，方便 CAS 管理员在出现认证故障时联系对应的 HiMarket 负责人。
        </p>
        <Form.Item
          name="serviceContacts"
          label={
            <span>
              JSON 格式联系人列表&nbsp;
              <Tooltip title="请输入 JSON 数组，包含 name, email, phone, department, type 字段。">
                <QuestionCircleOutlined />
              </Tooltip>
            </span>
          }
        >
          <Input.TextArea
            rows={4}
            placeholder='如: [{"name":"Admin SRE","email":"sre@example.com","department":"Operations"}]'
          />
        </Form.Item>
      </div>
    </div>
  );
}
