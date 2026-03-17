#!/usr/bin/env bash

verify_cas_authorize_flags() {
  local scope="$1"
  local authorize_url="$2"
  local scope_label="${scope} cas"
  local headers
  headers="$(mktemp)"
  curl -sS -D "${headers}" -o /dev/null "${authorize_url}" || true
  local redirect
  redirect="$(extract_header_value "$(cat "${headers}")" "Location")"
  if [[ -z "${redirect}" ]]; then
    err "missing redirect location from ${scope_label} flag authorize"
    cat "${headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${redirect}" "gateway")" == "true" ]] || {
    err "${scope_label} gateway flag missing from redirect"
    echo "${redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${redirect}" "warn")" == "true" ]] || {
    err "${scope_label} warn flag missing from redirect"
    echo "${redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${redirect}" "rememberMe")" == "true" ]] || {
    err "${scope_label} rememberMe flag missing from redirect"
    echo "${redirect}" >&2
    exit 1
  }
}

verify_cas_browser_flow() {
  local name="$1"
  local authorize_url="$2"
  local exchange_url="$3"
  local profile_url="$4"
  local username="$5"
  local password="$6"
  local logout_mode="$7"
  local login_label="$8"
  local authorize_headers
  local cookie_jar
  cookie_jar="$(mktemp)"
  authorize_headers="$(mktemp)"
  curl -sS -D "${authorize_headers}" -o /dev/null -c "${cookie_jar}" "${authorize_url}" || true
  local redirect
  redirect="$(extract_header_value "$(cat "${authorize_headers}")" "Location")"
  if [[ -z "${redirect}" ]]; then
    err "missing redirect location from ${name} authorize"
    cat "${authorize_headers}" >&2
    exit 1
  fi
  local service_encoded
  service_encoded="$(parse_query_param "${redirect}" "service")"
  local service_url
  service_url="$(url_decode "${service_encoded}")"

  log "${login_label}"
  local login_html
  login_html="$(curl -fsS -b "${cookie_jar}" -c "${cookie_jar}" "${redirect}")"
  local execution
  execution="$(extract_html_input_value "${login_html}" "execution")"
  if [[ -z "${execution}" ]]; then
    err "missing execution token from ${name} login page"
    exit 1
  fi
  local login_headers
  login_headers="$(mktemp)"
  curl -sS -D "${login_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" \
    -X POST "${redirect}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode "service=${service_url}" \
    --data-urlencode "execution=${execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local service_redirect
  service_redirect="$(extract_header_value "$(cat "${login_headers}")" "Location")"
  if [[ -z "${service_redirect}" ]]; then
    err "missing callback redirect from ${name} login submit"
    cat "${login_headers}" >&2
    exit 1
  fi
  local ticket
  ticket="$(parse_query_param "${service_redirect}" "ticket")"
  if [[ -z "${ticket}" ]]; then
    err "missing ${name} ticket in redirect"
    echo "${service_redirect}" >&2
    exit 1
  fi

  log "${name} callback via public service url"
  local callback_headers
  callback_headers="$(mktemp)"
  curl -sS -G -D "${callback_headers}" -o /dev/null -b "${cookie_jar}" \
    --data-urlencode "ticket=${ticket}" \
    "${service_url}" || true
  local frontend_redirect
  frontend_redirect="$(extract_header_value "$(cat "${callback_headers}")" "Location")"
  if [[ -z "${frontend_redirect}" ]]; then
    err "missing frontend redirect from ${name} callback"
    cat "${callback_headers}" >&2
    exit 1
  fi
  local code
  code="$(parse_query_param "${frontend_redirect}" "code")"
  if [[ -z "${code}" ]]; then
    err "missing ${name} code in redirect"
    echo "${frontend_redirect}" >&2
    exit 1
  fi
  local token
  token="$(exchange_code_for_token "${name}" "${exchange_url}" "${code}")"
  curl -fsS -H "Authorization: Bearer ${token}" "${profile_url}" >/dev/null

  if [[ "${name}" == "admin cas" ]]; then
    admin_cas_token="${token}"
    admin_cas_service_url="${service_url}"
    admin_cas_ticket="${ticket}"
  fi

  if [[ "${name}" == "developer cas" ]]; then
    log "developer cas proxy ticket"
    curl -fsS -X POST "http://localhost:8081/developers/cas/proxy-ticket" \
      -H "Authorization: Bearer ${token}" \
      -H 'Content-Type: application/json' \
      -d '{"provider":"cas","targetService":"http://localhost:5173/proxy-target"}' \
      | jq -e '.data.proxyTicket | startswith("PT-")' >/dev/null
    local reject_resp
    reject_resp="$(curl_json POST "http://localhost:8081/developers/cas/proxy-ticket" '{"provider":"cas","targetService":"http://localhost:5174/forbidden"}' "Authorization: Bearer ${token}")"
    [[ "$(echo "${reject_resp}" | head -n 1)" == "400" ]] || {
      err "developer cas proxy target policy should reject forbidden target service"
      echo "${reject_resp}" | tail -n +2 >&2
      exit 1
    }
  fi

  if [[ "${logout_mode}" == "front" ]]; then
    log "${name} front-channel logout"
    trigger_front_channel_logout "${name}" "${service_url}" "${ticket}"
  elif [[ "${logout_mode}" == "back" ]]; then
    log "${name} back-channel logout"
    local logout_request
    logout_request="$(build_logout_request "${ticket}")"
    curl -fsS -X POST "${service_url}" --data-urlencode "logoutRequest=${logout_request}" >/dev/null
  else
    return 0
  fi
  expect_auth_rejected "${name} revoked token" "${profile_url}" "${token}"
}

verify_developer_cas_protocol_flows() {
  log "developer cas authorize (capture state cookie and service url)"
  verify_cas_browser_flow \
    "developer cas" \
    "http://localhost:8081/developers/cas/authorize?provider=cas" \
    "http://localhost:8081/developers/cas/exchange" \
    "http://localhost:8081/developers/profile" \
    "alice" \
    "alice" \
    "front" \
    "cas login form (developer)"

  log "developer cas saml1 authorize"
  verify_cas_browser_flow \
    "developer cas saml1" \
    "http://localhost:8081/developers/cas/authorize?provider=cas-saml1" \
    "http://localhost:8081/developers/cas/exchange" \
    "http://localhost:8081/developers/profile" \
    "alice" \
    "alice" \
    "front" \
    "cas login form (developer saml1)"

  log "developer cas1 authorize"
  verify_cas_browser_flow \
    "developer cas1" \
    "http://localhost:8081/developers/cas/authorize?provider=cas1" \
    "http://localhost:8081/developers/cas/exchange" \
    "http://localhost:8081/developers/profile" \
    "alice" \
    "alice" \
    "back" \
    "cas login form (developer cas1)"

  log "developer cas2 authorize"
  verify_cas_browser_flow \
    "developer cas2" \
    "http://localhost:8081/developers/cas/authorize?provider=cas2" \
    "http://localhost:8081/developers/cas/exchange" \
    "http://localhost:8081/developers/profile" \
    "alice" \
    "alice" \
    "back" \
    "cas login form (developer cas2)"
}
