import { Form, Select, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasValidationSection() {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Form.Item
        name="validationProtocolVersion"
        label={
          <span>
            校验协议版本&nbsp;
            <Tooltip title="建议使用 CAS 3.0。SAML 1.1 用于支持多属性释放。CAS 1.0/2.0 用于兼容极老的服务器。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
        initialValue="CAS3"
      >
        <Select>
          <Select.Option value="CAS1">CAS 1.0 (基础验证)</Select.Option>
          <Select.Option value="CAS2">CAS 2.0 (代理验证)</Select.Option>
          <Select.Option value="CAS3">CAS 3.0 (主流/推荐)</Select.Option>
          <Select.Option value="SAML1">SAML 1.1 (多属性同步)</Select.Option>
        </Select>
      </Form.Item>
      <Form.Item
        name="validationResponseFormat"
        label={
          <span>
            校验响应格式&nbsp;
            <Tooltip title="CAS 服务器返回数据的格式。XML 是标准格式，JSON 仅部分现代 CAS 服务器支持。">
              <QuestionCircleOutlined />
            </Tooltip>
          </span>
        }
        initialValue="XML"
      >
        <Select>
          <Select.Option value="XML">XML (兼容性好)</Select.Option>
          <Select.Option value="JSON">JSON (现代/简洁)</Select.Option>
        </Select>
      </Form.Item>
    </div>
  );
}
