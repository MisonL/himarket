import React, { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "antd";
import APIs from "../lib/apis";
import { AuthStatusCard } from "../components/auth/AuthStatusCard";
import {
  buildStoredAuthRoute,
  consumeStoredReturnUrl,
} from "../lib/authReturnUrl";

const CasCallback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<"processing" | "success" | "error">(
    "processing"
  );
  const [statusMessage, setStatusMessage] = useState("正在校验 CAS 登录结果…");
  const processedRef = useRef(false);

  useEffect(() => {
    if (!processedRef.current) {
      processedRef.current = true;
      handleCasCallbackProcess();
    }
  }, [location.search]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCasCallbackProcess = async () => {
    try {
      setStatus("processing");
      setStatusMessage("正在校验 CAS 登录结果…");

      const searchParams = new URLSearchParams(location.search);
      const code = searchParams.get("code");

      if (!code) {
        setStatus("error");
        setStatusMessage("回调参数不完整，请重新发起登录。");
        return;
      }

      const authResult = await APIs.exchangeCasCode({ code });
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
          errorMessage = "CAS登录码无效或已过期";
        } else if (axiosError.response?.status === 404) {
          errorMessage = "CAS配置不存在";
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
          ? "正在处理 CAS 登录"
          : status === "success"
            ? "登录成功"
            : "CAS 登录未完成"
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

export default CasCallback;
