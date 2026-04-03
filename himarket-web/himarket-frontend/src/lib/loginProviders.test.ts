import { describe, expect, it } from "vitest";
import { splitEnterpriseProviders, type LoginProvider } from "./loginProviders";

function createProvider(overrides: Partial<LoginProvider>): LoginProvider {
  return {
    provider: "provider",
    interactiveBrowserLogin: true,
    authType: "CAS",
    ...overrides,
  };
}

describe("splitEnterpriseProviders", () => {
  it("keeps technical CAS flows out of recommended providers", () => {
    const providers = [
      createProvider({ provider: "cas", displayName: "CAS" }),
      createProvider({ provider: "cas-saml1", displayName: "CAS SAML1" }),
      createProvider({ provider: "cas-mfa", displayName: "CAS MFA" }),
      createProvider({
        provider: "trusted-header",
        displayName: "Trusted Header",
        interactiveBrowserLogin: false,
        trustedHeaderLogin: true,
        authType: "OAUTH2",
      }),
    ];

    const result = splitEnterpriseProviders(providers);

    expect(
      result.recommendedProviders.map(provider => provider.provider)
    ).toEqual(["cas"]);
    expect(
      result.advancedInteractiveProviders.map(provider => provider.provider)
    ).toEqual(["cas-mfa", "cas-saml1"]);
    expect(
      result.trustedHeaderProviders.map(provider => provider.provider)
    ).toEqual(["trusted-header"]);
  });
});
