import type { ReactNode } from "react";
import { Result, Spin } from "antd";

interface AuthStatusCardProps {
  status: "processing" | "success" | "error";
  title: string;
  message: string;
  actions?: ReactNode[];
}

export function AuthStatusCard({
  status,
  title,
  message,
  actions = [],
}: AuthStatusCardProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-lg rounded-2xl border border-gray-100 bg-white p-8 text-center shadow-sm">
        {status === "processing" ? (
          <>
            <Spin size="large" />
            <div className="mt-6 text-lg font-semibold text-gray-900">
              {title}
            </div>
            <div className="mt-2 text-sm leading-6 text-gray-500">
              {message}
            </div>
          </>
        ) : (
          <Result
            status={status}
            title={title}
            subTitle={message}
            extra={actions}
          />
        )}
      </div>
    </div>
  );
}
