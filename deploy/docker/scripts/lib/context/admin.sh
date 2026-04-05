#!/usr/bin/env bash

ensure_admin_token() {
  log "admin login"
  local login_result
  login_result="$(curl_json POST "${HIMARKET_BASE_URL}/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
  local login_code
  login_code="$(echo "${login_result}" | head -n 1)"
  if [[ "${login_code}" != 200 ]]; then
    log "init admin account"
    local init_result
    init_result="$(curl_json POST "${HIMARKET_BASE_URL}/admins/init" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    local init_code
    init_code="$(echo "${init_result}" | head -n 1)"
    if [[ "${init_code}" != 200 && "${init_code}" != 409 ]]; then
      err "admin init failed: http=${init_code}"
      echo "${init_result}" | tail -n +2 >&2
      exit 1
    fi

    log "admin login"
    login_result="$(curl_json POST "${HIMARKET_BASE_URL}/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    login_code="$(echo "${login_result}" | head -n 1)"
    if [[ "${login_code}" != 200 ]]; then
      err "admin login failed: http=${login_code}"
      echo "${login_result}" | tail -n +2 >&2
      exit 1
    fi
  fi
  local login_body
  login_body="$(echo "${login_result}" | tail -n +2)"
  admin_token="$(echo "${login_body}" | jq -r '.data.access_token')"
  if [[ -z "${admin_token}" || "${admin_token}" == "null" ]]; then
    err "cannot extract admin token"
    echo "${login_body}" >&2
    exit 1
  fi
}
