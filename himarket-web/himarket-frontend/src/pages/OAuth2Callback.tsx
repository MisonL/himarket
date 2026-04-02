import React, { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "antd";
import APIs from "../lib/apis";
import { AuthStatusCard } from "../components/auth/AuthStatusCard";
import {
  buildStoredAuthRoute,
  consumeStoredReturnUrl,
} from "../lib/authReturnUrl";
import {
  mergeOAuth2CallbackParams,
  resolveOAuth2CallbackJwt,
} from "../lib/oauth2Callback";

const SUCCESS_REDIRECT_DELAY_MS = 800;

const OAuth2Callback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<"processing" | "error" | "success">(
    "processing"
  );
  const [statusMessage, setStatusMessage] = useState("正在校验企业身份信息…");
  const processedRef = useRef(false);

  useEffect(() => {
    if (!processedRef.current) {
      processedRef.current = true;
      void handleOAuth2CallbackProcess();
    }
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleOAuth2CallbackProcess = async () => {
    try {
      setStatus("processing");
      setStatusMessage("正在校验企业身份信息…");

      const params = mergeOAuth2CallbackParams(location.search, location.hash);
      const provider = params.get("provider");
      const state = params.get("state");
      const ticket = params.get("ticket");
      const error = params.get("error");
      const errorDescription = params.get("error_description");
      const jwt = resolveOAuth2CallbackJwt(params);

      if (error) {
        setStatus("error");
        setStatusMessage(`登录失败：${errorDescription || error}`);
        return;
      }

      if (!state || !provider) {
        setStatus("error");
        setStatusMessage("回调参数不完整，请重新发起登录。");
        return;
      }

      if (!ticket && !jwt) {
        setStatus("error");
        setStatusMessage("回调中缺少 JWT 或 ticket，无法完成登录。");
        return;
      }

      setStatusMessage("身份校验通过，正在签发访问令牌…");
      const authResult = await APIs.completeOAuth2BrowserLogin({
        provider,
        state,
        jwt,
        ticket: ticket || undefined,
      });
      if (!authResult?.data?.access_token) {
        throw new Error("未获取到访问令牌");
      }

      localStorage.setItem("access_token", authResult.data.access_token);
      setStatus("success");
      setStatusMessage("登录成功，正在进入 HiMarket…");
      window.setTimeout(() => {
        navigate(consumeStoredReturnUrl("/"), { replace: true });
      }, SUCCESS_REDIRECT_DELAY_MS);
    } catch (error) {
      let errorMessage = "登录失败，请重试";

      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as { response?: { status: number } };
        if (axiosError.response?.status === 400) {
          errorMessage = "OAuth2 回调参数无效或已过期";
        } else if (axiosError.response?.status === 404) {
          errorMessage = "OAuth2 配置不存在";
        }
      } else if (error instanceof Error && error.message) {
        errorMessage = error.message;
      }

      setStatus("error");
      setStatusMessage(errorMessage);
    }
  };

  const handleBackToLogin = () => {
    navigate(buildStoredAuthRoute("/login"), { replace: true });
  };

  return (
    <AuthStatusCard
      status={status}
      title={
        status === "processing"
          ? "正在处理企业登录"
          : status === "success"
            ? "登录成功"
            : "企业登录未完成"
      }
      message={statusMessage}
      actions={
        status === "error"
          ? [
              <Button type="primary" key="login" onClick={handleBackToLogin}>
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

export default OAuth2Callback;
