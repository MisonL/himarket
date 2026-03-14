import React, { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Form, Input, Button, message, Divider, Select } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import api from "../lib/api";
import { AxiosError } from "axios";
import { Layout } from "../components/Layout";
import APIs from "../lib/apis";
import { setLastAuthState } from "../lib/authStorage";

import aliyunIcon from "../assets/aliyun.png";
import githubIcon from "../assets/github.png";
import googleIcon from "../assets/google.png";

type LoginProvider = {
  provider: string;
  name?: string;
  displayName?: string;
  sloEnabled?: boolean;
  authType: "OIDC" | "CAS";
};

const oidcIcons: Record<string, React.ReactNode> = {
  google: <img src={googleIcon} alt="Google" className="w-5 h-5 mr-2" />,
  github: <img src={githubIcon} alt="GitHub" className="w-6 h-6 mr-2" />,
  aliyun: <img src={aliyunIcon} alt="Aliyun" className="w-6 h-6 mr-2" />,
};

const Login: React.FC = () => {
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [ldapProviders, setLdapProviders] = useState<Array<{ provider: string; name?: string }>>(
    []
  );
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    Promise.allSettled([APIs.getOidcProviders(), APIs.getCasProviders(), APIs.getLdapProviders()])
      .then((results) => {
        const mergedProviders: LoginProvider[] = [];

        if (results[0].status === 'fulfilled') {
          mergedProviders.push(
            ...results[0].value.data.map((provider) => ({
              ...provider,
              authType: 'OIDC' as const,
            }))
          );
        }

        if (results[1].status === 'fulfilled') {
          mergedProviders.push(
            ...results[1].value.data.map((provider) => ({
              ...provider,
              authType: 'CAS' as const,
            }))
          );
        }

        if (results[2].status === "fulfilled") {
          setLdapProviders(results[2].value.data || []);
        } else {
          setLdapProviders([]);
        }

        setProviders(mergedProviders);
      })
      .catch(() => {
        setProviders([]);
        setLdapProviders([]);
      });
  }, []);

  // 账号密码登录
  const handlePasswordLogin = async (values: { username: string; password: string; loginMode?: string }) => {
    setLoading(true);
    try {
      const loginMode = values.loginMode || "builtin";
      const res =
        loginMode === "builtin"
          ? await api.post("/developers/login", {
              username: values.username,
              password: values.password,
            })
          : loginMode.startsWith("ldap:")
          ? await APIs.handleLdapLogin({
              provider: loginMode.replace(/^ldap:/, ""),
              username: values.username,
              password: values.password,
            })
          : (() => {
              throw new Error("未知登录方式");
            })();
      // 登录成功后跳转到首页并携带access_token
      if (res && res.data && res.data.access_token) {
        message.success('登录成功！', 1);
        localStorage.setItem('access_token', res.data.access_token)
        if (loginMode === "builtin") {
          setLastAuthState({ type: "BUILTIN" });
        } else if (loginMode.startsWith("ldap:")) {
          const provider = loginMode.replace(/^ldap:/, "");
          setLastAuthState({ type: "LDAP", provider });
        }

        // 检查URL中是否有returnUrl参数
        const returnUrl = searchParams.get('returnUrl');
        if (returnUrl) {
          navigate(decodeURIComponent(returnUrl));
        } else {
          navigate('/');
        }
      } else {
        message.error("登录失败，未获取到access_token");
      }
    } catch (error) {
      if (error instanceof AxiosError) {
        message.error(error.response?.data.message || "登录失败，请检查账号密码是否正确");
      } else {
        message.error("登录失败");
      }
    } finally {
      setLoading(false);
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

  // 跳转到 OIDC 授权 - 对接 /developers/oidc/authorize
  const handleOidcLogin = (provider: LoginProvider) => {
    setLastAuthState({ type: 'OIDC', provider: provider.provider });
    window.location.href = buildAuthorizeUrl("/developers/oidc/authorize", provider.provider);
  };

  const handleCasLogin = (provider: LoginProvider) => {
    setLastAuthState({
      type: 'CAS',
      provider: provider.provider,
      sloEnabled: !!provider.sloEnabled,
    });
    window.location.href = buildAuthorizeUrl("/developers/cas/authorize", provider.provider);
  };

  return (
    <Layout>
      <div
        className="min-h-[calc(100vh-96px)] w-full flex items-center justify-center"
      >
        <div className="w-full max-w-md mx-4">
          {/* 登录卡片 */}
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">
                  嗨，
                </span>
                您好
              </h2>
              <p className="text-sm text-[#85888D]">欢迎来到 HiMarket，登录以继续</p>
            </div>

            {/* 账号密码登录表单 */}
            <Form
              name="login"
              onFinish={handlePasswordLogin}
              autoComplete="off"
              layout="vertical"
              size="large"
            >
              {ldapProviders.length > 0 && (
                <Form.Item name="loginMode" initialValue="builtin" label="登录方式">
                  <Select
                    options={[
                      { label: "内置账号", value: "builtin" },
                      ...ldapProviders.map((p) => ({
                        label: `LDAP: ${p.name || p.provider}`,
                        value: `ldap:${p.provider}`,
                      })),
                    ]}
                  />
                </Form.Item>
              )}
              <Form.Item
                name="username"
                rules={[
                  { required: true, message: '请输入账号' }
                ]}
              >
                <Input
                  prefix={<UserOutlined className="text-gray-400" />}
                  placeholder="账号"
                  autoComplete="username"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[
                  { required: true, message: '请输入密码' }
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className="text-gray-400" />}
                  placeholder="密码"
                  autoComplete="current-password"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  className="w-full rounded-lg h-10"
                  size="large"
                >
                  {loading ? "登录中..." : "登录"}
                </Button>
              </Form.Item>
            </Form>
            {/* 分隔线 */}
            {
              providers.length > 0 && (
                <Divider plain className="text-subTitle"><span className="text-subTitle">或</span></Divider>
              )
            }
            {/* OIDC 登录按钮 */}
            <div className="flex flex-col gap-2 mb-2">
              {providers.length === 0 ? (
                null
              ) : (
                providers.map((provider) => (
                  <Button
                    key={provider.provider}
                    onClick={() => (
                      provider.authType === 'CAS'
                        ? handleCasLogin(provider)
                        : handleOidcLogin(provider)
                    )}
                    className="w-full flex items-center justify-center"
                    size="large"
                    icon={oidcIcons[provider.provider.toLowerCase()] || <span></span>}
                  >
                    使用 {provider.name || provider.provider} 登录
                  </Button>
                ))
              )}
            </div>
            <div className="text-center text-subTitle">
              没有账号？<Link to="/register" className="text-colorPrimary hover:text-colorPrimary hover:underline">注册</Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
