import React, { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Divider, Form, Input, message, Select } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import { AxiosError } from "axios";
import { useTranslation } from "react-i18next";
import { Layout } from "../components/Layout";
import request from "../lib/request";
import APIs, { type IIdpProvider } from "../lib/apis";
import { setLastAuthState } from "../lib/authStorage";

import aliyunIcon from "../assets/aliyun.png";
import githubIcon from "../assets/github.png";
import googleIcon from "../assets/google.png";

type LoginProvider = IIdpProvider & {
  sloEnabled?: boolean;
  interactiveBrowserLogin?: boolean;
  authType: "OIDC" | "CAS";
};

const oidcIcons: Record<string, React.ReactNode> = {
  google: <img src={googleIcon} alt="Google" className="w-5 h-5 mr-2" />,
  github: <img src={githubIcon} alt="GitHub" className="w-6 h-6 mr-2" />,
  aliyun: <img src={aliyunIcon} alt="Aliyun" className="w-6 h-6 mr-2" />,
};

const Login: React.FC = () => {
  const { t } = useTranslation("login");
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [ldapProviders, setLdapProviders] = useState<
    Array<{ provider: string; name?: string }>
  >([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    Promise.allSettled([
      APIs.getOidcProviders(),
      APIs.getCasProviders(),
      APIs.getLdapProviders(),
    ])
      .then(results => {
        const mergedProviders: LoginProvider[] = [];

        if (results[0].status === "fulfilled") {
          mergedProviders.push(
            ...results[0].value.data.map(provider => ({
              ...provider,
              interactiveBrowserLogin:
                provider.interactiveBrowserLogin === true,
              authType: "OIDC" as const,
            }))
          );
        }

        if (results[1].status === "fulfilled") {
          mergedProviders.push(
            ...results[1].value.data.map(provider => ({
              ...provider,
              interactiveBrowserLogin:
                provider.interactiveBrowserLogin === true,
              authType: "CAS" as const,
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

  const interactiveProviders = providers.filter(
    provider => provider.interactiveBrowserLogin === true
  );

  const handlePasswordLogin = async (values: {
    username: string;
    password: string;
    loginMode?: string;
  }) => {
    setLoading(true);
    try {
      const loginMode = values.loginMode || "builtin";
      const res =
        loginMode === "builtin"
          ? await request.post("/developers/login", {
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
      if (res && res.data && res.data.access_token) {
        message.success(t("loginSuccess"), 1);
        localStorage.setItem("access_token", res.data.access_token);
        if (loginMode === "builtin") {
          setLastAuthState({ type: "BUILTIN" });
        } else if (loginMode.startsWith("ldap:")) {
          setLastAuthState({
            type: "LDAP",
            provider: loginMode.replace(/^ldap:/, ""),
          });
        }

        const returnUrl = searchParams.get("returnUrl");
        if (returnUrl) {
          navigate(decodeURIComponent(returnUrl));
        } else {
          navigate("/");
        }
      } else {
        message.error(t("loginFailedNoToken"));
      }
    } catch (error) {
      if (error instanceof AxiosError) {
        message.error(
          error.response?.data.message || t("loginFailedCheckCredentials")
        );
      } else {
        message.error(t("loginFailed"));
      }
    } finally {
      setLoading(false);
    }
  };

  const buildAuthorizeUrl = (path: string, provider: string) => {
    const apiPrefix = request.defaults.baseURL || "/api/v1";
    const apiBaseUrl = new URL(apiPrefix, window.location.origin);
    if (!apiBaseUrl.pathname.endsWith("/")) {
      apiBaseUrl.pathname += "/";
    }
    const authUrl = new URL(path.replace(/^\//, ""), apiBaseUrl);
    authUrl.searchParams.set("provider", provider);
    return authUrl.toString();
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

  return (
    <Layout>
      <div className="min-h-[calc(100vh-96px)] w-full flex items-center justify-center">
        <div className="w-full max-w-md mx-4">
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">{t("greeting")}</span>
                {t("hello")}
              </h2>
              <p className="text-sm text-[#85888D]">{t("welcomeMessage")}</p>
            </div>

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
                rules={[{ required: true, message: t("usernameRequired") }]}
              >
                <Input
                  prefix={<UserOutlined className="text-gray-400" />}
                  placeholder={t("usernamePlaceholder")}
                  autoComplete="username"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[{ required: true, message: t("passwordRequired") }]}
              >
                <Input.Password
                  prefix={<LockOutlined className="text-gray-400" />}
                  placeholder={t("passwordPlaceholder")}
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
                  {loading ? t("loggingIn") : t("login")}
                </Button>
              </Form.Item>
            </Form>

            {interactiveProviders.length > 0 && (
              <Divider plain className="text-subTitle">
                <span className="text-subTitle">{t("or")}</span>
              </Divider>
            )}

            <div className="flex flex-col gap-2 mb-2">
              {interactiveProviders.length === 0
                ? null
                : interactiveProviders.map(provider => (
                    <Button
                      key={provider.provider}
                      onClick={() =>
                        provider.authType === "CAS"
                          ? handleCasLogin(provider)
                          : handleOidcLogin(provider)
                      }
                      className="w-full flex items-center justify-center"
                      size="large"
                      icon={oidcIcons[provider.provider.toLowerCase()] || <span />}
                    >
                      {t("loginWithProvider", {
                        provider: provider.name || provider.provider,
                      })}
                    </Button>
                  ))}
            </div>

            <div className="text-center text-subTitle">
              {t("noAccount")}
              <Link
                to="/register"
                className="text-colorPrimary hover:text-colorPrimary hover:underline"
              >
                {t("registerLink")}
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
