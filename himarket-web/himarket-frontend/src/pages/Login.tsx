import React, { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Alert, Button, Form, Input, Select, message } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { AxiosError } from "axios";
import api from "../lib/api";
import APIs from "../lib/apis";
import { Layout } from "../components/Layout";
import { EnterpriseLoginSection } from "../components/auth/EnterpriseLoginSection";
import {
  buildAuthRouteFromSearch,
  consumeReturnUrl,
  getSearchReturnUrl,
  persistReturnUrlFromSearch,
} from "../lib/authReturnUrl";
import {
  splitEnterpriseProviders,
  type LoginProvider,
} from "../lib/loginProviders";
import { setLastAuthState } from "../lib/authStorage";

type LdapProvider = {
  provider: string;
  name?: string;
};

const ENTERPRISE_PROVIDER_LABELS = ["OIDC", "CAS", "OAuth2"] as const;

function buildAuthorizeUrl(path: string, provider: string) {
  const apiPrefix = api.defaults.baseURL || "/api/v1";
  const apiBaseUrl = new URL(apiPrefix, window.location.origin);
  if (!apiBaseUrl.pathname.endsWith("/")) {
    apiBaseUrl.pathname += "/";
  }

  const authUrl = new URL(path.replace(/^\//, ""), apiBaseUrl);
  authUrl.searchParams.set("provider", provider);
  return authUrl.toString();
}

function buildEnterpriseLoadError(failedProviderTypes: string[]) {
  if (failedProviderTypes.length === 0) {
    return "";
  }

  if (failedProviderTypes.length === ENTERPRISE_PROVIDER_LABELS.length) {
    return "企业单点登录入口加载失败，请刷新页面或联系管理员检查身份源配置。";
  }

  return `部分企业登录入口加载失败：${failedProviderTypes.join("、")}。可先使用账号密码登录。`;
}

function tagProviders(
  providers: Array<Partial<LoginProvider>>,
  authType: LoginProvider["authType"]
) {
  return providers.map(provider => ({
    ...provider,
    interactiveBrowserLogin: provider.interactiveBrowserLogin === true,
    trustedHeaderLogin: provider.trustedHeaderLogin === true,
    authType,
  })) as LoginProvider[];
}

const Login: React.FC = () => {
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [ldapProviders, setLdapProviders] = useState<LdapProvider[]>([]);
  const [loading, setLoading] = useState(false);
  const [trustedHeaderLoading, setTrustedHeaderLoading] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [enterpriseLoadError, setEnterpriseLoadError] = useState("");
  const [ldapLoadError, setLdapLoadError] = useState("");
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    persistReturnUrlFromSearch(searchParams);
  }, [searchParams]);

  useEffect(() => {
    let active = true;

    void Promise.allSettled([
      APIs.getOidcProviders(),
      APIs.getCasProviders(),
      APIs.getOAuth2Providers(),
      APIs.getLdapProviders(),
    ]).then(results => {
      if (!active) {
        return;
      }

      const mergedProviders: LoginProvider[] = [];
      const failedEnterpriseProviders: string[] = [];

      if (results[0].status === "fulfilled") {
        mergedProviders.push(...tagProviders(results[0].value.data, "OIDC"));
      } else {
        failedEnterpriseProviders.push("OIDC");
      }

      if (results[1].status === "fulfilled") {
        mergedProviders.push(...tagProviders(results[1].value.data, "CAS"));
      } else {
        failedEnterpriseProviders.push("CAS");
      }

      if (results[2].status === "fulfilled") {
        mergedProviders.push(...tagProviders(results[2].value.data, "OAUTH2"));
      } else {
        failedEnterpriseProviders.push("OAuth2");
      }

      if (results[3].status === "fulfilled") {
        setLdapProviders(results[3].value.data || []);
        setLdapLoadError("");
      } else {
        setLdapProviders([]);
        setLdapLoadError("LDAP 登录方式加载失败，当前仅保留内置账号登录。");
      }

      setProviders(mergedProviders);
      setEnterpriseLoadError(
        buildEnterpriseLoadError(failedEnterpriseProviders)
      );
    });

    return () => {
      active = false;
    };
  }, []);

  const {
    recommendedProviders,
    advancedInteractiveProviders,
    trustedHeaderProviders,
  } = splitEnterpriseProviders(providers);

  const redirectAfterLogin = () => {
    navigate(consumeReturnUrl(searchParams), { replace: true });
  };

  const handlePasswordLogin = async (values: {
    username: string;
    password: string;
    loginMode?: string;
  }) => {
    setLoading(true);
    setLoginError("");

    try {
      const loginMode = values.loginMode || "builtin";
      const response =
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

      if (!response?.data?.access_token) {
        message.error("登录失败，未获取到访问令牌");
        return;
      }

      localStorage.setItem("access_token", response.data.access_token);
      if (loginMode === "builtin") {
        setLastAuthState({ type: "BUILTIN" });
      } else {
        setLastAuthState({
          type: "LDAP",
          provider: loginMode.replace(/^ldap:/, ""),
        });
      }

      message.success("登录成功！", 1);
      redirectAfterLogin();
    } catch (error) {
      if (error instanceof AxiosError) {
        const errorText =
          error.response?.data.message || "登录失败，请检查账号密码是否正确";
        setLoginError(errorText);
        message.error(errorText);
      } else {
        setLoginError("登录失败");
        message.error("登录失败");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleOidcLogin = (provider: LoginProvider) => {
    setLastAuthState({ type: "OIDC", provider: provider.provider });
    window.location.href = buildAuthorizeUrl(
      "/developers/oidc/authorize",
      provider.provider
    );
  };

  const handleCasLogin = (provider: LoginProvider) => {
    setLastAuthState({
      type: "CAS",
      provider: provider.provider,
      sloEnabled: !!provider.sloEnabled,
    });
    window.location.href = buildAuthorizeUrl(
      "/developers/cas/authorize",
      provider.provider
    );
  };

  const handleOAuth2Login = (provider: LoginProvider) => {
    setLastAuthState({ type: "OAUTH2", provider: provider.provider });
    window.location.href = buildAuthorizeUrl(
      "/developers/oauth2/authorize",
      provider.provider
    );
  };

  const handleProviderLogin = (provider: LoginProvider) => {
    if (provider.authType === "CAS") {
      handleCasLogin(provider);
      return;
    }

    if (provider.authType === "OAUTH2") {
      handleOAuth2Login(provider);
      return;
    }

    handleOidcLogin(provider);
  };

  const handleTrustedHeaderLogin = async (provider: LoginProvider) => {
    setTrustedHeaderLoading(true);
    try {
      const result = await APIs.loginWithTrustedHeader({
        provider: provider.provider,
      });

      if (!result?.data?.access_token) {
        throw new Error("未获取到访问令牌");
      }

      localStorage.setItem("access_token", result.data.access_token);
      setLastAuthState({ type: "OAUTH2", provider: provider.provider });
      redirectAfterLogin();
    } catch (error) {
      if (error instanceof AxiosError) {
        message.error(
          error.response?.data.message ||
            "企业 SSO 登录失败，请联系管理员检查代理配置"
        );
      } else if (error instanceof Error) {
        message.error(error.message);
      } else {
        message.error("企业 SSO 登录失败");
      }
    } finally {
      setTrustedHeaderLoading(false);
    }
  };

  return (
    <Layout>
      <div className="flex min-h-[calc(100vh-96px)] w-full items-center justify-center">
        <div className="mx-4 w-full max-w-lg">
          <div className="rounded-2xl bg-white/95 p-6 shadow-lg sm:p-8">
            <div className="mb-8">
              <div className="mb-3 inline-flex rounded-full bg-[#F5F7FF] px-3 py-1 text-xs font-medium text-colorPrimary">
                开发者登录
              </div>
              <h2 className="flex text-[32px] text-gray-900">
                <span className="text-colorPrimary">嗨，</span>
                您好
              </h2>
              <p className="mt-2 text-sm leading-6 text-[#85888D]">
                欢迎来到
                HiMarket。请先使用账号登录，或选择企业身份源完成单点登录。
              </p>
              {getSearchReturnUrl(searchParams) && (
                <p className="mt-3 text-xs leading-5 text-gray-500">
                  登录完成后将返回到之前访问的页面。
                </p>
              )}
            </div>

            <div className="rounded-2xl border border-gray-100 bg-[#FCFCFE] p-5">
              <div className="mb-4">
                <div className="text-sm font-semibold text-gray-900">
                  账号密码登录
                </div>
                <div className="mt-1 text-xs leading-5 text-gray-500">
                  适用于内置账号和 LDAP 账号。
                </div>
              </div>

              {ldapLoadError && (
                <Alert
                  showIcon
                  type="warning"
                  className="mb-4 rounded-xl"
                  message={ldapLoadError}
                />
              )}

              <Form
                name="login"
                onFinish={handlePasswordLogin}
                autoComplete="off"
                layout="vertical"
                size="large"
              >
                {ldapProviders.length > 0 && (
                  <Form.Item
                    name="loginMode"
                    initialValue="builtin"
                    label="登录方式"
                  >
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

                <Form.Item
                  name="username"
                  label="账号"
                  rules={[{ required: true, message: "请输入账号" }]}
                >
                  <Input
                    prefix={<UserOutlined className="text-gray-400" />}
                    placeholder="请输入账号…"
                    autoComplete="username"
                    spellCheck={false}
                    className="rounded-lg"
                  />
                </Form.Item>

                <Form.Item
                  name="password"
                  label="密码"
                  rules={[{ required: true, message: "请输入密码" }]}
                >
                  <Input.Password
                    prefix={<LockOutlined className="text-gray-400" />}
                    placeholder="请输入密码…"
                    autoComplete="current-password"
                    className="rounded-lg"
                  />
                </Form.Item>

                {loginError && (
                  <Form.Item>
                    <Alert
                      type="error"
                      showIcon
                      message={loginError}
                      className="rounded-lg"
                    />
                  </Form.Item>
                )}

                <Form.Item className="mb-0">
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    className="h-10 w-full rounded-lg"
                    size="large"
                  >
                    {loading ? "登录中…" : "登录"}
                  </Button>
                </Form.Item>
              </Form>
            </div>

            <EnterpriseLoginSection
              recommendedProviders={recommendedProviders}
              advancedInteractiveProviders={advancedInteractiveProviders}
              trustedHeaderProviders={trustedHeaderProviders}
              loading={loading}
              trustedHeaderLoading={trustedHeaderLoading}
              providerLoadError={enterpriseLoadError}
              onProviderLogin={handleProviderLogin}
              onTrustedHeaderLogin={provider =>
                void handleTrustedHeaderLogin(provider)
              }
            />

            <div className="text-center text-subTitle">
              没有账号？
              <Link
                to={buildAuthRouteFromSearch("/register", searchParams)}
                className="text-colorPrimary hover:text-colorPrimary hover:underline"
              >
                注册
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
