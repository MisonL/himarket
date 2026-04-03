import React, { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "antd";
import api from "../lib/api";
import { AuthStatusCard } from "../components/auth/AuthStatusCard";
import {
  buildStoredAuthRoute,
  consumeStoredReturnUrl,
} from "../lib/authReturnUrl";

const Callback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = React.useState<
    "processing" | "success" | "error"
  >("processing");
  const [statusMessage, setStatusMessage] =
    React.useState("正在完成登录信息处理…");

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const code = searchParams.get("code");
    const state = searchParams.get("state");

    if (!code || !state) {
      setStatus("error");
      setStatusMessage("缺少 code 或 state 参数，请重新发起登录。");
      return;
    }

    api
      .post<{ access_token: string }>("/developers/token", { code, state })
      .then(res => {
        if (res && res.data && res.data.access_token) {
          localStorage.setItem("access_token", res.data.access_token);
          setStatus("success");
          setStatusMessage("登录成功，正在进入 HiMarket…");
          window.setTimeout(() => {
            navigate(consumeStoredReturnUrl("/"), { replace: true });
          }, 800);
        } else {
          setStatus("error");
          setStatusMessage("登录失败，未获取到访问令牌。");
        }
      })
      .catch(() => {
        setStatus("error");
        setStatusMessage("登录失败，请重试。");
      });
  }, [location.search, navigate]);

  return (
    <AuthStatusCard
      status={status}
      title={
        status === "processing"
          ? "正在处理登录"
          : status === "success"
            ? "登录成功"
            : "登录未完成"
      }
      message={statusMessage}
      actions={
        status === "error"
          ? [
              <Button
                key="login"
                type="primary"
                onClick={() =>
                  navigate(buildStoredAuthRoute("/login"), { replace: true })
                }
              >
                返回登录页
              </Button>,
            ]
          : []
      }
    />
  );
};

export default Callback;
