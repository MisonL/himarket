const TOKEN_FIELDS = ["access_token", "id_token", "jwt", "token"] as const;

export function mergeOAuth2CallbackParams(search: string, hash: string) {
  const queryParams = new URLSearchParams(search);
  const normalizedHash = hash.startsWith("#") ? hash.slice(1) : hash;
  const hashParams = new URLSearchParams(normalizedHash);
  const merged = new Map<string, string>();

  queryParams.forEach((value, key) => merged.set(key, value));
  hashParams.forEach((value, key) => {
    if (!merged.has(key)) {
      merged.set(key, value);
    }
  });

  return merged;
}

export function resolveOAuth2CallbackJwt(params: Map<string, string>) {
  return (
    TOKEN_FIELDS.map(field => params.get(field)).find(Boolean) || undefined
  );
}
