export type LastAuthType = 'BUILTIN' | 'CAS';

const KEY_LAST_AUTH_TYPE = 'hm_last_auth_type';
const KEY_LAST_AUTH_PROVIDER = 'hm_last_auth_provider';
const KEY_LAST_AUTH_SLO_ENABLED = 'hm_last_auth_slo_enabled';

export interface LastAuthState {
  type: LastAuthType;
  provider?: string;
  sloEnabled?: boolean;
}

export function setLastAuthState(state: LastAuthState) {
  localStorage.setItem(KEY_LAST_AUTH_TYPE, state.type);

  if (state.provider) {
    localStorage.setItem(KEY_LAST_AUTH_PROVIDER, state.provider);
  } else {
    localStorage.removeItem(KEY_LAST_AUTH_PROVIDER);
  }

  if (typeof state.sloEnabled === 'boolean') {
    localStorage.setItem(KEY_LAST_AUTH_SLO_ENABLED, state.sloEnabled ? 'true' : 'false');
  } else {
    localStorage.removeItem(KEY_LAST_AUTH_SLO_ENABLED);
  }
}

export function getLastAuthState(): LastAuthState | null {
  const type = localStorage.getItem(KEY_LAST_AUTH_TYPE) as LastAuthType | null;
  if (!type) {
    return null;
  }

  const provider = localStorage.getItem(KEY_LAST_AUTH_PROVIDER) || undefined;
  const sloEnabledRaw = localStorage.getItem(KEY_LAST_AUTH_SLO_ENABLED);
  const sloEnabled = sloEnabledRaw == null ? undefined : sloEnabledRaw === 'true';

  return {
    type,
    provider,
    sloEnabled,
  };
}

export function clearLastAuthState() {
  localStorage.removeItem(KEY_LAST_AUTH_TYPE);
  localStorage.removeItem(KEY_LAST_AUTH_PROVIDER);
  localStorage.removeItem(KEY_LAST_AUTH_SLO_ENABLED);
}

