#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${DOCKER_DIR}/../.." && pwd)"
DATA_DIR="${DOCKER_DIR}/data"
ENV_FILE="${DATA_DIR}/.env"

log() { echo "[auth-harness $(date +'%H:%M:%S')] $*"; }
err() { echo "[auth-harness][ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "missing command: $1"; exit 1; }
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
  echo "${headers}" | tr -d '\r' | awk -v n="${name}" 'BEGIN{IGNORECASE=1} $1 ~ n":" {sub(/^[^:]+:[[:space:]]*/, "", $0); print $0; exit}'
}

url_decode() {
  node -e 'console.log(decodeURIComponent(process.argv[1]))' "$1"
}

parse_query_param() {
  local url="$1"
  local key="$2"
  node -e '
    const u = new URL(process.argv[1]);
    console.log(u.searchParams.get(process.argv[2]) || "");
  ' "$url" "$key"
}

main() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd node

  if ! docker compose version >/dev/null 2>&1; then
    err "docker compose is not available"
    exit 1
  fi

  mkdir -p "${DATA_DIR}"
  if [[ ! -f "${ENV_FILE}" ]]; then
    : > "${ENV_FILE}"
  fi

  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    . "${ENV_FILE}"
    set +a
  fi

  local admin_user="${ADMIN_USERNAME:-admin}"
  local admin_pass="${ADMIN_PASSWORD:-admin}"
  local portal_name="${PORTAL_NAME:-demo-portal}"
  local frontend_redirect_url="${FRONTEND_REDIRECT_URL:-http://localhost:${HIMARKET_FRONTEND_PORT:-5173}}"
  local cas_http_port="${CAS_HTTP_PORT:-8083}"
  local cas_ready_timeout="${CAS_READY_TIMEOUT:-660}"
  local skip_build="${SKIP_BUILD:-0}"
  local skip_docker_up="${SKIP_DOCKER_UP:-0}"

  if [[ "${skip_build}" != "1" ]]; then
    log "build himarket server jar"
    local java_home="${JAVA_HOME:-}"
    if [[ -z "${java_home}" && -x "/usr/libexec/java_home" ]]; then
      java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
    fi
    if [[ -z "${java_home}" ]]; then
      err "JAVA_HOME is not set and java_home is not available"
      exit 1
    fi

    (cd "${REPO_DIR}" && JAVA_HOME="${java_home}" mvn -pl himarket-bootstrap -am package -DskipTests)
  else
    log "skip build himarket server jar"
  fi

  log "prepare jwks files"
  local jwk_dir="${DATA_DIR}/jwt-bearer"
  node_generate_jwks "${jwk_dir}"

  if [[ "${skip_docker_up}" != "1" ]]; then
    log "start docker services"
    cd "${DOCKER_DIR}"
    export COMPOSE_PROFILES=builtin-mysql
    docker compose --env-file "${ENV_FILE}" -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.auth.yml up -d --build mysql himarket-server cas openldap jwks-server
  else
    log "skip docker compose up"
  fi

  wait_http_ok "himarket-server" "http://localhost:8081/portal/swagger-ui.html" 180
  wait_http_ok "cas" "http://localhost:${cas_http_port}/cas/login" "${cas_ready_timeout}"
  wait_http_ok "jwks-server" "http://localhost:${JWKS_HTTP_PORT:-8091}/jwks.json" 60
  wait_openldap_ready 120
  seed_openldap_users

  log "admin login"
  local login_result
  login_result="$(curl_json POST "http://localhost:8081/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
  local login_code
  login_code="$(echo "${login_result}" | head -n 1)"
  if [[ "${login_code}" != 200 ]]; then
    log "init admin account"
    local init_result
    init_result="$(curl_json POST "http://localhost:8081/admins/init" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    local init_code
    init_code="$(echo "${init_result}" | head -n 1)"
    if [[ "${init_code}" != 200 && "${init_code}" != 409 ]]; then
      err "admin init failed: http=${init_code}"
      echo "${init_result}" | tail -n +2 >&2
      exit 1
    fi

    log "admin login"
    login_result="$(curl_json POST "http://localhost:8081/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    login_code="$(echo "${login_result}" | head -n 1)"
    if [[ "${login_code}" != 200 ]]; then
      err "admin login failed: http=${login_code}"
      echo "${login_result}" | tail -n +2 >&2
      exit 1
    fi
  fi
  local login_body
  login_body="$(echo "${login_result}" | tail -n +2)"
  local admin_token
  admin_token="$(echo "${login_body}" | jq -r '.data.access_token')"
  if [[ -z "${admin_token}" || "${admin_token}" == "null" ]]; then
    err "cannot extract admin token"
    echo "${login_body}" >&2
    exit 1
  fi

  log "get or create portal: ${portal_name}"
  local portals_json
  portals_json="$(curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals?page=0&size=20")"
  local localhost_portal_id
  localhost_portal_id="$(
    echo "${portals_json}" | jq -r '.data.content[]?.portalId' | while read -r candidate_portal_id; do
      [[ -z "${candidate_portal_id}" ]] && continue
      if curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${candidate_portal_id}" \
        | jq -e '.data.portalDomainConfig[]? | select(.domain=="localhost")' >/dev/null; then
        echo "${candidate_portal_id}"
        break
      fi
    done
  )"
  local portal_id
  if [[ -n "${localhost_portal_id}" && "${localhost_portal_id}" != "null" ]]; then
    portal_id="${localhost_portal_id}"
  else
    portal_id="$(echo "${portals_json}" | jq -r --arg name "${portal_name}" '.data.content[]? | select(.name==$name) | .portalId' | head -n 1)"
  fi
  if [[ -z "${portal_id}" || "${portal_id}" == "null" ]]; then
    local create_resp
    create_resp="$(curl_json POST "http://localhost:8081/portals" "{\"name\":\"${portal_name}\"}" "Authorization: Bearer ${admin_token}")"
    local create_code
    create_code="$(echo "${create_resp}" | head -n 1)"
    if [[ "${create_code}" != 200 ]]; then
      err "create portal failed: http=${create_code}"
      echo "${create_resp}" | tail -n +2 >&2
      exit 1
    fi
    local create_body
    create_body="$(echo "${create_resp}" | tail -n +2)"
    portal_id="$(echo "${create_body}" | jq -r '.data.portalId')"
  fi
  if [[ -z "${portal_id}" || "${portal_id}" == "null" ]]; then
    err "cannot resolve portalId"
    exit 1
  fi

  if [[ -z "${localhost_portal_id}" || "${localhost_portal_id}" == "null" ]]; then
    log "bind domain localhost to portal: ${portal_id}"
    local bind_body
    bind_body='{"domain":"localhost","type":"CUSTOM","protocol":"HTTP"}'
    local bind_resp
    bind_resp="$(curl_json POST "http://localhost:8081/portals/${portal_id}/domains" "${bind_body}" "Authorization: Bearer ${admin_token}")"
    local bind_code
    bind_code="$(echo "${bind_resp}" | head -n 1)"
    if [[ "${bind_code}" != 200 && "${bind_code}" != 409 ]]; then
      local bind_body_resp
      bind_body_resp="$(echo "${bind_resp}" | tail -n +2)"
      if ! echo "${bind_body_resp}" | grep -q "Duplicate entry 'localhost'"; then
        err "bind domain failed: http=${bind_code}"
        echo "${bind_body_resp}" >&2
        exit 1
      fi
    fi
  else
    log "reuse existing localhost-bound portal: ${portal_id}"
  fi

  log "update portal auth configs"
  local portal_detail
  portal_detail="$(curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}")"
  local new_setting
  new_setting="$(echo "${portal_detail}" | jq \
    --arg frontendRedirectUrl "${frontend_redirect_url}" \
    --arg casServerUrl "http://localhost:${cas_http_port}/cas" \
    '
    .data.portalSettingConfig
    | .frontendRedirectUrl = $frontendRedirectUrl
    | .casConfigs = [
        {
          "provider": "cas",
          "name": "CAS",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        }
      ]
    | .ldapConfigs = [
        {
          "provider": "ldap",
          "name": "LDAP",
          "enabled": true,
          "serverUrl": "ldap://openldap:389",
          "baseDn": "ou=people,dc=example,dc=org",
          "bindDn": "cn=admin,dc=example,dc=org",
          "bindPassword": "admin",
          "userSearchFilter": "(uid={0})",
          "identityMapping": { "userIdField": "uid", "userNameField": "cn", "emailField": "mail" }
        }
      ]
    | .oauth2Configs = [
        {
          "provider": "jwt-bearer",
          "name": "JWT Bearer",
          "enabled": true,
          "grantType": "JWT_BEARER",
          "jwtBearerConfig": {
            "issuer": "http://jwks-server/issuer",
            "jwkSetUri": "http://jwks-server/jwks.json",
            "audiences": ["himarket-api"]
          },
          "identityMapping": { "userIdField": "sub", "userNameField": "name", "emailField": "email" }
        }
      ]
    ')"
  local update_payload
  update_payload="$(jq -n --argjson setting "${new_setting}" '{ portalSettingConfig: $setting }')"
  local update_resp
  update_resp="$(curl_json PUT "http://localhost:8081/portals/${portal_id}" "${update_payload}" "Authorization: Bearer ${admin_token}")"
  local update_code
  update_code="$(echo "${update_resp}" | head -n 1)"
  if [[ "${update_code}" != 200 ]]; then
    err "update portal failed: http=${update_code}"
    echo "${update_resp}" | tail -n +2 >&2
    exit 1
  fi

  log "verify developer cas providers"
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null

  log "developer cas authorize (capture state cookie and service url)"
  local cas_headers
  local cookie_jar
  cookie_jar="$(mktemp)"
  cas_headers="$(mktemp)"
  curl -sS -D "${cas_headers}" -o /dev/null -c "${cookie_jar}" "http://localhost:8081/developers/cas/authorize?provider=cas" || true
  local redirect
  redirect="$(extract_header_value "$(cat "${cas_headers}")" "Location")"
  if [[ -z "${redirect}" ]]; then
    err "missing redirect location from cas authorize"
    cat "${cas_headers}" >&2
    exit 1
  fi
  local encoded_service
  encoded_service="$(parse_query_param "${redirect}" "service")"
  if [[ -z "${encoded_service}" ]]; then
    err "missing service param in redirect"
    echo "${redirect}" >&2
    exit 1
  fi
  local service_url
  service_url="$(url_decode "${encoded_service}")"
  local state
  state="$(parse_query_param "${service_url}" "state")"
  if [[ -z "${state}" ]]; then
    err "missing state param in service url"
    echo "${service_url}" >&2
    exit 1
  fi

  log "cas rest: request tgt"
  local tgt_headers
  tgt_headers="$(mktemp)"
  curl -sS -D "${tgt_headers}" -o /dev/null \
    -X POST "http://localhost:${cas_http_port}/cas/v1/tickets" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice"
  local tgt_location
  tgt_location="$(extract_header_value "$(cat "${tgt_headers}")" "Location")"
  if [[ -z "${tgt_location}" ]]; then
    err "failed to create tgt via cas rest"
    cat "${tgt_headers}" >&2
    exit 1
  fi

  log "cas rest: request service ticket"
  local st
  st="$(curl -fsS -X POST "${tgt_location}" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode "service=${service_url}")"
  if [[ -z "${st}" ]]; then
    err "failed to get service ticket"
    exit 1
  fi

  log "himarket cas callback"
  local cas_cb
  cas_cb="$(curl -fsS -b "${cookie_jar}" -G "http://localhost:8081/developers/cas/callback" --data-urlencode "ticket=${st}" --data-urlencode "state=${state}")"
  echo "${cas_cb}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "verify ldap login (developer)"
  curl -fsS "http://localhost:8081/developers/ldap/providers" | jq -e '.data[]? | select(.provider=="ldap")' >/dev/null
  local ldap_login
  for _ in {1..10}; do
    if ldap_login="$(curl -fsS -X POST "http://localhost:8081/developers/ldap/login" -H 'Content-Type: application/json' -d '{"provider":"ldap","username":"alice","password":"alice"}')"; then
      break
    fi
    sleep 2
  done
  if [[ -z "${ldap_login:-}" ]]; then
    err "developer ldap login failed"
    exit 1
  fi
  echo "${ldap_login}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "verify oauth2 jwt-bearer token exchange (standard jwks)"
  local private_jwk="${jwk_dir}/private.jwk.json"
  local assertion
  assertion="$(node_mint_jwt "${private_jwk}" "http://jwks-server/issuer" "himarket-api" "alice" "Alice" "alice@example.org")"
  local oauth2_resp
  oauth2_resp="$(curl -fsS -X POST "http://localhost:8081/developers/oauth2/token" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    --data-urlencode "assertion=${assertion}")"
  echo "${oauth2_resp}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "verify admin cas providers"
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null

  log "admin cas authorize"
  local admin_cookie
  local admin_headers
  admin_cookie="$(mktemp)"
  admin_headers="$(mktemp)"
  curl -sS -D "${admin_headers}" -o /dev/null -c "${admin_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas" || true
  local admin_redirect
  admin_redirect="$(extract_header_value "$(cat "${admin_headers}")" "Location")"
  if [[ -z "${admin_redirect}" ]]; then
    err "missing redirect location from admin cas authorize"
    cat "${admin_headers}" >&2
    exit 1
  fi
  local admin_service_encoded
  admin_service_encoded="$(parse_query_param "${admin_redirect}" "service")"
  local admin_service_url
  admin_service_url="$(url_decode "${admin_service_encoded}")"
  local admin_state
  admin_state="$(parse_query_param "${admin_service_url}" "state")"

  log "cas rest: request tgt for admin"
  local tgt_admin_headers
  tgt_admin_headers="$(mktemp)"
  curl -sS -D "${tgt_admin_headers}" -o /dev/null \
    -X POST "http://localhost:${cas_http_port}/cas/v1/tickets" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin"
  local tgt_admin_location
  tgt_admin_location="$(extract_header_value "$(cat "${tgt_admin_headers}")" "Location")"
  if [[ -z "${tgt_admin_location}" ]]; then
    err "failed to create admin tgt via cas rest"
    cat "${tgt_admin_headers}" >&2
    exit 1
  fi
  local st_admin
  st_admin="$(curl -fsS -X POST "${tgt_admin_location}" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode "service=${admin_service_url}")"
  if [[ -z "${st_admin}" ]]; then
    err "failed to get admin service ticket"
    exit 1
  fi

  log "himarket admin cas callback"
  local admin_cb
  admin_cb="$(curl -fsS -b "${admin_cookie}" -G "http://localhost:8081/admins/cas/callback" --data-urlencode "ticket=${st_admin}" --data-urlencode "state=${admin_state}")"
  echo "${admin_cb}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "verify admin ldap login"
  curl -fsS "http://localhost:8081/admins/ldap/providers" | jq -e '.data[]? | select(.provider=="ldap")' >/dev/null
  local admin_ldap
  for _ in {1..10}; do
    if admin_ldap="$(curl -fsS -X POST "http://localhost:8081/admins/ldap/login" -H 'Content-Type: application/json' -d '{"provider":"ldap","username":"admin","password":"admin"}')"; then
      break
    fi
    sleep 2
  done
  if [[ -z "${admin_ldap:-}" ]]; then
    err "admin ldap login failed"
    exit 1
  fi
  echo "${admin_ldap}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "all scenarios passed"
}

main "$@"
