import { Collapse, Form, Input, Tooltip } from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";

export function CasIdentityMappingSection() {
  return (
    <div className="-ml-3">
      <Collapse
        size="small"
        ghost
        expandIcon={({ isActive }) => (
          <svg
            className={`w-4 h-4 transition-transform ${isActive ? "rotate-90" : ""}`}
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path
              fillRule="evenodd"
              d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
              clipRule="evenodd"
            />
          </svg>
        )}
        items={[
          {
            key: "advanced",
            label: (
              <div className="flex items-center text-gray-600">
                <svg
                  className="w-4 h-4"
                  fill="currentColor"
                  viewBox="0 0 20 20"
                >
                  <path
                    fillRule="evenodd"
                    d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                    clipRule="evenodd"
                  />
                  <path
                    fillRule="evenodd"
                    d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                    clipRule="evenodd"
                  />
                </svg>
                <span className="ml-2">身份映射</span>
                <span className="text-xs text-gray-400 ml-2">决定用户信息来源</span>
              </div>
            ),
            children: (
              <div className="space-y-4 pt-2 ml-3">
                <div className="grid grid-cols-3 gap-4">
                  <Form.Item
                    name="userIdField"
                    label={
                      <span>
                        用户ID字段&nbsp;
                        <Tooltip title="CAS 返回的哪个属性作为 HiMarket 的唯一用户 ID。通常是 user 或 uid。">
                          <QuestionCircleOutlined />
                        </Tooltip>
                      </span>
                    }
                  >
                    <Input placeholder="默认: user" />
                  </Form.Item>
                  <Form.Item
                    name="userNameField"
                    label={
                      <span>
                        显示名称字段&nbsp;
                        <Tooltip title="同步到 HiMarket 的用户真名或昵称字段。">
                          <QuestionCircleOutlined />
                        </Tooltip>
                      </span>
                    }
                  >
                    <Input placeholder="默认: user" />
                  </Form.Item>
                  <Form.Item
                    name="emailField"
                    label={
                      <span>
                        邮箱字段&nbsp;
                        <Tooltip title="同步到 HiMarket 的电子邮箱字段。">
                          <QuestionCircleOutlined />
                        </Tooltip>
                      </span>
                    }
                  >
                    <Input placeholder="默认: mail" />
                  </Form.Item>
                </div>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
