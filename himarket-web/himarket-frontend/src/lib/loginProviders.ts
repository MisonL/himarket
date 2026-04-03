export type LoginProvider = {
  provider: string;
  name?: string;
  displayName?: string;
  sloEnabled?: boolean;
  interactiveBrowserLogin: boolean;
  trustedHeaderLogin?: boolean;
  authType: "OIDC" | "CAS" | "OAUTH2";
};

type EnterpriseProviderGroups = {
  recommendedProviders: LoginProvider[];
  advancedInteractiveProviders: LoginProvider[];
  trustedHeaderProviders: LoginProvider[];
};

const TECHNICAL_PROVIDER_PATTERN =
  /\b(jwt|saml\d*|ticket|delegated|mfa|validate|cas1|cas2|header|proxy)\b/i;

export function getProviderLabel(provider: LoginProvider) {
  return provider.displayName || provider.name || provider.provider;
}

export function getProviderTypeLabel(provider: LoginProvider) {
  if (provider.trustedHeaderLogin) {
    return "Trusted Header";
  }

  if (provider.authType === "OIDC") {
    return "OIDC";
  }

  if (provider.authType === "OAUTH2") {
    return "OAuth2";
  }

  return "CAS";
}

export function getProviderMonogram(provider: LoginProvider) {
  if (provider.trustedHeaderLogin) {
    return "SSO";
  }

  if (provider.authType === "OAUTH2") {
    return "O2";
  }

  return getProviderTypeLabel(provider);
}

export function isTechnicalProvider(provider: LoginProvider) {
  return TECHNICAL_PROVIDER_PATTERN.test(
    [provider.provider, provider.name, provider.displayName]
      .filter(Boolean)
      .join(" ")
  );
}

export function sortProviders(a: LoginProvider, b: LoginProvider) {
  const getPriority = (provider: LoginProvider) => {
    if (provider.provider === "cas") {
      return 0;
    }

    if (provider.authType === "OIDC") {
      return 1;
    }

    if (provider.authType === "OAUTH2" && !isTechnicalProvider(provider)) {
      return 2;
    }

    if (!isTechnicalProvider(provider)) {
      return 3;
    }

    return 4;
  };

  return (
    getPriority(a) - getPriority(b) ||
    getProviderLabel(a).localeCompare(getProviderLabel(b), "zh-Hans-CN")
  );
}

export function splitEnterpriseProviders(
  providers: LoginProvider[]
): EnterpriseProviderGroups {
  const interactiveProviders = providers
    .filter(
      provider =>
        provider.interactiveBrowserLogin === true &&
        provider.trustedHeaderLogin !== true
    )
    .sort(sortProviders);
  const trustedHeaderProviders = providers
    .filter(provider => provider.trustedHeaderLogin === true)
    .sort(sortProviders);
  const primaryCandidates = interactiveProviders.filter(
    provider => !isTechnicalProvider(provider)
  );
  const recommendedProviders =
    primaryCandidates.length > 0
      ? primaryCandidates.slice(0, 3)
      : interactiveProviders.slice(0, 1);
  const advancedInteractiveProviders = interactiveProviders.filter(
    provider =>
      !recommendedProviders.some(
        recommendedProvider =>
          recommendedProvider.provider === provider.provider
      )
  );

  return {
    recommendedProviders,
    advancedInteractiveProviders,
    trustedHeaderProviders,
  };
}
