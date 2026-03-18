#!/usr/bin/env bash

phase_cas_delegated() {
  log "developer cas delegated authorize"
  local developer_delegated_cookie
  local developer_delegated_headers
  developer_delegated_cookie="$(mktemp)"
  developer_delegated_headers="$(mktemp)"
  curl -sS -D "${developer_delegated_headers}" -o /dev/null -c "${developer_delegated_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas-delegated" || true
  local developer_delegated_redirect
  developer_delegated_redirect="$(extract_header_value "$(cat "${developer_delegated_headers}")" "Location")"
  if [[ -z "${developer_delegated_redirect}" ]]; then
    err "missing redirect location from developer cas delegated authorize"
    cat "${developer_delegated_headers}" >&2
    exit 1
  fi
  local developer_delegated_service_encoded
  developer_delegated_service_encoded="$(parse_query_param "${developer_delegated_redirect}" "service")"
  local developer_delegated_service_url
  developer_delegated_service_url="$(url_decode "${developer_delegated_service_encoded}")"
  local developer_delegated_login_headers
  developer_delegated_login_headers="$(mktemp)"
  local developer_delegated_login_html
  developer_delegated_login_html="$(curl -sS -D "${developer_delegated_login_headers}" -b "${developer_delegated_cookie}" -c "${developer_delegated_cookie}" "${developer_delegated_redirect}" || true)"
  local developer_delegated_oidc_link
  developer_delegated_oidc_link="$(extract_header_value "$(cat "${developer_delegated_login_headers}")" "Location")"
  if [[ -z "${developer_delegated_oidc_link}" ]]; then
    developer_delegated_oidc_link="$(extract_html_link_href "${developer_delegated_login_html}" "Mock OIDC")"
  fi
  if [[ -z "${developer_delegated_oidc_link}" ]]; then
    developer_delegated_oidc_link="$(extract_html_link_href "${developer_delegated_login_html}" "MockOidcClient")"
  fi
  developer_delegated_oidc_link="${developer_delegated_oidc_link//&amp;/&}"
  if [[ -z "${developer_delegated_oidc_link}" ]]; then
    err "missing mock oidc delegated link from developer cas login page"
    cat "${developer_delegated_login_headers}" >&2
    printf '%s\n' "${developer_delegated_login_html}" >&2
    exit 1
  fi
  if [[ "${developer_delegated_oidc_link}" == /* ]]; then
    developer_delegated_oidc_link="http://localhost:${cas_http_port}${developer_delegated_oidc_link}"
  fi
  log "mock oidc delegated login (developer)"
  local developer_delegated_provider_redirect
  developer_delegated_provider_redirect="$(login_via_mock_oidc "${developer_delegated_oidc_link}" "${developer_delegated_cookie}" "alice" "alice")"
  if [[ -z "${developer_delegated_provider_redirect}" ]]; then
    err "missing redirect from developer delegated oidc login"
    exit 1
  fi
  local developer_delegated_service_redirect
  developer_delegated_service_redirect="$(follow_redirect_until_ticket "${developer_delegated_provider_redirect}" "${developer_delegated_cookie}")"
  if [[ -z "${developer_delegated_service_redirect}" ]]; then
    err "missing service redirect from developer delegated oidc callback"
    echo "${developer_delegated_provider_redirect}" >&2
    exit 1
  fi
  local developer_delegated_ticket
  developer_delegated_ticket="$(parse_query_param "${developer_delegated_service_redirect}" "ticket")"
  if [[ -z "${developer_delegated_ticket}" ]]; then
    err "missing developer delegated ticket in redirect"
    echo "${developer_delegated_service_redirect}" >&2
    exit 1
  fi
  log "himarket developer delegated callback via public service url"
  local developer_delegated_callback_headers
  developer_delegated_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_delegated_callback_headers}" -o /dev/null -b "${developer_delegated_cookie}" \
    --data-urlencode "ticket=${developer_delegated_ticket}" \
    "${developer_delegated_service_url}" || true
  local developer_delegated_frontend_redirect
  developer_delegated_frontend_redirect="$(extract_header_value "$(cat "${developer_delegated_callback_headers}")" "Location")"
  if [[ -z "${developer_delegated_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer delegated callback"
    cat "${developer_delegated_callback_headers}" >&2
    exit 1
  fi
  local developer_delegated_code
  developer_delegated_code="$(parse_query_param "${developer_delegated_frontend_redirect}" "code")"
  if [[ -z "${developer_delegated_code}" ]]; then
    err "missing developer delegated code in redirect"
    echo "${developer_delegated_frontend_redirect}" >&2
    exit 1
  fi
  local developer_delegated_token
  developer_delegated_token="$(exchange_code_for_token "developer delegated" "http://localhost:8081/developers/cas/exchange" "${developer_delegated_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_delegated_token}" "http://localhost:8081/developers/profile" | jq -e '(.data.username // "") | contains("alice")' >/dev/null

  log "developer delegated back-channel logout"
  local developer_delegated_logout_request
  developer_delegated_logout_request="$(build_logout_request "${developer_delegated_ticket}")"
  curl -fsS -X POST "${developer_delegated_service_url}" --data-urlencode "logoutRequest=${developer_delegated_logout_request}" >/dev/null
  expect_auth_rejected "developer delegated revoked token" "http://localhost:8081/developers/profile" "${developer_delegated_token}"

  log "admin cas delegated authorize"
  local admin_delegated_cookie
  local admin_delegated_headers
  admin_delegated_cookie="$(mktemp)"
  admin_delegated_headers="$(mktemp)"
  curl -sS -D "${admin_delegated_headers}" -o /dev/null -c "${admin_delegated_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas-delegated" || true
  local admin_delegated_redirect
  admin_delegated_redirect="$(extract_header_value "$(cat "${admin_delegated_headers}")" "Location")"
  if [[ -z "${admin_delegated_redirect}" ]]; then
    err "missing redirect location from admin cas delegated authorize"
    cat "${admin_delegated_headers}" >&2
    exit 1
  fi
  local admin_delegated_service_encoded
  admin_delegated_service_encoded="$(parse_query_param "${admin_delegated_redirect}" "service")"
  local admin_delegated_service_url
  admin_delegated_service_url="$(url_decode "${admin_delegated_service_encoded}")"
  local admin_delegated_login_headers
  admin_delegated_login_headers="$(mktemp)"
  local admin_delegated_login_html
  admin_delegated_login_html="$(curl -sS -D "${admin_delegated_login_headers}" -b "${admin_delegated_cookie}" -c "${admin_delegated_cookie}" "${admin_delegated_redirect}" || true)"
  local admin_delegated_oidc_link
  admin_delegated_oidc_link="$(extract_header_value "$(cat "${admin_delegated_login_headers}")" "Location")"
  if [[ -z "${admin_delegated_oidc_link}" ]]; then
    admin_delegated_oidc_link="$(extract_html_link_href "${admin_delegated_login_html}" "Mock OIDC")"
  fi
  if [[ -z "${admin_delegated_oidc_link}" ]]; then
    admin_delegated_oidc_link="$(extract_html_link_href "${admin_delegated_login_html}" "MockOidcClient")"
  fi
  admin_delegated_oidc_link="${admin_delegated_oidc_link//&amp;/&}"
  if [[ -z "${admin_delegated_oidc_link}" ]]; then
    err "missing mock oidc delegated link from admin cas login page"
    cat "${admin_delegated_login_headers}" >&2
    printf '%s\n' "${admin_delegated_login_html}" >&2
    exit 1
  fi
  if [[ "${admin_delegated_oidc_link}" == /* ]]; then
    admin_delegated_oidc_link="http://localhost:${cas_http_port}${admin_delegated_oidc_link}"
  fi
  log "mock oidc delegated login (admin)"
  local admin_delegated_provider_redirect
  admin_delegated_provider_redirect="$(login_via_mock_oidc "${admin_delegated_oidc_link}" "${admin_delegated_cookie}" "admin" "admin")"
  if [[ -z "${admin_delegated_provider_redirect}" ]]; then
    err "missing redirect from admin delegated oidc login"
    exit 1
  fi
  local admin_delegated_service_redirect
  admin_delegated_service_redirect="$(follow_redirect_until_ticket "${admin_delegated_provider_redirect}" "${admin_delegated_cookie}")"
  if [[ -z "${admin_delegated_service_redirect}" ]]; then
    err "missing service redirect from admin delegated oidc callback"
    echo "${admin_delegated_provider_redirect}" >&2
    exit 1
  fi
  local admin_delegated_ticket
  admin_delegated_ticket="$(parse_query_param "${admin_delegated_service_redirect}" "ticket")"
  if [[ -z "${admin_delegated_ticket}" ]]; then
    err "missing admin delegated ticket in redirect"
    echo "${admin_delegated_service_redirect}" >&2
    exit 1
  fi
  log "himarket admin delegated callback via public service url"
  local admin_delegated_callback_headers
  admin_delegated_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_delegated_callback_headers}" -o /dev/null -b "${admin_delegated_cookie}" \
    --data-urlencode "ticket=${admin_delegated_ticket}" \
    "${admin_delegated_service_url}" || true
  local admin_delegated_frontend_redirect
  admin_delegated_frontend_redirect="$(extract_header_value "$(cat "${admin_delegated_callback_headers}")" "Location")"
  if [[ -z "${admin_delegated_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin delegated callback"
    cat "${admin_delegated_callback_headers}" >&2
    exit 1
  fi
  local admin_delegated_code
  admin_delegated_code="$(parse_query_param "${admin_delegated_frontend_redirect}" "code")"
  if [[ -z "${admin_delegated_code}" ]]; then
    err "missing admin delegated code in redirect"
    echo "${admin_delegated_frontend_redirect}" >&2
    exit 1
  fi
  local admin_delegated_token
  admin_delegated_token="$(exchange_code_for_token "admin delegated" "http://localhost:8081/admins/cas/exchange" "${admin_delegated_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_delegated_token}" "http://localhost:8081/admins" | jq -e '.data.username == "admin"' >/dev/null

  log "admin delegated back-channel logout"
  local admin_delegated_logout_request
  admin_delegated_logout_request="$(build_logout_request "${admin_delegated_ticket}")"
  curl -fsS -X POST "${admin_delegated_service_url}" --data-urlencode "logoutRequest=${admin_delegated_logout_request}" >/dev/null
  expect_auth_rejected "admin delegated revoked token" "http://localhost:8081/admins" "${admin_delegated_token}"
}
