#!/usr/bin/env bash

curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local extra_header="${4:-}"
  local args=(-sS -w '\nHTTP_CODE:%{http_code}' -X "${method}" "${url}")
  args+=(-H 'Accept: application/json')
  if [[ -n "${extra_header}" ]]; then
    args+=(-H "${extra_header}")
  fi
  if [[ -n "${body}" ]]; then
    args+=(-H 'Content-Type: application/json' -d "${body}")
  fi
  local result
  result="$(curl "${args[@]}" 2>&1 || true)"
  local http_code
  http_code="$(printf '%s\n' "${result}" | awk -F: '/^HTTP_CODE:/ {print $2}' | tail -n 1)"
  local resp
  resp="$(printf '%s\n' "${result}" | sed '/^HTTP_CODE:/d')"
  echo "${http_code}"$'\n'"${resp}"
}

node_generate_jwks() {
  local out_dir="$1"
  node - "${out_dir}" <<'NODE'
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });
const privatePath = path.join(outDir, 'private.jwk.json');
const jwksPath = path.join(outDir, 'jwks.json');
if (fs.existsSync(privatePath) && fs.existsSync(jwksPath)) {
  process.exit(0);
}
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const pubJwk = publicKey.export({ format: 'jwk' });
const privJwk = privateKey.export({ format: 'jwk' });
const kid = 'himarket-jwt-bearer';
pubJwk.kid = kid;
privJwk.kid = kid;
fs.writeFileSync(privatePath, JSON.stringify(privJwk, null, 2));
fs.writeFileSync(jwksPath, JSON.stringify({ keys: [pubJwk] }, null, 2));
NODE
}

node_mint_jwt() {
  local private_jwk_path="$1"
  local issuer="$2"
  local audience="$3"
  local subject="$4"
  local name="$5"
  local email="$6"
  node - "${private_jwk_path}" "${issuer}" "${audience}" "${subject}" "${name}" "${email}" <<'NODE'
const fs = require('fs');
const crypto = require('crypto');
function b64url(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input);
  return buf.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
const [privateJwkPath, issuer, audience, subject, name, email] = process.argv.slice(2);
const privateJwk = JSON.parse(fs.readFileSync(privateJwkPath, 'utf8'));
const key = crypto.createPrivateKey({ key: privateJwk, format: 'jwk' });
const now = Math.floor(Date.now() / 1000);
const header = { alg: 'RS256', typ: 'JWT', kid: privateJwk.kid };
const payload = { iss: issuer, sub: subject, aud: audience, iat: now, exp: now + 300, name, email };
const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
const signature = crypto.sign('RSA-SHA256', Buffer.from(signingInput), key);
console.log(`${signingInput}.${b64url(signature)}`);
NODE
}

extract_header_value() {
  local headers="$1"
  local name="$2"
  local value
  value="$(echo "${headers}" | tr -d '\r' | awk -v n="${name}" 'BEGIN{IGNORECASE=1} $1 ~ n":" {sub(/^[^:]+:[[:space:]]*/, "", $0); print $0; exit}')"
  printf '%s\n' "${value//&amp;/&}"
}

url_decode() {
  node -e 'console.log(decodeURIComponent(process.argv[1]))' "$1"
}

base64_encode() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

parse_query_param() {
  local url="$1"
  local key="$2"
  node -e 'const u = new URL(process.argv[1]); console.log(u.searchParams.get(process.argv[2]) || "");' "$url" "$key"
}

request_cas_mfa_token() {
  local service="$1"
  local username="$2"
  local password="$3"
  local credential
  credential="$(base64_encode "${username}:${password}")"
  curl -fsS -G "http://localhost:${CAS_HTTP_PORT:-8083}/cas/actuator/mfaSimple" \
    -H "Credential: ${credential}" \
    --data-urlencode "service=${service}" \
    | jq -r '.id'
}

issue_cas_rest_service_ticket() {
  local service="$1"
  local username="$2"
  local password="$3"
  local sotp="${4:-}"
  local headers
  headers="$(mktemp)"
  local args=(-sS -D "${headers}" -o /dev/null -X POST "http://localhost:${CAS_HTTP_PORT:-8083}/cas/v1/tickets" --data-urlencode "username=${username}" --data-urlencode "password=${password}")
  if [[ -n "${sotp}" ]]; then
    args+=(--data-urlencode "sotp=${sotp}")
  fi
  curl "${args[@]}" >/dev/null
  local tgt_location
  tgt_location="$(extract_header_value "$(cat "${headers}")" "Location")"
  if [[ -z "${tgt_location}" ]]; then
    err "missing TGT location from CAS REST login"
    cat "${headers}" >&2
    exit 1
  fi
  local service_ticket
  service_ticket="$(curl -fsS -X POST "${tgt_location}" --data-urlencode "service=${service}")"
  if [[ -z "${service_ticket}" || "${service_ticket}" != ST-* ]]; then
    err "missing service ticket from CAS REST login"
    printf '%s\n' "${service_ticket}" >&2
    exit 1
  fi
  printf '%s\n' "${service_ticket}"
}

build_logout_request() {
  local session_index="$1"
  printf '%s' "<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"><samlp:SessionIndex>${session_index}</samlp:SessionIndex></samlp:LogoutRequest>"
}

trigger_front_channel_logout() {
  local name="$1"
  local service_url="$2"
  local session_index="$3"
  local logout_request
  logout_request="$(base64_encode "$(build_logout_request "${session_index}")")"
  local response
  response="$(curl -fsS -G --data-urlencode "logoutRequest=${logout_request}" --data-urlencode "callback=logoutCallback" "${service_url}")"
  if [[ "${response}" != 'logoutCallback({"status":"ok"});' ]]; then
    err "${name} front-channel logout response mismatch"
    printf '%s\n' "${response}" >&2
    exit 1
  fi
}

exchange_code_for_token() {
  local name="$1"
  local url="$2"
  local code="$3"
  local request_body
  request_body="$(jq -nc --arg code "${code}" '{code:$code}')"
  local exchange_resp
  exchange_resp="$(curl_json POST "${url}" "${request_body}")"
  local exchange_code
  exchange_code="$(echo "${exchange_resp}" | head -n 1)"
  if [[ "${exchange_code}" != 200 ]]; then
    err "${name} exchange failed: http=${exchange_code}"
    echo "${exchange_resp}" | tail -n +2 >&2
    exit 1
  fi
  local exchange_body
  exchange_body="$(echo "${exchange_resp}" | tail -n +2)"
  local token
  token="$(echo "${exchange_body}" | jq -r '.data.access_token')"
  if [[ -z "${token}" || "${token}" == "null" ]]; then
    err "${name} exchange did not return access_token"
    echo "${exchange_body}" >&2
    exit 1
  fi
  echo "${token}"
}

expect_auth_rejected() {
  local name="$1"
  local url="$2"
  local token="$3"
  local http_code
  http_code="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${token}" "${url}" || true)"
  if [[ "${http_code}" != "401" && "${http_code}" != "403" ]]; then
    err "${name} expected 401/403 but got ${http_code}"
    exit 1
  fi
}
