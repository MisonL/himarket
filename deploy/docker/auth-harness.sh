#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${DOCKER_DIR}/../.." && pwd)"
DATA_DIR="${DOCKER_DIR}/data"
ENV_FILE="${DATA_DIR}/.env"
CAS_MODULES_DIR="${DOCKER_DIR}/auth/cas/modules"
CAS_MODULES_LIB_DIR="${CAS_MODULES_DIR}/lib"
MOCK_OIDC_HOST="${MOCK_OIDC_HOST:-mock-oidc.local}"
MOCK_OIDC_PORT="${MOCK_OIDC_PORT:-8092}"

log() { echo "[auth-harness $(date +'%H:%M:%S')] $*"; }
err() { echo "[auth-harness][ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "missing command: $1"; exit 1; }
}

prepare_cas_modules() {
  local sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-saml-7.0.0.jar"
  local mfa_sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-simple-mfa-7.0.0.jar"
  local delegated_sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-pac4j-webflow-7.0.0.jar"
  if [[ -f "${sentinel}" && -f "${mfa_sentinel}" && -f "${delegated_sentinel}" ]]; then
    log "cas modules ready"
    return 0
  fi

  require_cmd mvn
  mkdir -p "${CAS_MODULES_LIB_DIR}"
  log "prepare cas modules"
  mvn -q -f "${CAS_MODULES_DIR}/pom.xml" dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="${CAS_MODULES_LIB_DIR}"
  if [[ ! -f "${sentinel}" || ! -f "${mfa_sentinel}" || ! -f "${delegated_sentinel}" ]]; then
    err "cas modules not prepared"
    exit 1
  fi
  log "cas modules prepared"
}

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

wait_http_ok() {
  local name="$1"
  local url="$2"
  local max_wait="${3:-120}"
  local elapsed=0
  local interval=2
  local probe_timeout="${HTTP_WAIT_PROBE_TIMEOUT:-5}"
  local start_ts
  start_ts="$(date +%s)"

  log "wait service: ${name} url=${url}"
  while true; do
    elapsed=$(( $(date +%s) - start_ts ))
    if (( elapsed >= max_wait )); then
      break
    fi
    if curl --max-time "${probe_timeout}" -fsS "${url}" >/dev/null 2>&1; then
      log "service ready: ${name}"
      return 0
    fi
    sleep "${interval}"
  done

  err "service not ready: ${name}"
  return 1
}

wait_openldap_ready() {
  local max_wait="${1:-120}"
  local elapsed=0
  local interval=2
  local start_ts
  start_ts="$(date +%s)"
  local admin_dn="cn=admin,dc=example,dc=org"
  local admin_password="${LDAP_ADMIN_PASSWORD:-admin}"

  log "wait service: openldap"
  while true; do
    elapsed=$(( $(date +%s) - start_ts ))
    if (( elapsed >= max_wait )); then
      break
    fi
    if docker exec himarket-openldap ldapwhoami -x -D "${admin_dn}" -w "${admin_password}" >/dev/null 2>&1; then
      log "service ready: openldap"
      return 0
    fi
    sleep "${interval}"
  done

  err "service not ready: openldap"
  return 1
}

wait_cas_ready() {
  local max_wait="${1:-900}"
  local interval=5
  local cas_http_port="${CAS_HTTP_PORT:-8083}"
  local cas_login_url="http://localhost:${cas_http_port}/cas/login"
  local probe_timeout="${CAS_HTTP_PROBE_TIMEOUT:-15}"
  local expected_services=0
  local start_ts
  start_ts="$(date +%s)"
  if docker ps --format '{{.Names}}' | grep -qx 'himarket-cas'; then
    expected_services="$(find "${DOCKER_DIR}/auth/cas/services" -maxdepth 1 -name '*.json' | wc -l | tr -d ' ')"
  fi

  log "wait service: cas url=${cas_login_url}"
  while true; do
    local elapsed
    elapsed=$(( $(date +%s) - start_ts ))
    if (( elapsed >= max_wait )); then
      break
    fi
    if curl --max-time "${probe_timeout}" -fsS "${cas_login_url}" >/dev/null 2>&1 && cas_runtime_ready "${expected_services}"; then
      log "service ready: cas"
      return 0
    fi
    sleep "${interval}"
  done

  err "service not ready: cas"
  return 1
}

cas_runtime_ready() {
  local expected_services="${1:-0}"
  local cas_logs
  local started_at
  if ! docker ps --format '{{.Names}}' | grep -qx 'himarket-cas'; then
    return 0
  fi
  started_at="$(docker inspect -f '{{.State.StartedAt}}' himarket-cas 2>/dev/null || true)"
  cas_logs="$(docker logs --since "${started_at}" himarket-cas 2>&1 || true)"
  if ! grep -q "Ready to process requests" <<<"${cas_logs}"; then
    return 1
  fi
  if (( expected_services > 0 )) && ! grep -q "Loaded \\[${expected_services}\\] service(s)" <<<"${cas_logs}"; then
    return 1
  fi
  return 0
}

ensure_cas_modules_loaded() {
  if ! docker ps --format '{{.Names}}' | grep -qx 'himarket-cas'; then
    return 0
  fi
  if docker exec himarket-cas sh -lc 'ls /tmp/cas-exploded/WEB-INF/lib | grep -q "cas-server-support-pac4j-webflow-7.0.0.jar" && ls /tmp/cas-exploded/WEB-INF/lib | grep -q "pac4j-oidc-6.0.0.jar" && ls /tmp/cas-exploded/WEB-INF/lib | grep -q "cas-server-support-simple-mfa-7.0.0.jar"' >/dev/null 2>&1; then
    log "cas exploded modules already loaded"
    return 0
  fi
  log "sync cas modules into exploded webapp"
  docker exec himarket-cas sh -lc 'cp -f /docker/cas/modules/*.jar /tmp/cas-exploded/WEB-INF/lib/'
  docker restart himarket-cas >/dev/null
}

seed_openldap_users() {
  local ldif_path="${DOCKER_DIR}/auth/ldap/ldif/50-users.ldif"
  local admin_dn="cn=admin,dc=example,dc=org"
  local admin_password="${LDAP_ADMIN_PASSWORD:-admin}"

  log "seed openldap users"
  if docker exec himarket-openldap ldapsearch -x -D "${admin_dn}" -w "${admin_password}" \
    -b "ou=people,dc=example,dc=org" "(uid=alice)" dn 2>/dev/null | grep -q "^dn: uid=alice,"; then
    log "openldap users already present"
    return 0
  fi

  local ldapadd_output
  if ! ldapadd_output="$(docker exec -i himarket-openldap ldapadd -x -D "${admin_dn}" -w "${admin_password}" 2>&1 < "${ldif_path}")"; then
    if ! printf '%s\n' "${ldapadd_output}" | grep -q "Already exists (68)"; then
      err "openldap user seed failed"
      printf '%s\n' "${ldapadd_output}" >&2
      exit 1
    fi
  fi
  log "openldap users loaded"
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
  return buf
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

const [privateJwkPath, issuer, audience, subject, name, email] = process.argv.slice(2);
const privateJwk = JSON.parse(fs.readFileSync(privateJwkPath, 'utf8'));
const key = crypto.createPrivateKey({ key: privateJwk, format: 'jwk' });

const now = Math.floor(Date.now() / 1000);
const header = { alg: 'RS256', typ: 'JWT', kid: privateJwk.kid };
const payload = {
  iss: issuer,
  sub: subject,
  aud: audience,
  iat: now,
  exp: now + 300,
  name,
  email
};

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
  node -e '
    const u = new URL(process.argv[1]);
    console.log(u.searchParams.get(process.argv[2]) || "");
  ' "$url" "$key"
}

request_cas_mfa_token() {
  local service="$1"
  local username="$2"
  local password="$3"
  local cas_http_port="${CAS_HTTP_PORT:-8083}"
  local credential
  credential="$(base64_encode "${username}:${password}")"
  curl -fsS -G "http://localhost:${cas_http_port}/cas/actuator/mfaSimple" \
    -H "Credential: ${credential}" \
    --data-urlencode "service=${service}" \
    | jq -r '.id'
}

issue_cas_rest_service_ticket() {
  local service="$1"
  local username="$2"
  local password="$3"
  local sotp="${4:-}"
  local cas_http_port="${CAS_HTTP_PORT:-8083}"
  local headers
  headers="$(mktemp)"
  local args=(
    -sS
    -D "${headers}"
    -o /dev/null
    -X POST "http://localhost:${cas_http_port}/cas/v1/tickets"
    --data-urlencode "username=${username}"
    --data-urlencode "password=${password}"
  )
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

extract_html_input_value() {
  local html="$1"
  local name="$2"
  printf '%s' "${html}" | node -e '
    const fs = require("fs");
    const html = fs.readFileSync(0, "utf8");
    const name = process.argv[1];
    const tagMatch = html.match(new RegExp(`<input[^>]*name=["\\x27]${name}["\\x27][^>]*>`, "i"));
    if (!tagMatch) {
      console.log("");
      process.exit(0);
    }
    const valueMatch = tagMatch[0].match(/value=["\x27]([^"\x27]*)["\x27]/i);
    console.log(valueMatch ? valueMatch[1] : "");
  ' "${name}"
}

extract_html_link_href() {
  local html="$1"
  local fragment="$2"
  printf '%s' "${html}" | node -e '
    const fs = require("fs");
    const html = fs.readFileSync(0, "utf8");
    const fragment = process.argv[1];
    const linkMatch = html.match(new RegExp(`<a[^>]*href=["\\x27]([^"\\x27]*${fragment.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&")}[^"\\x27]*)["\\x27][^>]*>`, "i"));
    console.log(linkMatch ? linkMatch[1] : "");
  ' "${fragment}"
}

curl_mock_oidc() {
  curl --resolve "${MOCK_OIDC_HOST}:${MOCK_OIDC_PORT}:127.0.0.1" "$@"
}

start_mock_oidc() {
  require_cmd node
  if docker ps -a --filter name='^/himarket-mock-oidc$' --format '{{.Names}}' | grep -qx 'himarket-mock-oidc'; then
    log "remove stale compose-managed mock oidc container"
    docker rm -f himarket-mock-oidc >/dev/null
  fi
  if curl -fsS "http://localhost:${MOCK_OIDC_PORT}/.well-known/openid-configuration" >/dev/null 2>&1; then
    log "mock oidc already available"
    return 0
  fi
  local log_file="${DATA_DIR}/mock-oidc.log"
  OIDC_ISSUER="http://${MOCK_OIDC_HOST}:${MOCK_OIDC_PORT}" \
  OIDC_PORT="${MOCK_OIDC_PORT}" \
  OIDC_CLIENT_ID="cas-delegated-client" \
  OIDC_CLIENT_SECRET="cas-delegated-secret" \
  OIDC_USER_ALICE_PASSWORD="alice" \
  OIDC_USER_ADMIN_PASSWORD="admin" \
    node "${DOCKER_DIR}/auth/oidc/mock-oidc-server.mjs" >"${log_file}" 2>&1 &
  MOCK_OIDC_PID=$!
}

stop_mock_oidc() {
  if [[ -n "${MOCK_OIDC_PID:-}" ]]; then
    kill "${MOCK_OIDC_PID}" >/dev/null 2>&1 || true
  fi
}

login_via_mock_oidc() {
  local authorize_url="$1"
  local cookie_jar="$2"
  local username="$3"
  local password="$4"
  local cas_http_port="${CAS_HTTP_PORT:-8083}"

  local authorize_headers
  authorize_headers="$(mktemp)"
  local authorize_html
  authorize_html="$(curl_mock_oidc -sS -D "${authorize_headers}" -b "${cookie_jar}" -c "${cookie_jar}" "${authorize_url}" || true)"
  local redirected_authorize_url
  redirected_authorize_url="$(extract_header_value "$(cat "${authorize_headers}")" "Location")"
  if [[ -n "${redirected_authorize_url}" ]]; then
    authorize_url="${redirected_authorize_url}"
    authorize_html="$(curl_mock_oidc -fsS -b "${cookie_jar}" -c "${cookie_jar}" "${authorize_url}")"
  fi

  local client_id
  local redirect_uri
  local response_type
  local scope
  local state
  local nonce
  client_id="$(extract_html_input_value "${authorize_html}" "client_id")"
  redirect_uri="$(extract_html_input_value "${authorize_html}" "redirect_uri")"
  response_type="$(extract_html_input_value "${authorize_html}" "response_type")"
  scope="$(extract_html_input_value "${authorize_html}" "scope")"
  state="$(extract_html_input_value "${authorize_html}" "state")"
  nonce="$(extract_html_input_value "${authorize_html}" "nonce")"
  if [[ -z "${client_id}" || -z "${redirect_uri}" ]]; then
    err "missing mock oidc authorize hidden fields"
    printf '%s\n' "${authorize_html}" >&2
    exit 1
  fi

  local action_url="${authorize_url%%\?*}"
  local submit_headers
  submit_headers="$(mktemp)"
  curl_mock_oidc -sS -D "${submit_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" \
    -X POST "${action_url}" \
    --data-urlencode "client_id=${client_id}" \
    --data-urlencode "redirect_uri=${redirect_uri}" \
    --data-urlencode "response_type=${response_type}" \
    --data-urlencode "scope=${scope}" \
    --data-urlencode "state=${state}" \
    --data-urlencode "nonce=${nonce}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" || true
  local callback_location
  callback_location="$(extract_header_value "$(cat "${submit_headers}")" "Location")"
  if [[ "${callback_location}" == https://cas.example.org:8443/* ]]; then
    callback_location="http://localhost:${cas_http_port}${callback_location#https://cas.example.org:8443}"
  fi
  printf '%s\n' "${callback_location}"
}

normalize_local_cas_url() {
  local url="$1"
  local cas_http_port="${CAS_HTTP_PORT:-8083}"
  if [[ "${url}" == https://cas.example.org:8443/* ]]; then
    printf 'http://localhost:%s%s\n' "${cas_http_port}" "${url#https://cas.example.org:8443}"
    return 0
  fi
  printf '%s\n' "${url}"
}

follow_redirect_until_ticket() {
  local current_url="$1"
  local cookie_jar="$2"
  local max_hops="${3:-5}"
  local hop=0
  while (( hop < max_hops )); do
    local redirect_headers
    redirect_headers="$(mktemp)"
    curl -sS -D "${redirect_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" "${current_url}" || true
    local next_url
    next_url="$(extract_header_value "$(cat "${redirect_headers}")" "Location")"
    if [[ -z "${next_url}" ]]; then
      printf '%s\n' "${current_url}"
      return 0
    fi
    next_url="$(normalize_local_cas_url "${next_url}")"
    if [[ "${next_url}" == *"ticket="* ]]; then
      printf '%s\n' "${next_url}"
      return 0
    fi
    current_url="${next_url}"
    hop=$((hop + 1))
  done
  printf '%s\n' "${current_url}"
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
  response="$(curl -fsS -G \
    --data-urlencode "logoutRequest=${logout_request}" \
    --data-urlencode "callback=logoutCallback" \
    "${service_url}")"
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

source "${SCRIPT_DIR}/lib/common.sh"
source "${SCRIPT_DIR}/lib/context.sh"
for phase_script in "${SCRIPT_DIR}"/phases/*.sh; do
  # shellcheck disable=SC1090
  source "${phase_script}"
done

has_selected_non_bootstrap_phases() {
  local phase
  for phase in "${SELECTED_PHASES[@]}"; do
    if [[ "${phase}" != "bootstrap" ]]; then
      return 0
    fi
  done
  return 1
}

main() {
  init_phase_selection
  bootstrap_runtime

  admin_user="${ADMIN_USER}"
  admin_pass="${ADMIN_PASS}"
  portal_name="${PORTAL_NAME}"
  frontend_redirect_url="${FRONTEND_REDIRECT_URL}"
  cas_http_port="${CAS_HTTP_PORT}"
  jwk_dir="${JWK_DIR}"

  if ! has_selected_non_bootstrap_phases; then
    log "all selected phases passed"
    return 0
  fi

  prepare_auth_phase_context

  local phase
  for phase in "${SELECTED_PHASES[@]}"; do
    if [[ "${phase}" == "bootstrap" ]]; then
      continue
    fi
    run_phase "${phase}"
  done

  log "all selected phases passed"
}

main "$@"
