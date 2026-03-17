#!/usr/bin/env bash

phase_cas_header() {
  log "developer cas header authorize"
  local developer_header_cookie
  local developer_header_headers
  developer_header_cookie="$(mktemp)"
  developer_header_headers="$(mktemp)"
  curl -sS -D "${developer_header_headers}" -o /dev/null -c "${developer_header_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas-header" || true
  local developer_header_redirect
  developer_header_redirect="$(extract_header_value "$(cat "${developer_header_headers}")" "Location")"
  if [[ -z "${developer_header_redirect}" ]]; then
    err "missing redirect location from developer cas header authorize"
    cat "${developer_header_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${developer_header_redirect}" "method")" == "HEADER" ]] || {
    err "developer cas header authorize should request method=HEADER"
    echo "${developer_header_redirect}" >&2
    exit 1
  }
  local developer_header_service_encoded
  developer_header_service_encoded="$(parse_query_param "${developer_header_redirect}" "service")"
  local developer_header_service_url
  developer_header_service_url="$(url_decode "${developer_header_service_encoded}")"

  log "cas login form (developer cas header)"
  local developer_header_login_html
  developer_header_login_html="$(curl -fsS -b "${developer_header_cookie}" -c "${developer_header_cookie}" "${developer_header_redirect}")"
  local developer_header_execution
  developer_header_execution="$(extract_html_input_value "${developer_header_login_html}" "execution")"
  if [[ -z "${developer_header_execution}" ]]; then
    err "missing execution token from developer cas header login page"
    exit 1
  fi
  local developer_header_login_headers
  developer_header_login_headers="$(mktemp)"
  curl -sS -D "${developer_header_login_headers}" -o /dev/null -b "${developer_header_cookie}" -c "${developer_header_cookie}" \
    -X POST "${developer_header_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "service=${developer_header_service_url}" \
    --data-urlencode "execution=${developer_header_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_header_service_from_response
  developer_header_service_from_response="$(extract_header_value "$(cat "${developer_header_login_headers}")" "service")"
  local developer_header_ticket
  developer_header_ticket="$(extract_header_value "$(cat "${developer_header_login_headers}")" "ticket")"
  if [[ -z "${developer_header_service_from_response}" || -z "${developer_header_ticket}" ]]; then
    err "missing service/ticket headers from developer cas header login submit"
    cat "${developer_header_login_headers}" >&2
    exit 1
  fi

  log "himarket cas header callback via public service url"
  local developer_header_callback_headers
  developer_header_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_header_callback_headers}" -o /dev/null -b "${developer_header_cookie}" \
    --data-urlencode "ticket=${developer_header_ticket}" \
    "${developer_header_service_url}" || true
  local developer_header_frontend_redirect
  developer_header_frontend_redirect="$(extract_header_value "$(cat "${developer_header_callback_headers}")" "Location")"
  if [[ -z "${developer_header_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas header callback"
    cat "${developer_header_callback_headers}" >&2
    exit 1
  fi
  local developer_header_code
  developer_header_code="$(parse_query_param "${developer_header_frontend_redirect}" "code")"
  if [[ -z "${developer_header_code}" ]]; then
    err "missing developer cas header code in redirect"
    echo "${developer_header_frontend_redirect}" >&2
    exit 1
  fi
  local developer_header_token
  developer_header_token="$(exchange_code_for_token "developer cas header" "http://localhost:8081/developers/cas/exchange" "${developer_header_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_header_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas header back-channel logout"
  local developer_header_logout_request
  developer_header_logout_request="$(build_logout_request "${developer_header_ticket}")"
  curl -fsS -X POST "${developer_header_service_url}" --data-urlencode "logoutRequest=${developer_header_logout_request}" >/dev/null
  expect_auth_rejected "developer cas header revoked token" "http://localhost:8081/developers/profile" "${developer_header_token}"

  log "admin cas header authorize"
  local admin_header_cookie
  local admin_header_headers
  admin_header_cookie="$(mktemp)"
  admin_header_headers="$(mktemp)"
  curl -sS -D "${admin_header_headers}" -o /dev/null -c "${admin_header_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas-header" || true
  local admin_header_redirect
  admin_header_redirect="$(extract_header_value "$(cat "${admin_header_headers}")" "Location")"
  if [[ -z "${admin_header_redirect}" ]]; then
    err "missing redirect location from admin cas header authorize"
    cat "${admin_header_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${admin_header_redirect}" "method")" == "HEADER" ]] || {
    err "admin cas header authorize should request method=HEADER"
    echo "${admin_header_redirect}" >&2
    exit 1
  }
  local admin_header_service_encoded
  admin_header_service_encoded="$(parse_query_param "${admin_header_redirect}" "service")"
  local admin_header_service_url
  admin_header_service_url="$(url_decode "${admin_header_service_encoded}")"

  log "cas login form (admin cas header)"
  local admin_header_login_html
  admin_header_login_html="$(curl -fsS -b "${admin_header_cookie}" -c "${admin_header_cookie}" "${admin_header_redirect}")"
  local admin_header_execution
  admin_header_execution="$(extract_html_input_value "${admin_header_login_html}" "execution")"
  if [[ -z "${admin_header_execution}" ]]; then
    err "missing execution token from admin cas header login page"
    exit 1
  fi
  local admin_header_login_headers
  admin_header_login_headers="$(mktemp)"
  curl -sS -D "${admin_header_login_headers}" -o /dev/null -b "${admin_header_cookie}" -c "${admin_header_cookie}" \
    -X POST "${admin_header_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "service=${admin_header_service_url}" \
    --data-urlencode "execution=${admin_header_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_header_service_from_response
  admin_header_service_from_response="$(extract_header_value "$(cat "${admin_header_login_headers}")" "service")"
  local admin_header_ticket
  admin_header_ticket="$(extract_header_value "$(cat "${admin_header_login_headers}")" "ticket")"
  if [[ -z "${admin_header_service_from_response}" || -z "${admin_header_ticket}" ]]; then
    err "missing service/ticket headers from admin cas header login submit"
    cat "${admin_header_login_headers}" >&2
    exit 1
  fi

  log "himarket admin cas header callback via public service url"
  local admin_header_callback_headers
  admin_header_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_header_callback_headers}" -o /dev/null -b "${admin_header_cookie}" \
    --data-urlencode "ticket=${admin_header_ticket}" \
    "${admin_header_service_url}" || true
  local admin_header_frontend_redirect
  admin_header_frontend_redirect="$(extract_header_value "$(cat "${admin_header_callback_headers}")" "Location")"
  if [[ -z "${admin_header_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas header callback"
    cat "${admin_header_callback_headers}" >&2
    exit 1
  fi
  local admin_header_code
  admin_header_code="$(parse_query_param "${admin_header_frontend_redirect}" "code")"
  if [[ -z "${admin_header_code}" ]]; then
    err "missing admin cas header code in redirect"
    echo "${admin_header_frontend_redirect}" >&2
    exit 1
  fi
  local admin_header_token
  admin_header_token="$(exchange_code_for_token "admin cas header" "http://localhost:8081/admins/cas/exchange" "${admin_header_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_header_token}" "http://localhost:8081/admins" >/dev/null

  log "admin cas header back-channel logout"
  local admin_header_logout_request
  admin_header_logout_request="$(build_logout_request "${admin_header_ticket}")"
  curl -fsS -X POST "${admin_header_service_url}" --data-urlencode "logoutRequest=${admin_header_logout_request}" >/dev/null
  expect_auth_rejected "admin cas header revoked token" "http://localhost:8081/admins" "${admin_header_token}"
}
