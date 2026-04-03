const AUTH_RETURN_URL_KEY = "hm_auth_return_url";

function normalizeReturnUrl(returnUrl?: string | null) {
  if (!returnUrl) {
    return undefined;
  }

  return returnUrl.startsWith("/") ? returnUrl : `/${returnUrl}`;
}

export function getSearchReturnUrl(searchParams: URLSearchParams) {
  return normalizeReturnUrl(searchParams.get("returnUrl"));
}

export function getStoredReturnUrl() {
  return normalizeReturnUrl(sessionStorage.getItem(AUTH_RETURN_URL_KEY));
}

export function persistReturnUrl(returnUrl?: string | null) {
  const normalizedReturnUrl = normalizeReturnUrl(returnUrl);
  if (normalizedReturnUrl) {
    sessionStorage.setItem(AUTH_RETURN_URL_KEY, normalizedReturnUrl);
    return normalizedReturnUrl;
  }

  sessionStorage.removeItem(AUTH_RETURN_URL_KEY);
  return undefined;
}

export function persistReturnUrlFromSearch(searchParams: URLSearchParams) {
  return persistReturnUrl(getSearchReturnUrl(searchParams));
}

export function consumeStoredReturnUrl(fallback = "/") {
  const returnUrl = getStoredReturnUrl() || fallback;
  sessionStorage.removeItem(AUTH_RETURN_URL_KEY);
  return returnUrl;
}

export function consumeReturnUrl(
  searchParams: URLSearchParams,
  fallback = "/"
) {
  const returnUrl = getSearchReturnUrl(searchParams);
  const storedReturnUrl = getStoredReturnUrl();
  sessionStorage.removeItem(AUTH_RETURN_URL_KEY);
  return returnUrl || storedReturnUrl || fallback;
}

export function buildAuthRoute(path: string, returnUrl?: string | null) {
  const normalizedReturnUrl = normalizeReturnUrl(returnUrl);
  if (!normalizedReturnUrl) {
    return path;
  }

  const params = new URLSearchParams();
  params.set("returnUrl", normalizedReturnUrl);
  return `${path}?${params.toString()}`;
}

export function buildAuthRouteFromSearch(
  path: string,
  searchParams: URLSearchParams
) {
  return buildAuthRoute(path, getSearchReturnUrl(searchParams));
}

export function buildStoredAuthRoute(path: string) {
  return buildAuthRoute(path, getStoredReturnUrl());
}
