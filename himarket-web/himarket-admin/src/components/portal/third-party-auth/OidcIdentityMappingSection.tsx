import { Collapse, Form, Input } from "antd";

export function OidcIdentityMappingSection() {
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
            key: "identity",
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
                <span className="ml-2">高级配置</span>
                <span className="text-xs text-gray-400 ml-2">身份映射</span>
              </div>
            ),
            children: (
              <div className="space-y-4 pt-2 ml-3">
                <div className="grid grid-cols-3 gap-4">
                  <Form.Item name="userIdField" label="开发者ID">
                    <Input placeholder="默认: sub" />
                  </Form.Item>
                  <Form.Item name="userNameField" label="开发者名称">
                    <Input placeholder="默认: name" />
                  </Form.Item>
                  <Form.Item name="emailField" label="邮箱">
                    <Input placeholder="默认: email" />
                  </Form.Item>
                </div>

                <div className="bg-blue-50 p-3 rounded-lg">
                  <div className="flex items-start space-x-2">
                    <div className="text-blue-600 mt-0.5">
                      <svg
                        className="w-4 h-4"
                        fill="currentColor"
                        viewBox="0 0 20 20"
                      >
                        <path
                          fillRule="evenodd"
                          d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                          clipRule="evenodd"
                        />
                      </svg>
                    </div>
                    <div>
                      <h4 className="text-blue-800 font-medium text-sm">
                        配置说明
                      </h4>
                      <p className="text-blue-700 text-xs mt-1">
                        身份映射用于从OIDC令牌中提取用户信息。如果不填写，系统将使用OIDC标准字段。
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
