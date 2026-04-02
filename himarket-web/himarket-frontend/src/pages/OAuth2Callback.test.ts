import { describe, expect, it } from "vitest";
import {
  mergeOAuth2CallbackParams,
  resolveOAuth2CallbackJwt,
} from "../lib/oauth2Callback";

describe("OAuth2Callback parameter parsing", () => {
  it("merges query and hash callback parameters", () => {
    const params = mergeOAuth2CallbackParams(
      "?provider=cas-jwt&state=state-123",
      "#ticket=ST-123"
    );

    expect(params.get("provider")).toBe("cas-jwt");
    expect(params.get("state")).toBe("state-123");
    expect(params.get("ticket")).toBe("ST-123");
  });

  it("keeps query parameters when hash contains duplicated keys", () => {
    const params = mergeOAuth2CallbackParams(
      "?provider=query-provider&state=query-state",
      "#provider=hash-provider&state=hash-state"
    );

    expect(params.get("provider")).toBe("query-provider");
    expect(params.get("state")).toBe("query-state");
  });

  it("resolves jwt token from fragment parameters", () => {
    const params = mergeOAuth2CallbackParams(
      "?provider=cas-jwt&state=state-123",
      "#access_token=jwt-from-fragment"
    );

    expect(resolveOAuth2CallbackJwt(params)).toBe("jwt-from-fragment");
  });
});
