import React, { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Form, Input, Button, message } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import api from "../lib/api";
import { Layout } from "../components/Layout";
import {
  buildAuthRouteFromSearch,
  getSearchReturnUrl,
} from "../lib/authReturnUrl";

const Register: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const handleRegister = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) => {
    setLoading(true);
    try {
      await api.post("/developers", {
        username: values.username,
        password: values.password,
      });
      message.success("注册成功！");
      navigate(buildAuthRouteFromSearch("/login", searchParams), {
        replace: true,
      });
    } catch {
      message.error("注册失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <div
        className="min-h-[calc(100vh-96px)] flex items-center justify-center "
        style={{
          backdropFilter: "blur(204px)",
          WebkitBackdropFilter: "blur(204px)",
        }}
      >
        <div className="w-full max-w-md mx-4">
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">嗨，</span>
                您好
              </h2>
              <p className="text-sm text-[#85888D]">欢迎来到 HiMarket</p>
              {getSearchReturnUrl(searchParams) && (
                <p className="mt-3 text-xs leading-5 text-gray-500">
                  注册完成后会回到登录页，并保留原始访问目标。
                </p>
              )}
            </div>

            <Form
              name="register"
              onFinish={handleRegister}
              autoComplete="off"
              layout="vertical"
              size="large"
            >
              <Form.Item
                name="username"
                label="账号"
                rules={[
                  { required: true, message: "请输入账号" },
                  { min: 3, message: "账号至少3个字符" },
                ]}
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
                rules={[
                  { required: true, message: "请输入密码" },
                  { min: 6, message: "密码至少6个字符" },
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className="text-gray-400" />}
                  placeholder="请输入密码…"
                  autoComplete="new-password"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item
                name="confirmPassword"
                label="确认密码"
                dependencies={["password"]}
                rules={[
                  { required: true, message: "请确认密码" },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue("password") === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error("两次输入的密码不一致"));
                    },
                  }),
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className="text-gray-400" />}
                  placeholder="请再次输入密码…"
                  autoComplete="new-password"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  className="rounded-lg w-full"
                  size="large"
                >
                  {loading ? "注册中…" : "注册"}
                </Button>
              </Form.Item>
            </Form>

            <div className="text-center text-subTitle">
              已有账号？
              <Link
                to={buildAuthRouteFromSearch("/login", searchParams)}
                className="text-colorPrimary hover:text-colorPrimary hover:underline"
              >
                登录
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Register;
