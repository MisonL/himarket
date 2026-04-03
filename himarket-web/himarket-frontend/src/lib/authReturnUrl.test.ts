import { beforeEach, describe, expect, it } from "vitest";
import {
  buildAuthRoute,
  buildAuthRouteFromSearch,
  consumeReturnUrl,
  getSearchReturnUrl,
  getStoredReturnUrl,
  persistReturnUrl,
} from "./authReturnUrl";

describe("authReturnUrl", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("reads decoded returnUrl from search params", () => {
    const searchParams = new URLSearchParams(
      "returnUrl=%2Fapis%3Ftab%3Ddetail"
    );

    expect(getSearchReturnUrl(searchParams)).toBe("/apis?tab=detail");
  });

  it("stores and consumes pending returnUrl for callback flows", () => {
    persistReturnUrl("/apis/demo");

    expect(getStoredReturnUrl()).toBe("/apis/demo");
    expect(consumeReturnUrl(new URLSearchParams(), "/")).toBe("/apis/demo");
    expect(getStoredReturnUrl()).toBeUndefined();
  });

  it("rebuilds auth routes while preserving returnUrl", () => {
    const searchParams = new URLSearchParams("returnUrl=%2Fmodels%2F1");

    expect(buildAuthRoute("/login", "/apis/demo")).toBe(
      "/login?returnUrl=%2Fapis%2Fdemo"
    );
    expect(buildAuthRouteFromSearch("/register", searchParams)).toBe(
      "/register?returnUrl=%2Fmodels%2F1"
    );
  });
});
