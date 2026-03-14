import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Button, Divider, Form, Input, Select } from "antd";
import api, { authApi } from "@/lib/api";
import { setLastAuthState } from "@/lib/authStorage";

interface IdpProvider {
  provider: string;
  name?: string;
  type?: string;
  sloEnabled?: boolean;
}

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isRegister, setIsRegister] = useState<boolean | null>(null);
  const [casProviders, setCasProviders] = useState<IdpProvider[]>([]);
  const [ldapProviders, setLdapProviders] = useState<IdpProvider[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await authApi.getNeedInit();
        const needInit = response.data === true;
        setIsRegister(needInit);
        if (!needInit) {
          await Promise.all([fetchCasProviders(), fetchLdapProviders()]);
        }
      } catch {
        setIsRegister(false);
      }
    };

    checkAuth();
  }, []);

  const fetchCasProviders = async () => {
    try {
      const res = await api.get("/admins/cas/providers");
      setCasProviders(Array.isArray(res?.data) ? res.data : []);
    } catch {
      setCasProviders([]);
    }
  };

  const fetchLdapProviders = async () => {
    try {
      const res = await api.get("/admins/ldap/providers");
      setLdapProviders(Array.isArray(res?.data) ? res.data : []);
    } catch {
      setLdapProviders([]);
    }
  };

  const buildAuthorizeUrl = (path: string, provider: string) => {
    const apiPrefix = api.defaults.baseURL || "/api/v1";
    const apiBaseUrl = new URL(apiPrefix, window.location.origin);
    if (!apiBaseUrl.pathname.endsWith("/")) {
      apiBaseUrl.pathname += "/";
    }

    const authUrl = new URL(path.replace(/^\//, ""), apiBaseUrl);
    authUrl.searchParams.set("provider", provider);
    return authUrl.toString();
  };

  const handleCasLogin = (provider: IdpProvider) => {
    setLastAuthState({
      type: "CAS",
      provider: provider.provider,
      sloEnabled: !!provider.sloEnabled,
    });
    window.location.href = buildAuthorizeUrl("/admins/cas/authorize", provider.provider);
  };

  const handleLogin = async (values: {
    username: string;
    password: string;
    loginMode?: string;
  }) => {
    setLoading(true);
    setError("");
    try {
      const loginMode = values.loginMode || "builtin";
      const response =
        loginMode === "builtin"
          ? await api.post("/admins/login", {
              username: values.username,
              password: values.password,
            })
          : loginMode.startsWith("ldap:")
            ? await api.post("/admins/ldap/login", {
                provider: loginMode.replace(/^ldap:/, ""),
                username: values.username,
                password: values.password,
              })
            : (() => {
                throw new Error("未知登录方式");
              })();

      const accessToken = response.data.access_token;
      localStorage.setItem("access_token", accessToken);
      localStorage.setItem("userInfo", JSON.stringify(response.data));
      if (loginMode === "builtin") {
        setLastAuthState({ type: "BUILTIN" });
      } else {
        setLastAuthState({
          type: "LDAP",
          provider: loginMode.replace(/^ldap:/, ""),
        });
      }
      navigate("/portals");
    } catch {
      setError("账号或密码错误");
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) => {
    setLoading(true);
    setError("");
    if (values.password !== values.confirmPassword) {
      setError("两次输入的密码不一致");
      setLoading(false);
      return;
    }
    try {
      const response = await api.post("/admins/init", {
        username: values.username,
        password: values.password,
      });
      if (response.data.adminId) {
        setIsRegister(false);
      }
    } catch {
      setError("初始化失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      <div
        className="hidden md:flex w-1/2 items-center justify-center relative overflow-hidden"
        style={{ background: "linear-gradient(135deg, #6366F1 0%, #4F46E5 50%, #818CF8 100%)" }}
      >
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-10 w-32 h-32 rounded-full bg-white" />
          <div className="absolute bottom-32 right-16 w-48 h-48 rounded-full bg-white" />
          <div className="absolute top-1/2 left-1/3 w-24 h-24 rounded-full bg-white" />
        </div>
        <div className="text-center text-white relative z-10">
          <img src="/logo.png" alt="Logo" className="w-20 h-20 mx-auto mb-6 drop-shadow-lg" />
          <h1 className="text-3xl font-bold mb-3">HiMarket</h1>
          <p className="text-lg opacity-80">企业级 AI 开放平台管理后台</p>
        </div>
      </div>

      <div className="w-full md:w-1/2 flex items-center justify-center bg-gradient-to-br from-indigo-50 to-white md:bg-white">
        <div className="w-full max-w-md px-8">
          <div className="md:hidden mb-6 text-center">
            <img src="/logo.png" alt="Logo" className="w-16 h-16 mx-auto mb-4" />
          </div>

          <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">
            {isRegister ? "注册Admin账号" : "登录HiMarket-后台"}
          </h2>

          {!isRegister && (
            <Form className="w-full flex flex-col gap-4" layout="vertical" onFinish={handleLogin}>
              {ldapProviders.length > 0 && (
                <Form.Item name="loginMode" initialValue="builtin" label="登录方式">
                  <Select
                    options={[
                      { label: "内置账号", value: "builtin" },
                      ...ldapProviders.map(provider => ({
                        label: `LDAP: ${provider.name || provider.provider}`,
                        value: `ldap:${provider.provider}`,
                      })),
                    ]}
                  />
                </Form.Item>
              )}
              <Form.Item name="username" rules={[{ required: true, message: "请输入账号" }]}>
                <Input placeholder="账号" size="large" />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: "请输入密码" }]}>
                <Input.Password placeholder="密码" size="large" />
              </Form.Item>
              {error && <Alert message={error} type="error" showIcon className="mb-2" />}
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  className="w-full"
                  loading={loading}
                  size="large"
                >
                  登录
                </Button>
              </Form.Item>
              {casProviders.length > 0 && (
                <>
                  <Divider plain className="text-gray-400">
                    或
                  </Divider>
                  <div className="flex flex-col gap-2">
                    {casProviders.map(provider => (
                      <Button
                        key={provider.provider}
                        className="w-full"
                        size="large"
                        onClick={() => handleCasLogin(provider)}
                      >
                        使用 {provider.name || provider.provider} 登录
                      </Button>
                    ))}
                  </div>
                </>
              )}
            </Form>
          )}

          {isRegister && (
            <Form
              className="w-full flex flex-col gap-4"
              layout="vertical"
              onFinish={handleRegister}
            >
              <Form.Item name="username" rules={[{ required: true, message: "请输入账号" }]}>
                <Input placeholder="账号" size="large" />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: "请输入密码" }]}>
                <Input.Password placeholder="密码" size="large" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                rules={[{ required: true, message: "请确认密码" }]}
              >
                <Input.Password placeholder="确认密码" size="large" />
              </Form.Item>
              {error && <Alert message={error} type="error" showIcon className="mb-2" />}
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  className="w-full"
                  loading={loading}
                  size="large"
                >
                  初始化
                </Button>
              </Form.Item>
            </Form>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
