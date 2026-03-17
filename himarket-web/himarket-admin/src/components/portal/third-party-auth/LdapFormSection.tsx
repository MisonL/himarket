import { Collapse, Form, Input } from "antd";

export function LdapFormSection() {
  return (
    <div className="space-y-6">
      <Form.Item
        name="serverUrl"
        label="LDAP 服务地址"
        rules={[
          { required: true, message: "请输入LDAP服务地址" },
          {
            validator: (_, value) => {
              const v = String(value || "").toLowerCase();
              return v.startsWith("ldap://") || v.startsWith("ldaps://")
                ? Promise.resolve()
                : Promise.reject(
                    new Error("请输入以 ldap:// 或 ldaps:// 开头的地址")
                  );
            },
          },
        ]}
      >
        <Input placeholder="如: ldap://ldap.example.com:389 或 ldaps://ldap.example.com:636" />
      </Form.Item>

      <Form.Item
        name="baseDn"
        label="Base DN"
        rules={[{ required: true, message: "请输入 Base DN" }]}
      >
        <Input placeholder="如: dc=example,dc=com" />
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="bindDn"
          label="Bind DN"
          extra="可选，用于查询用户。与 Bind 密码 配对使用。"
        >
          <Input placeholder="如: cn=admin,dc=example,dc=com" />
        </Form.Item>
        <Form.Item
          name="bindPassword"
          label="Bind 密码"
          extra="可选，与 Bind DN 配对使用。"
        >
          <Input.Password placeholder="可选" />
        </Form.Item>
      </div>

      <Form.Item
        name="userSearchFilter"
        label="用户搜索过滤器"
        extra="必须包含 {0} 占位符，例如 (uid={0}) 或 (sAMAccountName={0})."
        rules={[
          { required: true, message: "请输入用户搜索过滤器" },
          {
            validator: (_, value) =>
              value && String(value).includes("{0}")
                ? Promise.resolve()
                : Promise.reject(new Error("过滤器必须包含 {0} 占位符")),
          },
        ]}
      >
        <Input placeholder="默认: (uid={0})" />
      </Form.Item>

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
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item name="userIdField" label="开发者ID">
                      <Input placeholder="默认: uid" />
                    </Form.Item>
                    <Form.Item name="userNameField" label="开发者名称">
                      <Input placeholder="默认: cn" />
                    </Form.Item>
                    <Form.Item name="emailField" label="邮箱">
                      <Input placeholder="默认: mail" />
                    </Form.Item>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}
