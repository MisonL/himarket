import { Alert, Collapse, Divider } from "antd";
import type { LoginProvider } from "../../lib/loginProviders";
import { ProviderButton } from "./ProviderButton";

interface EnterpriseLoginSectionProps {
  recommendedProviders: LoginProvider[];
  advancedInteractiveProviders: LoginProvider[];
  trustedHeaderProviders: LoginProvider[];
  loading: boolean;
  trustedHeaderLoading: boolean;
  providerLoadError?: string;
  onProviderLogin: (provider: LoginProvider) => void;
  onTrustedHeaderLogin: (provider: LoginProvider) => void;
}

export function EnterpriseLoginSection({
  recommendedProviders,
  advancedInteractiveProviders,
  trustedHeaderProviders,
  loading,
  trustedHeaderLoading,
  providerLoadError,
  onProviderLogin,
  onTrustedHeaderLogin,
}: EnterpriseLoginSectionProps) {
  const advancedCount =
    advancedInteractiveProviders.length + trustedHeaderProviders.length;
  const hasProviders =
    recommendedProviders.length > 0 ||
    advancedInteractiveProviders.length > 0 ||
    trustedHeaderProviders.length > 0;

  if (!hasProviders && !providerLoadError) {
    return null;
  }

  return (
    <>
      <Divider plain className="text-subTitle">
        <span className="text-subTitle">企业单点登录</span>
      </Divider>

      <div className="rounded-2xl border border-gray-100 bg-[#FCFCFE] p-5">
        <div className="mb-4">
          <div className="text-sm font-semibold text-gray-900">
            浏览器跳转登录
          </div>
          <div className="mt-1 text-xs leading-5 text-gray-500">
            推荐普通用户使用。系统会跳转到企业身份提供方，完成认证后自动回到
            HiMarket。
          </div>
        </div>

        {providerLoadError && (
          <Alert
            showIcon
            type={hasProviders ? "warning" : "error"}
            className="mb-4 rounded-xl"
            message={providerLoadError}
          />
        )}

        {recommendedProviders.length > 0 && (
          <div className="flex flex-col gap-3">
            {recommendedProviders.map(provider => (
              <ProviderButton
                key={provider.provider}
                provider={provider}
                onClick={() => onProviderLogin(provider)}
                disabled={loading || trustedHeaderLoading}
              />
            ))}
          </div>
        )}

        {advancedCount > 0 && (
          <div className="mt-4">
            <Collapse
              ghost
              items={[
                {
                  key: "advanced-enterprise-login",
                  label: `查看高级或测试登录入口（${advancedCount}）`,
                  children: (
                    <div className="flex flex-col gap-3 pt-2">
                      <div className="rounded-xl border border-dashed border-gray-200 bg-white px-4 py-3 text-xs leading-5 text-gray-500">
                        下列入口主要用于特定企业接入、协议验证或代理环境联调。普通用户应优先使用上方推荐入口。
                      </div>
                      {advancedInteractiveProviders.map(provider => (
                        <ProviderButton
                          key={provider.provider}
                          provider={provider}
                          onClick={() => onProviderLogin(provider)}
                          disabled={loading || trustedHeaderLoading}
                          description="高级协议入口，适用于兼容性验证或特定企业接入场景"
                        />
                      ))}
                      {trustedHeaderProviders.map(provider => (
                        <ProviderButton
                          key={provider.provider}
                          provider={provider}
                          onClick={() => onTrustedHeaderLogin(provider)}
                          disabled={loading}
                          loading={trustedHeaderLoading}
                          description="仅适用于受信反向代理已注入身份头的企业网络环境"
                        />
                      ))}
                    </div>
                  ),
                },
              ]}
            />
          </div>
        )}
      </div>
    </>
  );
}
