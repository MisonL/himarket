#!/usr/bin/env bash

phase_ldap() {
  log "verify ldap login (developer)"
  curl -fsS "${HIMARKET_BASE_URL}/developers/ldap/providers" | jq -e '.data[]? | select(.provider=="ldap")' >/dev/null
  local ldap_login
  for _ in {1..10}; do
    if ldap_login="$(curl -fsS -X POST "${HIMARKET_BASE_URL}/developers/ldap/login" -H 'Content-Type: application/json' -d '{"provider":"ldap","username":"alice","password":"alice"}')"; then
      break
    fi
    sleep 2
  done
  if [[ -z "${ldap_login:-}" ]]; then
    err "developer ldap login failed"
    exit 1
  fi
  echo "${ldap_login}" | jq -e '.data.access_token | length > 0' >/dev/null

  log "verify admin ldap login"
  curl -fsS "${HIMARKET_BASE_URL}/admins/ldap/providers" | jq -e '.data[]? | select(.provider=="ldap")' >/dev/null
  local admin_ldap
  for _ in {1..10}; do
    if admin_ldap="$(curl -fsS -X POST "${HIMARKET_BASE_URL}/admins/ldap/login" -H 'Content-Type: application/json' -d '{"provider":"ldap","username":"admin","password":"admin"}')"; then
      break
    fi
    sleep 2
  done
  if [[ -z "${admin_ldap:-}" ]]; then
    err "admin ldap login failed"
    exit 1
  fi
  echo "${admin_ldap}" | jq -e '.data.access_token | length > 0' >/dev/null
}
