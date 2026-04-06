#!/usr/bin/env bash

phase_jwt_bearer() {
  log "verify oauth2 jwt-bearer token exchange (standard jwks)"
  local private_jwk="${jwk_dir}/private.jwk.json"
  local assertion
  assertion="$(node_mint_jwt "${private_jwk}" "http://jwks-server/issuer" "himarket-api" "alice" "Alice" "alice@example.org")"
  local oauth2_resp
  oauth2_resp="$(curl -fsS -X POST "${HIMARKET_BASE_URL}/developers/oauth2/token" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    --data-urlencode "assertion=${assertion}")"
  echo "${oauth2_resp}" | jq -e '.data.access_token | length > 0' >/dev/null
}
