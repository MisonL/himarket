#!/usr/bin/env bash

phase_cas_mfa() {
  log "developer cas mfa authorize"
  local developer_mfa_cookie
  local developer_mfa_headers
  developer_mfa_cookie="$(mktemp)"
  developer_mfa_headers="$(mktemp)"
  curl -sS -D "${developer_mfa_headers}" -o /dev/null -c "${developer_mfa_cookie}" "${HIMARKET_BASE_URL}/developers/cas/authorize?provider=cas-mfa" || true
  local developer_mfa_redirect
  developer_mfa_redirect="$(extract_header_value "$(cat "${developer_mfa_headers}")" "Location")"
  if [[ -z "${developer_mfa_redirect}" ]]; then
    err "missing redirect location from developer cas mfa authorize"
    cat "${developer_mfa_headers}" >&2
    exit 1
  fi
  local developer_mfa_service_encoded
  developer_mfa_service_encoded="$(parse_query_param "${developer_mfa_redirect}" "service")"
  local developer_mfa_service_url
  developer_mfa_service_url="$(url_decode "${developer_mfa_service_encoded}")"
  local developer_mfa_sotp
  developer_mfa_sotp="$(request_cas_mfa_token "${developer_mfa_service_url}" "alice" "alice")"
  if [[ -z "${developer_mfa_sotp}" || "${developer_mfa_sotp}" == "null" ]]; then
    err "missing developer cas mfa token"
    exit 1
  fi
  local developer_mfa_ticket
  developer_mfa_ticket="$(issue_cas_rest_service_ticket "${developer_mfa_service_url}" "alice" "alice" "${developer_mfa_sotp}")"
  local developer_mfa_callback_headers
  developer_mfa_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_mfa_callback_headers}" -o /dev/null -b "${developer_mfa_cookie}" \
    --data-urlencode "ticket=${developer_mfa_ticket}" \
    "${developer_mfa_service_url}" || true
  local developer_mfa_frontend_redirect
  developer_mfa_frontend_redirect="$(extract_header_value "$(cat "${developer_mfa_callback_headers}")" "Location")"
  if [[ -z "${developer_mfa_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas mfa callback"
    cat "${developer_mfa_callback_headers}" >&2
    exit 1
  fi
  local developer_mfa_code
  developer_mfa_code="$(parse_query_param "${developer_mfa_frontend_redirect}" "code")"
  if [[ -z "${developer_mfa_code}" ]]; then
    err "missing developer cas mfa code in redirect"
    echo "${developer_mfa_frontend_redirect}" >&2
    exit 1
  fi
  local developer_mfa_token
  developer_mfa_token="$(exchange_code_for_token "developer cas mfa" "${HIMARKET_BASE_URL}/developers/cas/exchange" "${developer_mfa_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_mfa_token}" "${HIMARKET_BASE_URL}/sessions" >/dev/null

  log "developer cas mfa back-channel logout"
  local developer_mfa_logout_request
  developer_mfa_logout_request="$(build_logout_request "${developer_mfa_ticket}")"
  curl -fsS -X POST "${developer_mfa_service_url}" --data-urlencode "logoutRequest=${developer_mfa_logout_request}" >/dev/null
  expect_auth_rejected "developer cas mfa revoked token" "${HIMARKET_BASE_URL}/sessions" "${developer_mfa_token}"

  log "admin cas mfa authorize"
  local admin_mfa_cookie
  local admin_mfa_headers
  admin_mfa_cookie="$(mktemp)"
  admin_mfa_headers="$(mktemp)"
  curl -sS -D "${admin_mfa_headers}" -o /dev/null -c "${admin_mfa_cookie}" "${HIMARKET_BASE_URL}/admins/cas/authorize?provider=cas-mfa" || true
  local admin_mfa_redirect
  admin_mfa_redirect="$(extract_header_value "$(cat "${admin_mfa_headers}")" "Location")"
  if [[ -z "${admin_mfa_redirect}" ]]; then
    err "missing redirect location from admin cas mfa authorize"
    cat "${admin_mfa_headers}" >&2
    exit 1
  fi
  local admin_mfa_service_encoded
  admin_mfa_service_encoded="$(parse_query_param "${admin_mfa_redirect}" "service")"
  local admin_mfa_service_url
  admin_mfa_service_url="$(url_decode "${admin_mfa_service_encoded}")"
  local admin_mfa_sotp
  admin_mfa_sotp="$(request_cas_mfa_token "${admin_mfa_service_url}" "admin" "admin")"
  if [[ -z "${admin_mfa_sotp}" || "${admin_mfa_sotp}" == "null" ]]; then
    err "missing admin cas mfa token"
    exit 1
  fi
  local admin_mfa_ticket
  admin_mfa_ticket="$(issue_cas_rest_service_ticket "${admin_mfa_service_url}" "admin" "admin" "${admin_mfa_sotp}")"
  local admin_mfa_callback_headers
  admin_mfa_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_mfa_callback_headers}" -o /dev/null -b "${admin_mfa_cookie}" \
    --data-urlencode "ticket=${admin_mfa_ticket}" \
    "${admin_mfa_service_url}" || true
  local admin_mfa_frontend_redirect
  admin_mfa_frontend_redirect="$(extract_header_value "$(cat "${admin_mfa_callback_headers}")" "Location")"
  if [[ -z "${admin_mfa_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas mfa callback"
    cat "${admin_mfa_callback_headers}" >&2
    exit 1
  fi
  local admin_mfa_code
  admin_mfa_code="$(parse_query_param "${admin_mfa_frontend_redirect}" "code")"
  if [[ -z "${admin_mfa_code}" ]]; then
    err "missing admin cas mfa code in redirect"
    echo "${admin_mfa_frontend_redirect}" >&2
    exit 1
  fi
  local admin_mfa_token
  admin_mfa_token="$(exchange_code_for_token "admin cas mfa" "${HIMARKET_BASE_URL}/admins/cas/exchange" "${admin_mfa_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_mfa_token}" "${HIMARKET_BASE_URL}/admins" >/dev/null

  log "admin cas mfa back-channel logout"
  local admin_mfa_logout_request
  admin_mfa_logout_request="$(build_logout_request "${admin_mfa_ticket}")"
  curl -fsS -X POST "${admin_mfa_service_url}" --data-urlencode "logoutRequest=${admin_mfa_logout_request}" >/dev/null
  expect_auth_rejected "admin cas mfa revoked token" "${HIMARKET_BASE_URL}/admins" "${admin_mfa_token}"
}
