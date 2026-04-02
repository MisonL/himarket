#!/usr/bin/env bash

oauth2_complete_with_cookie_jar() {
  local url="$1"
  local body="$2"
  local cookie_jar="$3"
  local result
  result="$(
    curl -sS \
      -w '\nHTTP_CODE:%{http_code}' \
      -b "${cookie_jar}" \
      -c "${cookie_jar}" \
      -H 'Accept: application/json' \
      -H 'Content-Type: application/json' \
      -X POST \
      -d "${body}" \
      "${url}" 2>&1 || true
  )"
  local http_code
  http_code="$(printf '%s\n' "${result}" | awk -F: '/^HTTP_CODE:/ {print $2}' | tail -n 1)"
  local resp
  resp="$(printf '%s\n' "${result}" | sed '/^HTTP_CODE:/d')"
  echo "${http_code}"$'\n'"${resp}"
}

verify_oauth2_browser_provider() {
  local provider="$1"
  curl -fsS "http://localhost:8081/developers/oauth2/providers" \
    | jq -e --arg provider "${provider}" '.data[]? | select(.provider==$provider and .interactiveBrowserLogin==true)' >/dev/null
}

verify_oauth2_direct_provider() {
  local provider="$1"
  curl -fsS "http://localhost:8081/developers/oauth2/providers" \
    | jq -e --arg provider "${provider}" '.data[]? | select(.provider==$provider and .directTokenLogin==true)' >/dev/null
}

verify_oauth2_trusted_header_provider() {
  local provider="$1"
  curl -fsS "http://localhost:8081/developers/oauth2/providers" \
    | jq -e --arg provider "${provider}" '.data[]? | select(.provider==$provider and .trustedHeaderLogin==true)' >/dev/null
}

verify_oauth2_browser_cas_flow() {
  local provider="$1"
  local username="$2"
  local password="$3"
  local cookie_jar
  local authorize_headers
  local redirect
  local service_encoded
  local service_url
  local login_html
  local execution
  local login_headers
  local callback_url
  local returned_provider
  local state
  local ticket
  local request_body
  local complete_resp
  local complete_code
  local complete_body
  local access_token

  cookie_jar="$(mktemp)"
  authorize_headers="$(mktemp)"
  curl -sS -D "${authorize_headers}" -o /dev/null -c "${cookie_jar}" \
    "http://localhost:8081/developers/oauth2/authorize?provider=${provider}" || true
  redirect="$(extract_header_value "$(cat "${authorize_headers}")" "Location")"
  if [[ -z "${redirect}" ]]; then
    err "missing redirect location from oauth2 authorize: ${provider}"
    cat "${authorize_headers}" >&2
    exit 1
  fi

  service_encoded="$(parse_query_param "${redirect}" "service")"
  if [[ -z "${service_encoded}" ]]; then
    err "missing service parameter in oauth2 authorize redirect: ${provider}"
    echo "${redirect}" >&2
    exit 1
  fi
  service_url="$(url_decode "${service_encoded}")"

  login_html="$(curl -fsS -b "${cookie_jar}" -c "${cookie_jar}" "${redirect}")"
  execution="$(extract_html_input_value "${login_html}" "execution")"
  if [[ -z "${execution}" ]]; then
    err "missing execution token in cas login page: ${provider}"
    exit 1
  fi

  login_headers="$(mktemp)"
  curl -sS -D "${login_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" \
    -X POST "${redirect}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode "service=${service_url}" \
    --data-urlencode "execution=${execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  callback_url="$(extract_header_value "$(cat "${login_headers}")" "Location")"
  if [[ -z "${callback_url}" ]]; then
    err "missing callback redirect after cas login: ${provider}"
    cat "${login_headers}" >&2
    exit 1
  fi

  returned_provider="$(parse_query_param "${callback_url}" "provider")"
  state="$(parse_query_param "${callback_url}" "state")"
  ticket="$(parse_query_param "${callback_url}" "ticket")"
  if [[ "${returned_provider}" != "${provider}" ]]; then
    err "oauth2 callback provider mismatch: expected=${provider} actual=${returned_provider}"
    echo "${callback_url}" >&2
    exit 1
  fi
  if [[ -z "${state}" || -z "${ticket}" ]]; then
    err "oauth2 callback missing state or ticket: ${provider}"
    echo "${callback_url}" >&2
    exit 1
  fi

  request_body="$(jq -nc \
    --arg provider "${provider}" \
    --arg state "${state}" \
    --arg ticket "${ticket}" \
    '{provider:$provider, state:$state, ticket:$ticket}')"
  complete_resp="$(oauth2_complete_with_cookie_jar "http://localhost:8081/developers/oauth2/complete" "${request_body}" "${cookie_jar}")"
  complete_code="$(echo "${complete_resp}" | head -n 1)"
  if [[ "${complete_code}" != 200 ]]; then
    err "oauth2 complete failed: provider=${provider} http=${complete_code}"
    echo "${complete_resp}" | tail -n +2 >&2
    exit 1
  fi
  complete_body="$(echo "${complete_resp}" | tail -n +2)"
  access_token="$(echo "${complete_body}" | jq -r '.data.access_token')"
  if [[ -z "${access_token}" || "${access_token}" == "null" ]]; then
    err "oauth2 complete did not return access_token: ${provider}"
    echo "${complete_body}" >&2
    exit 1
  fi

  curl -fsS -H "Authorization: Bearer ${access_token}" "http://localhost:8081/developers/profile" >/dev/null
}

verify_oauth2_direct_flow() {
  local provider="$1"
  local jwk_dir="$2"
  if [[ -z "${jwk_dir}" ]]; then
    err "oauth2 direct flow missing jwk_dir"
    exit 1
  fi
  local private_jwk="${jwk_dir}/private.jwk.json"
  local assertion
  assertion="$(node_mint_jwt "${private_jwk}" "http://jwks-server/issuer" "himarket-api" "alice" "Alice" "alice@example.org")"
  local request_body
  request_body="$(jq -nc --arg provider "${provider}" --arg jwt "${assertion}" '{provider:$provider, jwt:$jwt}')"
  local direct_resp
  direct_resp="$(curl_json POST "http://localhost:8081/developers/oauth2/direct" "${request_body}")"
  local direct_code
  direct_code="$(echo "${direct_resp}" | head -n 1)"
  if [[ "${direct_code}" != 200 ]]; then
    err "oauth2 direct failed: provider=${provider} http=${direct_code}"
    echo "${direct_resp}" | tail -n +2 >&2
    exit 1
  fi
  local direct_body
  direct_body="$(echo "${direct_resp}" | tail -n +2)"
  local access_token
  access_token="$(echo "${direct_body}" | jq -r '.data.access_token')"
  if [[ -z "${access_token}" || "${access_token}" == "null" ]]; then
    err "oauth2 direct did not return access_token: ${provider}"
    echo "${direct_body}" >&2
    exit 1
  fi
  curl -fsS -H "Authorization: Bearer ${access_token}" "http://localhost:8081/developers/profile" >/dev/null
}

verify_oauth2_trusted_header_flow() {
  local provider="$1"
  local request_body
  request_body="$(jq -nc --arg provider "${provider}" '{provider:$provider}')"
  local trusted_resp
  trusted_resp="$(
    curl -sS \
      -w '\nHTTP_CODE:%{http_code}' \
      -H 'Accept: application/json' \
      -H 'Content-Type: application/json' \
      -H 'X-Auth-User: alice' \
      -H 'X-Auth-Name: Alice Header' \
      -H 'X-Auth-Email: alice@example.org' \
      -H 'X-Auth-Groups: platform,ops' \
      -H 'X-Auth-Roles: developer,owner' \
      -X POST \
      -d "${request_body}" \
      "http://localhost:8081/developers/oauth2/trusted-header" 2>&1 || true
  )"
  local trusted_code
  trusted_code="$(printf '%s\n' "${trusted_resp}" | awk -F: '/^HTTP_CODE:/ {print $2}' | tail -n 1)"
  local trusted_body
  trusted_body="$(printf '%s\n' "${trusted_resp}" | sed '/^HTTP_CODE:/d')"
  if [[ "${trusted_code}" != 200 ]]; then
    err "oauth2 trusted header failed: provider=${provider} http=${trusted_code}"
    echo "${trusted_body}" >&2
    exit 1
  fi
  local access_token
  access_token="$(echo "${trusted_body}" | jq -r '.data.access_token')"
  if [[ -z "${access_token}" || "${access_token}" == "null" ]]; then
    err "oauth2 trusted header did not return access_token: ${provider}"
    echo "${trusted_body}" >&2
    exit 1
  fi
  curl -fsS -H "Authorization: Bearer ${access_token}" "http://localhost:8081/developers/profile" >/dev/null
}

phase_oauth2_browser_cas() {
  log "verify oauth2 browser cas providers"
  verify_oauth2_browser_provider "cas-jwt-validate-json"
  verify_oauth2_browser_provider "cas-jwt-validate-xml"
  verify_oauth2_browser_provider "cas-jwt-validate-saml"
  verify_oauth2_browser_provider "cas-jwt-ticket"
  verify_oauth2_direct_provider "jwt-bearer"
  verify_oauth2_trusted_header_provider "trusted-header"

  log "verify oauth2 direct jwt flow"
  verify_oauth2_direct_flow "jwt-bearer" "${jwk_dir}"

  log "verify oauth2 browser cas json validation flow"
  verify_oauth2_browser_cas_flow "cas-jwt-validate-json" "alice" "alice"

  log "verify oauth2 browser cas xml validation flow"
  verify_oauth2_browser_cas_flow "cas-jwt-validate-xml" "alice" "alice"

  log "verify oauth2 browser cas saml validation flow"
  verify_oauth2_browser_cas_flow "cas-jwt-validate-saml" "alice" "alice"

  log "verify oauth2 browser cas ticket exchange flow"
  verify_oauth2_browser_cas_flow "cas-jwt-ticket" "alice" "alice"

  log "verify oauth2 trusted header flow"
  verify_oauth2_trusted_header_flow "trusted-header"
}
