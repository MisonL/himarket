import type { ReactNode } from "react";
import { Button } from "antd";
import {
  getProviderLabel,
  getProviderMonogram,
  getProviderTypeLabel,
} from "../../lib/loginProviders";
import type { LoginProvider } from "../../lib/loginProviders";
import aliyunIcon from "../../assets/aliyun.png";
import githubIcon from "../../assets/github.png";
import googleIcon from "../../assets/google.png";

const PROVIDER_ICONS: Record<string, ReactNode> = {
  google: <img src={googleIcon} alt="Google" className="h-5 w-5" />,
  github: <img src={githubIcon} alt="GitHub" className="h-6 w-6" />,
  aliyun: <img src={aliyunIcon} alt="Aliyun" className="h-6 w-6" />,
};

interface ProviderButtonProps {
  provider: LoginProvider;
  onClick: () => void;
  loading?: boolean;
  disabled?: boolean;
  description?: string;
}

export function ProviderButton({
  provider,
  onClick,
  loading = false,
  disabled = false,
  description = "浏览器将跳转到企业身份源完成认证",
}: ProviderButtonProps) {
  const providerIcon = PROVIDER_ICONS[provider.provider.toLowerCase()];

  return (
    <Button
      onClick={onClick}
      className="!h-auto w-full rounded-xl !border-gray-200 !px-4 !py-3 text-left shadow-sm"
      size="large"
      disabled={disabled}
      loading={loading}
    >
      <div className="flex w-full items-center gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#F5F7FF]">
          {providerIcon || (
            <span className="text-xs font-semibold text-colorPrimary">
              {getProviderMonogram(provider)}
            </span>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <span className="truncate font-medium text-gray-900">
              {getProviderLabel(provider)}
            </span>
            <span className="shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium uppercase tracking-[0.08em] text-gray-500">
              {getProviderTypeLabel(provider)}
            </span>
          </div>
          <div className="mt-1 text-xs leading-5 text-gray-500">
            {description}
          </div>
        </div>
      </div>
    </Button>
  );
}
