import React, { useEffect, useState, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "antd";
import { handleOidcCallback, type AuthResult } from "../lib/api";
import { AuthStatusCard } from "../components/auth/AuthStatusCard";
import {
  buildStoredAuthRoute,
  consumeStoredReturnUrl,
} from "../lib/authReturnUrl";

const OidcCallback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<"processing" | "success" | "error">(
    "processing"
  );
  const [statusMessage, setStatusMessage] = useState("正在校验企业身份信息…");
  const processedRef = useRef(false);

  useEffect(() => {
    if (!processedRef.current) {
      processedRef.current = true;
      void handleOidcCallbackProcess();
    }
  }, [location.search]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleOidcCallbackProcess = async () => {
    try {
      setStatus("processing");
      setStatusMessage("正在校验企业身份信息…");

      const searchParams = new URLSearchParams(location.search);
      const code = searchParams.get("code");
      const state = searchParams.get("state");
      const error = searchParams.get("error");
      const errorDescription = searchParams.get("error_description");

      if (error) {
        setStatus("error");
        setStatusMessage(`登录失败：${errorDescription || error}`);
        return;
      }

      if (!code || !state) {
        setStatus("error");
        setStatusMessage("回调参数不完整，请重新发起登录。");
        return;
      }

      const authResult: AuthResult = await handleOidcCallback(code, state);
      if (!authResult?.data?.access_token) {
        throw new Error("未获取到访问令牌");
      }

      localStorage.setItem("access_token", authResult.data.access_token);
      setStatus("success");
      setStatusMessage("登录成功，正在进入 HiMarket…");
      window.setTimeout(() => {
        navigate(consumeStoredReturnUrl("/"), { replace: true });
      }, 800);
    } catch (error) {
      let errorMessage = "登录失败，请重试";
      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as { response?: { status: number } };
        if (axiosError.response?.status === 400) {
          errorMessage = "授权码无效或已过期";
        } else if (axiosError.response?.status === 404) {
          errorMessage = "OIDC配置不存在";
        }
      } else if (error instanceof Error && error.message) {
        errorMessage = error.message;
      }

      setStatus("error");
      setStatusMessage(errorMessage);
    }
  };

  return (
    <AuthStatusCard
      status={status}
      title={
        status === "processing"
          ? "正在处理 OIDC 登录"
          : status === "success"
            ? "登录成功"
            : "OIDC 登录未完成"
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
              <Button key="retry" onClick={() => window.location.reload()}>
                重新处理当前回调
              </Button>,
            ]
          : []
      }
    />
  );
};

export default OidcCallback;
