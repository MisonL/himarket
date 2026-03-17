#!/usr/bin/env bash

verify_admin_cas_protocol_flows() {
  log "admin cas authorize"
  verify_cas_browser_flow \
    "admin cas" \
    "http://localhost:8081/admins/cas/authorize?provider=cas" \
    "http://localhost:8081/admins/cas/exchange" \
    "http://localhost:8081/admins" \
    "admin" \
    "admin" \
    "none" \
    "cas login form (admin)"

  log "admin cas proxy ticket"
  curl -fsS -X POST "http://localhost:8081/admins/cas/proxy-ticket" \
    -H "Authorization: Bearer ${admin_cas_token}" \
    -H 'Content-Type: application/json' \
    -d '{"provider":"cas","targetService":"http://localhost:5174/admin-proxy-target"}' \
    | jq -e '.data.proxyTicket | startswith("PT-")' >/dev/null
  local admin_proxy_reject
  admin_proxy_reject="$(curl_json POST "http://localhost:8081/admins/cas/proxy-ticket" '{"provider":"cas","targetService":"http://localhost:5173/forbidden"}' "Authorization: Bearer ${admin_cas_token}")"
  [[ "$(echo "${admin_proxy_reject}" | head -n 1)" == "400" ]] || {
    err "admin cas proxy target policy should reject forbidden target service"
    echo "${admin_proxy_reject}" | tail -n +2 >&2
    exit 1
  }

  log "admin cas front-channel logout"
  trigger_front_channel_logout "admin cas" "${admin_cas_service_url}" "${admin_cas_ticket}"
  expect_auth_rejected "admin cas revoked token" "http://localhost:8081/admins" "${admin_cas_token}"

  log "admin cas saml1 authorize"
  verify_cas_browser_flow \
    "admin cas saml1" \
    "http://localhost:8081/admins/cas/authorize?provider=cas-saml1" \
    "http://localhost:8081/admins/cas/exchange" \
    "http://localhost:8081/admins" \
    "admin" \
    "admin" \
    "front" \
    "cas login form (admin saml1)"

  log "admin cas1 authorize"
  verify_cas_browser_flow \
    "admin cas1" \
    "http://localhost:8081/admins/cas/authorize?provider=cas1" \
    "http://localhost:8081/admins/cas/exchange" \
    "http://localhost:8081/admins" \
    "admin" \
    "admin" \
    "back" \
    "cas login form (admin cas1)"

  log "admin cas2 authorize"
  verify_cas_browser_flow \
    "admin cas2" \
    "http://localhost:8081/admins/cas/authorize?provider=cas2" \
    "http://localhost:8081/admins/cas/exchange" \
    "http://localhost:8081/admins" \
    "admin" \
    "admin" \
    "back" \
    "cas login form (admin cas2)"
}
