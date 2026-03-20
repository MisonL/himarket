#!/usr/bin/env bash

prepare_cas_modules() {
  local sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-saml-7.0.0.jar"
  local mfa_sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-simple-mfa-7.0.0.jar"
  local delegated_sentinel="${CAS_MODULES_LIB_DIR}/cas-server-support-pac4j-webflow-7.0.0.jar"
  if [[ -f "${sentinel}" && -f "${mfa_sentinel}" && -f "${delegated_sentinel}" ]]; then
    log "cas modules ready"
    return 0
  fi
  if [[ ! -x "${REPO_DIR}/mvnw" ]]; then
    err "mvnw is not available"
    exit 1
  fi
  mkdir -p "${CAS_MODULES_LIB_DIR}"
  log "prepare cas modules"
  "${REPO_DIR}/mvnw" -q -f "${CAS_MODULES_DIR}/pom.xml" dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="${CAS_MODULES_LIB_DIR}"
  if [[ ! -f "${sentinel}" || ! -f "${mfa_sentinel}" || ! -f "${delegated_sentinel}" ]]; then
    err "cas modules not prepared"
    exit 1
  fi
  log "cas modules prepared"
}

wait_http_ok() {
  local name="$1"
  local url="$2"
  local max_wait="${3:-120}"
  local interval=2
  local probe_timeout="${HTTP_WAIT_PROBE_TIMEOUT:-5}"
  local start_ts
  start_ts="$(date +%s)"
  log "wait service: ${name} url=${url}"
  while true; do
    local elapsed
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
  local interval=2
  local start_ts
  start_ts="$(date +%s)"
  local admin_dn="cn=admin,dc=example,dc=org"
  local admin_password="${LDAP_ADMIN_PASSWORD:-admin}"
  log "wait service: openldap"
  while true; do
    local elapsed
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

detect_expected_cas_service_count() {
  if [[ -n "${CAS_READY_EXPECTED_SERVICE_COUNT:-}" ]]; then
    printf '%s\n' "${CAS_READY_EXPECTED_SERVICE_COUNT}"
    return 0
  fi
  if docker ps --format '{{.Names}}' | grep -qx 'himarket-cas'; then
    find "${DOCKER_DIR}/auth/cas/services" -maxdepth 1 -name '*.json' | wc -l | tr -d ' '
    return 0
  fi
  echo 0
}

get_cas_started_at() {
  docker inspect -f '{{.State.StartedAt}}' himarket-cas 2>/dev/null || true
}

get_cas_logs_since_start() {
  docker logs --since "$(get_cas_started_at)" himarket-cas 2>&1 || true
}

extract_loaded_service_count() {
  local cas_logs="$1"
  printf '%s\n' "${cas_logs}" | sed -n 's/.*Loaded \[\([0-9][0-9]*\)\] service(s).*/\1/p' | tail -n 1
}

cas_runtime_ready_once() {
  local expected_services="${1:-0}"
  if ! docker ps --format '{{.Names}}' | grep -qx 'himarket-cas'; then
    return 0
  fi
  local cas_logs
  cas_logs="$(get_cas_logs_since_start)"
  if ! grep -q "Ready to process requests" <<<"${cas_logs}"; then
    return 1
  fi
  if (( expected_services > 0 )); then
    local loaded_count
    loaded_count="$(extract_loaded_service_count "${cas_logs}")"
    if [[ -z "${loaded_count}" || "${loaded_count}" != "${expected_services}" ]]; then
      return 1
    fi
  fi
  return 0
}

wait_cas_ready() {
  local max_wait="${1:-900}"
  local interval=5
  local cas_login_url="http://localhost:${CAS_HTTP_PORT:-8083}/cas/login"
  local probe_timeout="${CAS_HTTP_PROBE_TIMEOUT:-15}"
  local expected_services
  expected_services="$(detect_expected_cas_service_count)"
  local stable_seconds="${CAS_READY_STABLE_SECONDS:-8}"
  local start_ts
  start_ts="$(date +%s)"
  log "wait service: cas url=${cas_login_url} expectedServices=${expected_services} stableSeconds=${stable_seconds}"
  while true; do
    local elapsed
    elapsed=$(( $(date +%s) - start_ts ))
    if (( elapsed >= max_wait )); then
      break
    fi
    if curl --max-time "${probe_timeout}" -fsS "${cas_login_url}" >/dev/null 2>&1 && cas_runtime_ready_once "${expected_services}"; then
      local first_logs first_count second_logs second_count
      first_logs="$(get_cas_logs_since_start)"
      first_count="$(extract_loaded_service_count "${first_logs}")"
      sleep "${stable_seconds}"
      if curl --max-time "${probe_timeout}" -fsS "${cas_login_url}" >/dev/null 2>&1 && cas_runtime_ready_once "${expected_services}"; then
        second_logs="$(get_cas_logs_since_start)"
        second_count="$(extract_loaded_service_count "${second_logs}")"
        if [[ "${first_count}" == "${second_count}" ]]; then
          log "service ready: cas"
          return 0
        fi
      fi
    fi
    sleep "${interval}"
  done
  err "service not ready: cas"
  return 1
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
  docker exec himarket-cas sh -lc 'mkdir -p /tmp/cas-exploded/WEB-INF/lib/ && cp -f /docker/cas/modules/*.jar /tmp/cas-exploded/WEB-INF/lib/'
  docker restart himarket-cas >/dev/null
}

seed_openldap_users() {
  local ldif_path="${DOCKER_DIR}/auth/ldap/ldif/50-users.ldif"
  local admin_dn="cn=admin,dc=example,dc=org"
  local admin_password="${LDAP_ADMIN_PASSWORD:-admin}"
  log "seed openldap users"
  if docker exec himarket-openldap ldapsearch -x -D "${admin_dn}" -w "${admin_password}" -b "ou=people,dc=example,dc=org" "(uid=alice)" dn 2>/dev/null | grep -q "^dn: uid=alice,"; then
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

load_auth_harness_env() {
  mkdir -p "${DATA_DIR}"
  if [[ ! -f "${ENV_FILE}" ]]; then
    : > "${ENV_FILE}"
  fi
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    . "${ENV_FILE}"
    set +a
  fi
}

init_runtime_context() {
  load_auth_harness_env
  ADMIN_USER="${ADMIN_USERNAME:-admin}"
  ADMIN_PASS="${ADMIN_PASSWORD:-admin}"
  PORTAL_NAME="${PORTAL_NAME:-demo-portal}"
  FRONTEND_REDIRECT_URL="${FRONTEND_REDIRECT_URL:-http://localhost:${HIMARKET_FRONTEND_PORT:-5173}}"
  CAS_HTTP_PORT="${CAS_HTTP_PORT:-8083}"
  CAS_READY_TIMEOUT="${CAS_READY_TIMEOUT:-900}"
  SKIP_BUILD="${SKIP_BUILD:-0}"
  SKIP_DOCKER_UP="${SKIP_DOCKER_UP:-0}"
  CAS_READY_STABLE_SECONDS="${CAS_READY_STABLE_SECONDS:-8}"
  MOCK_OIDC_HOST="${MOCK_OIDC_HOST:-mock-oidc.local}"
  MOCK_OIDC_PORT="${MOCK_OIDC_PORT:-8092}"
  JWK_DIR="${DATA_DIR}/jwt-bearer"
}

maybe_build_artifacts() {
  if [[ "${SKIP_BUILD}" == "1" ]]; then
    log "skip build himarket server jar"
    return 0
  fi
  log "build himarket server jar"
  local java_home="${JAVA_HOME:-}"
  if [[ -z "${java_home}" && -x "/usr/libexec/java_home" ]]; then
    java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi
  if [[ -z "${java_home}" ]]; then
    for candidate in \
      "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
      "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"; do
      if [[ -x "${candidate}/bin/java" ]]; then
        java_home="${candidate}"
        break
      fi
    done
  fi
  if [[ -z "${java_home}" ]]; then
    err "JAVA_HOME is not set and java_home is not available"
    exit 1
  fi
  (cd "${REPO_DIR}" && JAVA_HOME="${java_home}" ./mvnw -pl himarket-bootstrap -am clean package -DskipTests)
  require_cmd npm
  log "build himarket frontend dist"
  (cd "${REPO_DIR}/himarket-web/himarket-frontend" && npm run build)
  log "build himarket admin dist"
  (cd "${REPO_DIR}/himarket-web/himarket-admin" && npm run build)
}

start_docker_services() {
  if [[ "${SKIP_DOCKER_UP}" == "1" ]]; then
    log "skip docker compose up"
    return 0
  fi
  log "start docker services"
  (
    cd "${DOCKER_DIR}"
    export COMPOSE_PROFILES=builtin-mysql
    docker compose --env-file "${ENV_FILE}" -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.auth.yml \
      up -d --build mysql redis-stack-server himarket-server himarket-frontend himarket-admin cas openldap jwks-server
  )
}

bootstrap_runtime() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd node
  prepare_cas_modules
  if ! docker compose version >/dev/null 2>&1; then
    err "docker compose is not available"
    exit 1
  fi
  init_runtime_context
  start_mock_oidc
  trap stop_mock_oidc EXIT
  maybe_build_artifacts
  log "prepare jwks files"
  node_generate_jwks "${JWK_DIR}"
  start_docker_services
  ensure_cas_modules_loaded
  wait_http_ok "himarket-server" "http://localhost:8081/portal/swagger-ui.html" 180
  wait_http_ok "himarket-frontend" "http://localhost:${HIMARKET_FRONTEND_PORT:-5173}/login" 120
  wait_http_ok "himarket-admin" "http://localhost:${HIMARKET_ADMIN_PORT:-5174}/login" 120
  wait_cas_ready "${CAS_READY_TIMEOUT}"
  wait_http_ok "jwks-server" "http://localhost:${JWKS_HTTP_PORT:-8091}/jwks.json" 60
  wait_http_ok "mock-oidc" "http://localhost:${MOCK_OIDC_PORT}/.well-known/openid-configuration" 60
  wait_openldap_ready 120
  seed_openldap_users
}
