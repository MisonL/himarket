#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${DOCKER_DIR}/../.." && pwd)"
DATA_DIR="${DOCKER_DIR}/data"
ENV_FILE="${DATA_DIR}/.env"
CAS_MODULES_DIR="${DOCKER_DIR}/auth/cas/modules"
CAS_MODULES_LIB_DIR="${CAS_MODULES_DIR}/lib"

log() { echo "[auth-harness $(date +'%H:%M:%S')] $*"; }
err() { echo "[auth-harness][ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "missing command: $1"; exit 1; }
}

prepare_cas_modules() {
  local sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-saml-7.0.0.jar"
  if [[ -f "${sentinel}" ]]; then
    log "cas modules ready"
    return 0
  fi

  require_cmd mvn
  mkdir -p "${CAS_MODULES_LIB_DIR}"
  log "prepare cas modules"
  mvn -q -f "${CAS_MODULES_DIR}/pom.xml" dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="${CAS_MODULES_LIB_DIR}"
  if [[ ! -f "${sentinel}" ]]; then
    err "cas saml module not prepared"
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
  local start_ts
  start_ts="$(date +%s)"

  log "wait service: cas url=${cas_login_url}"
  while true; do
    local elapsed
    elapsed=$(( $(date +%s) - start_ts ))
    if (( elapsed >= max_wait )); then
      break
    fi
    if curl --max-time 5 -fsS "${cas_login_url}" >/dev/null 2>&1; then
      log "service ready: cas"
      return 0
    fi
    sleep "${interval}"
  done

  err "service not ready: cas"
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

build_logout_request() {
  local session_index="$1"
  printf '%s' "<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"><samlp:SessionIndex>${session_index}</samlp:SessionIndex></samlp:LogoutRequest>"
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

main() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd node
  prepare_cas_modules

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
  local cas_ready_timeout="${CAS_READY_TIMEOUT:-900}"
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

    require_cmd npm
    log "build himarket frontend dist"
    (cd "${REPO_DIR}/himarket-web/himarket-frontend" && npm run build)
    log "build himarket admin dist"
    (cd "${REPO_DIR}/himarket-web/himarket-admin" && npm run build)
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
    docker compose --env-file "${ENV_FILE}" -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.auth.yml up -d --build mysql redis-stack-server himarket-server himarket-frontend himarket-admin cas openldap jwks-server
  else
    log "skip docker compose up"
  fi

  wait_http_ok "himarket-server" "http://localhost:8081/portal/swagger-ui.html" 180
  wait_http_ok "himarket-frontend" "http://localhost:${HIMARKET_FRONTEND_PORT:-5173}/login" 120
  wait_http_ok "himarket-admin" "http://localhost:${HIMARKET_ADMIN_PORT:-5174}/login" 120
  wait_cas_ready "${cas_ready_timeout}"
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
          "validation": {
            "protocolVersion": "CAS3",
            "responseFormat": "JSON"
          },
          "proxy": {
            "enabled": true,
            "callbackPath": "http://himarket-server:8080/developers/cas/proxy-callback",
            "proxyEndpoint": "http://cas:8080/cas/proxy"
          },
          "serviceDefinition": {
            "evaluationOrder": 7,
            "responseType": "POST",
            "logoutType": "FRONT_CHANNEL",
            "logoutUrl": "http://localhost:5173/login"
          },
          "attributeRelease": {
            "allowedAttributes": ["user", "mail", "displayName"]
          },
          "multifactorPolicy": {
            "providers": ["mfa-duo"],
            "bypassEnabled": true,
            "forceExecution": true
          },
          "accessStrategy": {
            "enabled": true,
            "ssoEnabled": true,
            "delegatedAuthenticationPolicy": {
              "allowedProviders": ["GithubClient"],
              "permitUndefined": false,
              "exclusive": true
            }
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-saml1",
          "name": "CAS SAML1",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/samlValidate",
          "validation": {
            "protocolVersion": "SAML1",
            "responseFormat": "XML"
          },
          "serviceDefinition": {
            "evaluationOrder": 11,
            "responseType": "POST",
            "logoutType": "FRONT_CHANNEL",
            "logoutUrl": "http://localhost:5173/login"
          },
          "attributeRelease": {
            "allowedAttributes": ["user", "mail"]
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas1",
          "name": "CAS1",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/validate",
          "validation": {
            "protocolVersion": "CAS1",
            "responseFormat": "XML"
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas2",
          "name": "CAS2",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/serviceValidate",
          "validation": {
            "protocolVersion": "CAS2",
            "responseFormat": "XML"
          },
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
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-saml1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas2")' >/dev/null

  log "preview portal cas service definition"
  local portal_cas_service_definition
  portal_cas_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas/service-definition")"
  echo "${portal_cas_service_definition}" | jq -e '
    .data.serviceId? // .serviceId
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.proxyPolicy // .proxyPolicy).pattern // "") | contains("/developers/cas/proxy-callback")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 7
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.attributeReleasePolicy // .attributeReleasePolicy).allowedAttributes // [])[1][]? | select(.=="displayName")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="GithubClient")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).permitUndefined == false
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).exclusive == true
  ' >/dev/null

  log "preview portal cas saml1 service definition"
  local portal_cas_saml1_service_definition
  portal_cas_saml1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-saml1/service-definition")"
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 11
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="SAML1")
  ' >/dev/null

  log "preview portal cas1 service definition"
  local portal_cas1_service_definition
  portal_cas1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas1/service-definition")"
  echo "${portal_cas1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS10")
  ' >/dev/null

  log "preview portal cas2 service definition"
  local portal_cas2_service_definition
  portal_cas2_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas2/service-definition")"
  echo "${portal_cas2_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS20")
  ' >/dev/null

  log "developer cas authorize flag passthrough"
  local developer_flag_headers
  developer_flag_headers="$(mktemp)"
  curl -sS -D "${developer_flag_headers}" -o /dev/null \
    "http://localhost:8081/developers/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true" || true
  local developer_flag_redirect
  developer_flag_redirect="$(extract_header_value "$(cat "${developer_flag_headers}")" "Location")"
  if [[ -z "${developer_flag_redirect}" ]]; then
    err "missing redirect location from developer cas flag authorize"
    cat "${developer_flag_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${developer_flag_redirect}" "gateway")" == "true" ]] || {
    err "developer cas gateway flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${developer_flag_redirect}" "warn")" == "true" ]] || {
    err "developer cas warn flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${developer_flag_redirect}" "rememberMe")" == "true" ]] || {
    err "developer cas rememberMe flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }

  log "admin cas authorize flag passthrough"
  local admin_flag_headers
  admin_flag_headers="$(mktemp)"
  curl -sS -D "${admin_flag_headers}" -o /dev/null \
    "http://localhost:8081/admins/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true" || true
  local admin_flag_redirect
  admin_flag_redirect="$(extract_header_value "$(cat "${admin_flag_headers}")" "Location")"
  if [[ -z "${admin_flag_redirect}" ]]; then
    err "missing redirect location from admin cas flag authorize"
    cat "${admin_flag_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${admin_flag_redirect}" "gateway")" == "true" ]] || {
    err "admin cas gateway flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${admin_flag_redirect}" "warn")" == "true" ]] || {
    err "admin cas warn flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${admin_flag_redirect}" "rememberMe")" == "true" ]] || {
    err "admin cas rememberMe flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }

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

  log "cas login form (developer)"
  local developer_login_html
  developer_login_html="$(curl -fsS -b "${cookie_jar}" -c "${cookie_jar}" "${redirect}")"
  local developer_execution
  developer_execution="$(extract_html_input_value "${developer_login_html}" "execution")"
  if [[ -z "${developer_execution}" ]]; then
    err "missing execution token from developer cas login page"
    exit 1
  fi
  local developer_login_headers
  developer_login_headers="$(mktemp)"
  curl -sS -D "${developer_login_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" \
    -X POST "${redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "execution=${developer_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_service_redirect
  developer_service_redirect="$(extract_header_value "$(cat "${developer_login_headers}")" "Location")"
  if [[ -z "${developer_service_redirect}" ]]; then
    err "missing callback redirect from developer cas login submit"
    cat "${developer_login_headers}" >&2
    exit 1
  fi

  log "himarket cas callback via public service url"
  local cas_callback_headers
  cas_callback_headers="$(mktemp)"
  curl -sS -D "${cas_callback_headers}" -o /dev/null -b "${cookie_jar}" "${developer_service_redirect}" || true
  local frontend_cas_redirect
  frontend_cas_redirect="$(extract_header_value "$(cat "${cas_callback_headers}")" "Location")"
  if [[ -z "${frontend_cas_redirect}" ]]; then
    err "missing frontend redirect from developer cas callback"
    cat "${cas_callback_headers}" >&2
    exit 1
  fi
  local developer_cas_code
  developer_cas_code="$(parse_query_param "${frontend_cas_redirect}" "code")"
  if [[ -z "${developer_cas_code}" ]]; then
    err "missing developer cas code in redirect"
    echo "${frontend_cas_redirect}" >&2
    exit 1
  fi
  local developer_cas_token
  developer_cas_token="$(exchange_code_for_token "developer cas" "http://localhost:8081/developers/cas/exchange" "${developer_cas_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas proxy ticket"
  curl -fsS -X POST "http://localhost:8081/developers/cas/proxy-ticket" \
    -H "Authorization: Bearer ${developer_cas_token}" \
    -H 'Content-Type: application/json' \
    -d '{"provider":"cas","targetService":"http://localhost:5173/proxy-target"}' \
    | jq -e '.data.proxyTicket | startswith("PT-")' >/dev/null

  log "developer cas back-channel logout"
  local developer_logout_request
  local developer_ticket
  developer_ticket="$(parse_query_param "${developer_service_redirect}" "ticket")"
  developer_logout_request="$(build_logout_request "${developer_ticket}")"
  curl -fsS -X POST "${service_url}" --data-urlencode "logoutRequest=${developer_logout_request}" >/dev/null
  expect_auth_rejected "developer cas revoked token" "http://localhost:8081/developers/profile" "${developer_cas_token}"

  log "developer cas saml1 authorize"
  local developer_saml_cookie
  local developer_saml_headers
  developer_saml_cookie="$(mktemp)"
  developer_saml_headers="$(mktemp)"
  curl -sS -D "${developer_saml_headers}" -o /dev/null -c "${developer_saml_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas-saml1" || true
  local developer_saml_redirect
  developer_saml_redirect="$(extract_header_value "$(cat "${developer_saml_headers}")" "Location")"
  if [[ -z "${developer_saml_redirect}" ]]; then
    err "missing redirect location from developer cas saml1 authorize"
    cat "${developer_saml_headers}" >&2
    exit 1
  fi
  local developer_saml_service_encoded
  developer_saml_service_encoded="$(parse_query_param "${developer_saml_redirect}" "service")"
  local developer_saml_service_url
  developer_saml_service_url="$(url_decode "${developer_saml_service_encoded}")"

  log "cas login form (developer saml1)"
  local developer_saml_login_html
  developer_saml_login_html="$(curl -fsS -b "${developer_saml_cookie}" -c "${developer_saml_cookie}" "${developer_saml_redirect}")"
  local developer_saml_execution
  developer_saml_execution="$(extract_html_input_value "${developer_saml_login_html}" "execution")"
  if [[ -z "${developer_saml_execution}" ]]; then
    err "missing execution token from developer cas saml1 login page"
    exit 1
  fi
  local developer_saml_login_headers
  developer_saml_login_headers="$(mktemp)"
  curl -sS -D "${developer_saml_login_headers}" -o /dev/null -b "${developer_saml_cookie}" -c "${developer_saml_cookie}" \
    -X POST "${developer_saml_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "execution=${developer_saml_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_saml_service_redirect
  developer_saml_service_redirect="$(extract_header_value "$(cat "${developer_saml_login_headers}")" "Location")"
  if [[ -z "${developer_saml_service_redirect}" ]]; then
    err "missing callback redirect from developer cas saml1 login submit"
    cat "${developer_saml_login_headers}" >&2
    exit 1
  fi

  log "himarket cas saml1 callback via public service url"
  local developer_saml_callback_headers
  developer_saml_callback_headers="$(mktemp)"
  curl -sS -D "${developer_saml_callback_headers}" -o /dev/null -b "${developer_saml_cookie}" "${developer_saml_service_redirect}" || true
  local developer_saml_frontend_redirect
  developer_saml_frontend_redirect="$(extract_header_value "$(cat "${developer_saml_callback_headers}")" "Location")"
  if [[ -z "${developer_saml_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas saml1 callback"
    cat "${developer_saml_callback_headers}" >&2
    exit 1
  fi
  local developer_saml_code
  developer_saml_code="$(parse_query_param "${developer_saml_frontend_redirect}" "code")"
  if [[ -z "${developer_saml_code}" ]]; then
    err "missing developer cas saml1 code in redirect"
    echo "${developer_saml_frontend_redirect}" >&2
    exit 1
  fi
  local developer_saml_token
  developer_saml_token="$(exchange_code_for_token "developer cas saml1" "http://localhost:8081/developers/cas/exchange" "${developer_saml_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_saml_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas saml1 back-channel logout"
  local developer_saml_logout_request
  local developer_saml_ticket
  developer_saml_ticket="$(parse_query_param "${developer_saml_service_redirect}" "ticket")"
  developer_saml_logout_request="$(build_logout_request "${developer_saml_ticket}")"
  curl -fsS -X POST "${developer_saml_service_url}" --data-urlencode "logoutRequest=${developer_saml_logout_request}" >/dev/null
  expect_auth_rejected "developer cas saml1 revoked token" "http://localhost:8081/developers/profile" "${developer_saml_token}"

  log "developer cas1 authorize"
  local developer_cas1_cookie
  local developer_cas1_headers
  developer_cas1_cookie="$(mktemp)"
  developer_cas1_headers="$(mktemp)"
  curl -sS -D "${developer_cas1_headers}" -o /dev/null -c "${developer_cas1_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas1" || true
  local developer_cas1_redirect
  developer_cas1_redirect="$(extract_header_value "$(cat "${developer_cas1_headers}")" "Location")"
  if [[ -z "${developer_cas1_redirect}" ]]; then
    err "missing redirect location from developer cas1 authorize"
    cat "${developer_cas1_headers}" >&2
    exit 1
  fi
  local developer_cas1_service_encoded
  developer_cas1_service_encoded="$(parse_query_param "${developer_cas1_redirect}" "service")"
  local developer_cas1_service_url
  developer_cas1_service_url="$(url_decode "${developer_cas1_service_encoded}")"

  log "cas login form (developer cas1)"
  local developer_cas1_login_html
  developer_cas1_login_html="$(curl -fsS -b "${developer_cas1_cookie}" -c "${developer_cas1_cookie}" "${developer_cas1_redirect}")"
  local developer_cas1_execution
  developer_cas1_execution="$(extract_html_input_value "${developer_cas1_login_html}" "execution")"
  if [[ -z "${developer_cas1_execution}" ]]; then
    err "missing execution token from developer cas1 login page"
    exit 1
  fi
  local developer_cas1_login_headers
  developer_cas1_login_headers="$(mktemp)"
  curl -sS -D "${developer_cas1_login_headers}" -o /dev/null -b "${developer_cas1_cookie}" -c "${developer_cas1_cookie}" \
    -X POST "${developer_cas1_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "execution=${developer_cas1_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_cas1_service_redirect
  developer_cas1_service_redirect="$(extract_header_value "$(cat "${developer_cas1_login_headers}")" "Location")"
  if [[ -z "${developer_cas1_service_redirect}" ]]; then
    err "missing callback redirect from developer cas1 login submit"
    cat "${developer_cas1_login_headers}" >&2
    exit 1
  fi

  log "himarket cas1 callback via public service url"
  local developer_cas1_callback_headers
  developer_cas1_callback_headers="$(mktemp)"
  curl -sS -D "${developer_cas1_callback_headers}" -o /dev/null -b "${developer_cas1_cookie}" "${developer_cas1_service_redirect}" || true
  local developer_cas1_frontend_redirect
  developer_cas1_frontend_redirect="$(extract_header_value "$(cat "${developer_cas1_callback_headers}")" "Location")"
  if [[ -z "${developer_cas1_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas1 callback"
    cat "${developer_cas1_callback_headers}" >&2
    exit 1
  fi
  local developer_cas1_code
  developer_cas1_code="$(parse_query_param "${developer_cas1_frontend_redirect}" "code")"
  if [[ -z "${developer_cas1_code}" ]]; then
    err "missing developer cas1 code in redirect"
    echo "${developer_cas1_frontend_redirect}" >&2
    exit 1
  fi
  local developer_cas1_token
  developer_cas1_token="$(exchange_code_for_token "developer cas1" "http://localhost:8081/developers/cas/exchange" "${developer_cas1_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas1_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas1 back-channel logout"
  local developer_cas1_logout_request
  local developer_cas1_ticket
  developer_cas1_ticket="$(parse_query_param "${developer_cas1_service_redirect}" "ticket")"
  developer_cas1_logout_request="$(build_logout_request "${developer_cas1_ticket}")"
  curl -fsS -X POST "${developer_cas1_service_url}" --data-urlencode "logoutRequest=${developer_cas1_logout_request}" >/dev/null
  expect_auth_rejected "developer cas1 revoked token" "http://localhost:8081/developers/profile" "${developer_cas1_token}"

  log "developer cas2 authorize"
  local developer_cas2_cookie
  local developer_cas2_headers
  developer_cas2_cookie="$(mktemp)"
  developer_cas2_headers="$(mktemp)"
  curl -sS -D "${developer_cas2_headers}" -o /dev/null -c "${developer_cas2_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas2" || true
  local developer_cas2_redirect
  developer_cas2_redirect="$(extract_header_value "$(cat "${developer_cas2_headers}")" "Location")"
  if [[ -z "${developer_cas2_redirect}" ]]; then
    err "missing redirect location from developer cas2 authorize"
    cat "${developer_cas2_headers}" >&2
    exit 1
  fi
  local developer_cas2_service_encoded
  developer_cas2_service_encoded="$(parse_query_param "${developer_cas2_redirect}" "service")"
  local developer_cas2_service_url
  developer_cas2_service_url="$(url_decode "${developer_cas2_service_encoded}")"

  log "cas login form (developer cas2)"
  local developer_cas2_login_html
  developer_cas2_login_html="$(curl -fsS -b "${developer_cas2_cookie}" -c "${developer_cas2_cookie}" "${developer_cas2_redirect}")"
  local developer_cas2_execution
  developer_cas2_execution="$(extract_html_input_value "${developer_cas2_login_html}" "execution")"
  if [[ -z "${developer_cas2_execution}" ]]; then
    err "missing execution token from developer cas2 login page"
    exit 1
  fi
  local developer_cas2_login_headers
  developer_cas2_login_headers="$(mktemp)"
  curl -sS -D "${developer_cas2_login_headers}" -o /dev/null -b "${developer_cas2_cookie}" -c "${developer_cas2_cookie}" \
    -X POST "${developer_cas2_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "execution=${developer_cas2_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_cas2_service_redirect
  developer_cas2_service_redirect="$(extract_header_value "$(cat "${developer_cas2_login_headers}")" "Location")"
  if [[ -z "${developer_cas2_service_redirect}" ]]; then
    err "missing callback redirect from developer cas2 login submit"
    cat "${developer_cas2_login_headers}" >&2
    exit 1
  fi

  log "himarket cas2 callback via public service url"
  local developer_cas2_callback_headers
  developer_cas2_callback_headers="$(mktemp)"
  curl -sS -D "${developer_cas2_callback_headers}" -o /dev/null -b "${developer_cas2_cookie}" "${developer_cas2_service_redirect}" || true
  local developer_cas2_frontend_redirect
  developer_cas2_frontend_redirect="$(extract_header_value "$(cat "${developer_cas2_callback_headers}")" "Location")"
  if [[ -z "${developer_cas2_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas2 callback"
    cat "${developer_cas2_callback_headers}" >&2
    exit 1
  fi
  local developer_cas2_code
  developer_cas2_code="$(parse_query_param "${developer_cas2_frontend_redirect}" "code")"
  if [[ -z "${developer_cas2_code}" ]]; then
    err "missing developer cas2 code in redirect"
    echo "${developer_cas2_frontend_redirect}" >&2
    exit 1
  fi
  local developer_cas2_token
  developer_cas2_token="$(exchange_code_for_token "developer cas2" "http://localhost:8081/developers/cas/exchange" "${developer_cas2_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas2_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas2 back-channel logout"
  local developer_cas2_logout_request
  local developer_cas2_ticket
  developer_cas2_ticket="$(parse_query_param "${developer_cas2_service_redirect}" "ticket")"
  developer_cas2_logout_request="$(build_logout_request "${developer_cas2_ticket}")"
  curl -fsS -X POST "${developer_cas2_service_url}" --data-urlencode "logoutRequest=${developer_cas2_logout_request}" >/dev/null
  expect_auth_rejected "developer cas2 revoked token" "http://localhost:8081/developers/profile" "${developer_cas2_token}"

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

  log "preview admin cas service definition"
  local admin_cas_service_definition
  admin_cas_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas/service-definition")"
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("/admins/cas/callback")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.proxyPolicy // .proxyPolicy).pattern // "") | contains("/admins/cas/proxy-callback")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 9
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5174/login"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.attributeReleasePolicy // .attributeReleasePolicy).allowedAttributes // [])[1][]? | select(.=="displayName")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="GithubClient")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).permitUndefined == false
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).exclusive == true
  ' >/dev/null

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

  log "cas login form (admin)"
  local admin_login_html
  admin_login_html="$(curl -fsS -b "${admin_cookie}" -c "${admin_cookie}" "${admin_redirect}")"
  local admin_execution
  admin_execution="$(extract_html_input_value "${admin_login_html}" "execution")"
  if [[ -z "${admin_execution}" ]]; then
    err "missing execution token from admin cas login page"
    exit 1
  fi
  local admin_login_headers
  admin_login_headers="$(mktemp)"
  curl -sS -D "${admin_login_headers}" -o /dev/null -b "${admin_cookie}" -c "${admin_cookie}" \
    -X POST "${admin_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "execution=${admin_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_service_redirect
  admin_service_redirect="$(extract_header_value "$(cat "${admin_login_headers}")" "Location")"
  if [[ -z "${admin_service_redirect}" ]]; then
    err "missing callback redirect from admin cas login submit"
    cat "${admin_login_headers}" >&2
    exit 1
  fi

  log "himarket admin cas callback via public service url"
  local admin_callback_headers
  admin_callback_headers="$(mktemp)"
  curl -sS -D "${admin_callback_headers}" -o /dev/null -b "${admin_cookie}" "${admin_service_redirect}" || true
  local admin_frontend_redirect
  admin_frontend_redirect="$(extract_header_value "$(cat "${admin_callback_headers}")" "Location")"
  if [[ -z "${admin_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas callback"
    cat "${admin_callback_headers}" >&2
    exit 1
  fi
  local admin_cas_code
  admin_cas_code="$(parse_query_param "${admin_frontend_redirect}" "code")"
  if [[ -z "${admin_cas_code}" ]]; then
    err "missing admin cas code in redirect"
    echo "${admin_frontend_redirect}" >&2
    exit 1
  fi
  local admin_cas_token
  admin_cas_token="$(exchange_code_for_token "admin cas" "http://localhost:8081/admins/cas/exchange" "${admin_cas_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_cas_token}" "http://localhost:8081/admins" >/dev/null

  log "admin cas proxy ticket"
  curl -fsS -X POST "http://localhost:8081/admins/cas/proxy-ticket" \
    -H "Authorization: Bearer ${admin_cas_token}" \
    -H 'Content-Type: application/json' \
    -d '{"provider":"cas","targetService":"http://localhost:5174/admin-proxy-target"}' \
    | jq -e '.data.proxyTicket | startswith("PT-")' >/dev/null

  log "admin cas back-channel logout"
  local admin_logout_request
  local admin_ticket
  admin_ticket="$(parse_query_param "${admin_service_redirect}" "ticket")"
  admin_logout_request="$(build_logout_request "${admin_ticket}")"
  curl -fsS -X POST "${admin_service_url}" --data-urlencode "logoutRequest=${admin_logout_request}" >/dev/null
  expect_auth_rejected "admin cas revoked token" "http://localhost:8081/admins" "${admin_cas_token}"

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
