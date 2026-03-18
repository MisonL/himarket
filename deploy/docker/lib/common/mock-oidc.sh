#!/usr/bin/env bash

extract_html_input_value() {
  local html="$1"
  local name="$2"
  printf '%s' "${html}" | node -e '
    const fs = require("fs");
    const html = fs.readFileSync(0, "utf8");
    const name = process.argv[1];
    const tagMatch = html.match(new RegExp(`<input[^>]*name=["\\x27]${name}["\\x27][^>]*>`, "i"));
    if (!tagMatch) { console.log(""); process.exit(0); }
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
  curl --resolve "${MOCK_OIDC_HOST:-mock-oidc.local}:${MOCK_OIDC_PORT:-8092}:127.0.0.1" "$@"
}

start_mock_oidc() {
  require_cmd node
  if docker ps -a --filter name='^/himarket-mock-oidc$' --format '{{.Names}}' | grep -qx 'himarket-mock-oidc'; then
    log "remove stale compose-managed mock oidc container"
    docker rm -f himarket-mock-oidc >/dev/null
  fi
  if curl -fsS "http://localhost:${MOCK_OIDC_PORT:-8092}/.well-known/openid-configuration" >/dev/null 2>&1; then
    log "mock oidc already available"
    return 0
  fi
  local log_file="${DATA_DIR}/mock-oidc.log"
  OIDC_ISSUER="http://${MOCK_OIDC_HOST:-mock-oidc.local}:${MOCK_OIDC_PORT:-8092}" \
  OIDC_PORT="${MOCK_OIDC_PORT:-8092}" \
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
  local client_id redirect_uri response_type scope state nonce
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
    callback_location="http://localhost:${CAS_HTTP_PORT:-8083}${callback_location#https://cas.example.org:8443}"
  fi
  printf '%s\n' "${callback_location}"
}

normalize_local_cas_url() {
  local url="$1"
  if [[ "${url}" == https://cas.example.org:8443/* ]]; then
    printf 'http://localhost:%s%s\n' "${CAS_HTTP_PORT:-8083}" "${url#https://cas.example.org:8443}"
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
