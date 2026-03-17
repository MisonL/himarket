#!/usr/bin/env bash

phase_cas_core() {
  log "developer cas authorize flag passthrough"
  local developer_flag_headers
  developer_flag_headers="$(mktemp)"
  curl -sS -D "${developer_flag_headers}" -o /dev/null \
    "http://localhost:8081/developers/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true" || true
  local developer_flag_redirect
  developer_flag_redirect="$(extract_header_value "$(cat "${developer_flag_headers}")" "Location")"
  if [[ -z "${developer_flag_redirect}" ]]; then
    err "missing redirect location from developer cas flag authorize"
    cat "${developer_flag_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${developer_flag_redirect}" "gateway")" == "true" ]] || {
    err "developer cas gateway flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${developer_flag_redirect}" "warn")" == "true" ]] || {
    err "developer cas warn flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${developer_flag_redirect}" "rememberMe")" == "true" ]] || {
    err "developer cas rememberMe flag missing from redirect"
    echo "${developer_flag_redirect}" >&2
    exit 1
  }

  log "admin cas authorize flag passthrough"
  local admin_flag_headers
  admin_flag_headers="$(mktemp)"
  curl -sS -D "${admin_flag_headers}" -o /dev/null \
    "http://localhost:8081/admins/cas/authorize?provider=cas&gateway=true&warn=true&rememberMe=true" || true
  local admin_flag_redirect
  admin_flag_redirect="$(extract_header_value "$(cat "${admin_flag_headers}")" "Location")"
  if [[ -z "${admin_flag_redirect}" ]]; then
    err "missing redirect location from admin cas flag authorize"
    cat "${admin_flag_headers}" >&2
    exit 1
  fi
  [[ "$(parse_query_param "${admin_flag_redirect}" "gateway")" == "true" ]] || {
    err "admin cas gateway flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${admin_flag_redirect}" "warn")" == "true" ]] || {
    err "admin cas warn flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }
  [[ "$(parse_query_param "${admin_flag_redirect}" "rememberMe")" == "true" ]] || {
    err "admin cas rememberMe flag missing from redirect"
    echo "${admin_flag_redirect}" >&2
    exit 1
  }

  log "developer cas authorize (capture state cookie and service url)"
  local cas_headers
  local cookie_jar
  cookie_jar="$(mktemp)"
  cas_headers="$(mktemp)"
  curl -sS -D "${cas_headers}" -o /dev/null -c "${cookie_jar}" "http://localhost:8081/developers/cas/authorize?provider=cas" || true
  local redirect
  redirect="$(extract_header_value "$(cat "${cas_headers}")" "Location")"
  if [[ -z "${redirect}" ]]; then
    err "missing redirect location from cas authorize"
    cat "${cas_headers}" >&2
    exit 1
  fi
  local encoded_service
  encoded_service="$(parse_query_param "${redirect}" "service")"
  if [[ -z "${encoded_service}" ]]; then
    err "missing service param in redirect"
    echo "${redirect}" >&2
    exit 1
  fi
  local service_url
  service_url="$(url_decode "${encoded_service}")"
  local state
  state="$(parse_query_param "${service_url}" "state")"
  if [[ -z "${state}" ]]; then
    err "missing state param in service url"
    echo "${service_url}" >&2
    exit 1
  fi

  log "cas login form (developer)"
  local developer_login_html
  developer_login_html="$(curl -fsS -b "${cookie_jar}" -c "${cookie_jar}" "${redirect}")"
  local developer_execution
  developer_execution="$(extract_html_input_value "${developer_login_html}" "execution")"
  if [[ -z "${developer_execution}" ]]; then
    err "missing execution token from developer cas login page"
    exit 1
  fi
  local developer_login_headers
  developer_login_headers="$(mktemp)"
  curl -sS -D "${developer_login_headers}" -o /dev/null -b "${cookie_jar}" -c "${cookie_jar}" \
    -X POST "${redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "service=${service_url}" \
    --data-urlencode "execution=${developer_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_service_redirect
  developer_service_redirect="$(extract_header_value "$(cat "${developer_login_headers}")" "Location")"
  if [[ -z "${developer_service_redirect}" ]]; then
    err "missing callback redirect from developer cas login submit"
    cat "${developer_login_headers}" >&2
    exit 1
  fi
  local developer_ticket
  developer_ticket="$(parse_query_param "${developer_service_redirect}" "ticket")"
  if [[ -z "${developer_ticket}" ]]; then
    err "missing developer cas ticket in redirect"
    echo "${developer_service_redirect}" >&2
    exit 1
  fi

  log "himarket cas callback via public service url"
  local cas_callback_headers
  cas_callback_headers="$(mktemp)"
  curl -sS -G -D "${cas_callback_headers}" -o /dev/null -b "${cookie_jar}" \
    --data-urlencode "ticket=${developer_ticket}" \
    "${service_url}" || true
  local frontend_cas_redirect
  frontend_cas_redirect="$(extract_header_value "$(cat "${cas_callback_headers}")" "Location")"
  if [[ -z "${frontend_cas_redirect}" ]]; then
    err "missing frontend redirect from developer cas callback"
    cat "${cas_callback_headers}" >&2
    exit 1
  fi
  local developer_cas_code
  developer_cas_code="$(parse_query_param "${frontend_cas_redirect}" "code")"
  if [[ -z "${developer_cas_code}" ]]; then
    err "missing developer cas code in redirect"
    echo "${frontend_cas_redirect}" >&2
    exit 1
  fi
  local developer_cas_token
  developer_cas_token="$(exchange_code_for_token "developer cas" "http://localhost:8081/developers/cas/exchange" "${developer_cas_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas proxy ticket"
  curl -fsS -X POST "http://localhost:8081/developers/cas/proxy-ticket" \
    -H "Authorization: Bearer ${developer_cas_token}" \
    -H 'Content-Type: application/json' \
    -d '{"provider":"cas","targetService":"http://localhost:5173/proxy-target"}' \
    | jq -e '.data.proxyTicket | startswith("PT-")' >/dev/null
  local developer_proxy_reject
  developer_proxy_reject="$(curl_json POST "http://localhost:8081/developers/cas/proxy-ticket" '{"provider":"cas","targetService":"http://localhost:5174/forbidden"}' "Authorization: Bearer ${developer_cas_token}")"
  [[ "$(echo "${developer_proxy_reject}" | head -n 1)" == "400" ]] || {
    err "developer cas proxy target policy should reject forbidden target service"
    echo "${developer_proxy_reject}" | tail -n +2 >&2
    exit 1
  }

  log "developer cas front-channel logout"
  trigger_front_channel_logout "developer cas" "${service_url}" "${developer_ticket}"
  expect_auth_rejected "developer cas revoked token" "http://localhost:8081/developers/profile" "${developer_cas_token}"

  log "developer cas saml1 authorize"
  local developer_saml_cookie
  local developer_saml_headers
  developer_saml_cookie="$(mktemp)"
  developer_saml_headers="$(mktemp)"
  curl -sS -D "${developer_saml_headers}" -o /dev/null -c "${developer_saml_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas-saml1" || true
  local developer_saml_redirect
  developer_saml_redirect="$(extract_header_value "$(cat "${developer_saml_headers}")" "Location")"
  if [[ -z "${developer_saml_redirect}" ]]; then
    err "missing redirect location from developer cas saml1 authorize"
    cat "${developer_saml_headers}" >&2
    exit 1
  fi
  local developer_saml_service_encoded
  developer_saml_service_encoded="$(parse_query_param "${developer_saml_redirect}" "service")"
  local developer_saml_service_url
  developer_saml_service_url="$(url_decode "${developer_saml_service_encoded}")"

  log "cas login form (developer saml1)"
  local developer_saml_login_html
  developer_saml_login_html="$(curl -fsS -b "${developer_saml_cookie}" -c "${developer_saml_cookie}" "${developer_saml_redirect}")"
  local developer_saml_execution
  developer_saml_execution="$(extract_html_input_value "${developer_saml_login_html}" "execution")"
  if [[ -z "${developer_saml_execution}" ]]; then
    err "missing execution token from developer cas saml1 login page"
    exit 1
  fi
  local developer_saml_login_headers
  developer_saml_login_headers="$(mktemp)"
  curl -sS -D "${developer_saml_login_headers}" -o /dev/null -b "${developer_saml_cookie}" -c "${developer_saml_cookie}" \
    -X POST "${developer_saml_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "service=${developer_saml_service_url}" \
    --data-urlencode "execution=${developer_saml_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_saml_service_redirect
  developer_saml_service_redirect="$(extract_header_value "$(cat "${developer_saml_login_headers}")" "Location")"
  if [[ -z "${developer_saml_service_redirect}" ]]; then
    err "missing callback redirect from developer cas saml1 login submit"
    cat "${developer_saml_login_headers}" >&2
    exit 1
  fi
  local developer_saml_ticket
  developer_saml_ticket="$(parse_query_param "${developer_saml_service_redirect}" "ticket")"
  if [[ -z "${developer_saml_ticket}" ]]; then
    err "missing developer cas saml1 ticket in redirect"
    echo "${developer_saml_service_redirect}" >&2
    exit 1
  fi

  log "himarket cas saml1 callback via public service url"
  local developer_saml_callback_headers
  developer_saml_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_saml_callback_headers}" -o /dev/null -b "${developer_saml_cookie}" \
    --data-urlencode "ticket=${developer_saml_ticket}" \
    "${developer_saml_service_url}" || true
  local developer_saml_frontend_redirect
  developer_saml_frontend_redirect="$(extract_header_value "$(cat "${developer_saml_callback_headers}")" "Location")"
  if [[ -z "${developer_saml_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas saml1 callback"
    cat "${developer_saml_callback_headers}" >&2
    exit 1
  fi
  local developer_saml_code
  developer_saml_code="$(parse_query_param "${developer_saml_frontend_redirect}" "code")"
  if [[ -z "${developer_saml_code}" ]]; then
    err "missing developer cas saml1 code in redirect"
    echo "${developer_saml_frontend_redirect}" >&2
    exit 1
  fi
  local developer_saml_token
  developer_saml_token="$(exchange_code_for_token "developer cas saml1" "http://localhost:8081/developers/cas/exchange" "${developer_saml_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_saml_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas saml1 front-channel logout"
  trigger_front_channel_logout "developer cas saml1" "${developer_saml_service_url}" "${developer_saml_ticket}"
  expect_auth_rejected "developer cas saml1 revoked token" "http://localhost:8081/developers/profile" "${developer_saml_token}"

  log "developer cas1 authorize"
  local developer_cas1_cookie
  local developer_cas1_headers
  developer_cas1_cookie="$(mktemp)"
  developer_cas1_headers="$(mktemp)"
  curl -sS -D "${developer_cas1_headers}" -o /dev/null -c "${developer_cas1_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas1" || true
  local developer_cas1_redirect
  developer_cas1_redirect="$(extract_header_value "$(cat "${developer_cas1_headers}")" "Location")"
  if [[ -z "${developer_cas1_redirect}" ]]; then
    err "missing redirect location from developer cas1 authorize"
    cat "${developer_cas1_headers}" >&2
    exit 1
  fi
  local developer_cas1_service_encoded
  developer_cas1_service_encoded="$(parse_query_param "${developer_cas1_redirect}" "service")"
  local developer_cas1_service_url
  developer_cas1_service_url="$(url_decode "${developer_cas1_service_encoded}")"

  log "cas login form (developer cas1)"
  local developer_cas1_login_html
  developer_cas1_login_html="$(curl -fsS -b "${developer_cas1_cookie}" -c "${developer_cas1_cookie}" "${developer_cas1_redirect}")"
  local developer_cas1_execution
  developer_cas1_execution="$(extract_html_input_value "${developer_cas1_login_html}" "execution")"
  if [[ -z "${developer_cas1_execution}" ]]; then
    err "missing execution token from developer cas1 login page"
    exit 1
  fi
  local developer_cas1_login_headers
  developer_cas1_login_headers="$(mktemp)"
  curl -sS -D "${developer_cas1_login_headers}" -o /dev/null -b "${developer_cas1_cookie}" -c "${developer_cas1_cookie}" \
    -X POST "${developer_cas1_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "service=${developer_cas1_service_url}" \
    --data-urlencode "execution=${developer_cas1_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_cas1_service_redirect
  developer_cas1_service_redirect="$(extract_header_value "$(cat "${developer_cas1_login_headers}")" "Location")"
  if [[ -z "${developer_cas1_service_redirect}" ]]; then
    err "missing callback redirect from developer cas1 login submit"
    cat "${developer_cas1_login_headers}" >&2
    exit 1
  fi
  local developer_cas1_ticket
  developer_cas1_ticket="$(parse_query_param "${developer_cas1_service_redirect}" "ticket")"
  if [[ -z "${developer_cas1_ticket}" ]]; then
    err "missing developer cas1 ticket in redirect"
    echo "${developer_cas1_service_redirect}" >&2
    exit 1
  fi

  log "himarket cas1 callback via public service url"
  local developer_cas1_callback_headers
  developer_cas1_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_cas1_callback_headers}" -o /dev/null -b "${developer_cas1_cookie}" \
    --data-urlencode "ticket=${developer_cas1_ticket}" \
    "${developer_cas1_service_url}" || true
  local developer_cas1_frontend_redirect
  developer_cas1_frontend_redirect="$(extract_header_value "$(cat "${developer_cas1_callback_headers}")" "Location")"
  if [[ -z "${developer_cas1_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas1 callback"
    cat "${developer_cas1_callback_headers}" >&2
    exit 1
  fi
  local developer_cas1_code
  developer_cas1_code="$(parse_query_param "${developer_cas1_frontend_redirect}" "code")"
  if [[ -z "${developer_cas1_code}" ]]; then
    err "missing developer cas1 code in redirect"
    echo "${developer_cas1_frontend_redirect}" >&2
    exit 1
  fi
  local developer_cas1_token
  developer_cas1_token="$(exchange_code_for_token "developer cas1" "http://localhost:8081/developers/cas/exchange" "${developer_cas1_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas1_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas1 back-channel logout"
  local developer_cas1_logout_request
  developer_cas1_logout_request="$(build_logout_request "${developer_cas1_ticket}")"
  curl -fsS -X POST "${developer_cas1_service_url}" --data-urlencode "logoutRequest=${developer_cas1_logout_request}" >/dev/null
  expect_auth_rejected "developer cas1 revoked token" "http://localhost:8081/developers/profile" "${developer_cas1_token}"

  log "developer cas2 authorize"
  local developer_cas2_cookie
  local developer_cas2_headers
  developer_cas2_cookie="$(mktemp)"
  developer_cas2_headers="$(mktemp)"
  curl -sS -D "${developer_cas2_headers}" -o /dev/null -c "${developer_cas2_cookie}" "http://localhost:8081/developers/cas/authorize?provider=cas2" || true
  local developer_cas2_redirect
  developer_cas2_redirect="$(extract_header_value "$(cat "${developer_cas2_headers}")" "Location")"
  if [[ -z "${developer_cas2_redirect}" ]]; then
    err "missing redirect location from developer cas2 authorize"
    cat "${developer_cas2_headers}" >&2
    exit 1
  fi
  local developer_cas2_service_encoded
  developer_cas2_service_encoded="$(parse_query_param "${developer_cas2_redirect}" "service")"
  local developer_cas2_service_url
  developer_cas2_service_url="$(url_decode "${developer_cas2_service_encoded}")"

  log "cas login form (developer cas2)"
  local developer_cas2_login_html
  developer_cas2_login_html="$(curl -fsS -b "${developer_cas2_cookie}" -c "${developer_cas2_cookie}" "${developer_cas2_redirect}")"
  local developer_cas2_execution
  developer_cas2_execution="$(extract_html_input_value "${developer_cas2_login_html}" "execution")"
  if [[ -z "${developer_cas2_execution}" ]]; then
    err "missing execution token from developer cas2 login page"
    exit 1
  fi
  local developer_cas2_login_headers
  developer_cas2_login_headers="$(mktemp)"
  curl -sS -D "${developer_cas2_login_headers}" -o /dev/null -b "${developer_cas2_cookie}" -c "${developer_cas2_cookie}" \
    -X POST "${developer_cas2_redirect}" \
    --data-urlencode "username=alice" \
    --data-urlencode "password=alice" \
    --data-urlencode "service=${developer_cas2_service_url}" \
    --data-urlencode "execution=${developer_cas2_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local developer_cas2_service_redirect
  developer_cas2_service_redirect="$(extract_header_value "$(cat "${developer_cas2_login_headers}")" "Location")"
  if [[ -z "${developer_cas2_service_redirect}" ]]; then
    err "missing callback redirect from developer cas2 login submit"
    cat "${developer_cas2_login_headers}" >&2
    exit 1
  fi
  local developer_cas2_ticket
  developer_cas2_ticket="$(parse_query_param "${developer_cas2_service_redirect}" "ticket")"
  if [[ -z "${developer_cas2_ticket}" ]]; then
    err "missing developer cas2 ticket in redirect"
    echo "${developer_cas2_service_redirect}" >&2
    exit 1
  fi

  log "himarket cas2 callback via public service url"
  local developer_cas2_callback_headers
  developer_cas2_callback_headers="$(mktemp)"
  curl -sS -G -D "${developer_cas2_callback_headers}" -o /dev/null -b "${developer_cas2_cookie}" \
    --data-urlencode "ticket=${developer_cas2_ticket}" \
    "${developer_cas2_service_url}" || true
  local developer_cas2_frontend_redirect
  developer_cas2_frontend_redirect="$(extract_header_value "$(cat "${developer_cas2_callback_headers}")" "Location")"
  if [[ -z "${developer_cas2_frontend_redirect}" ]]; then
    err "missing frontend redirect from developer cas2 callback"
    cat "${developer_cas2_callback_headers}" >&2
    exit 1
  fi
  local developer_cas2_code
  developer_cas2_code="$(parse_query_param "${developer_cas2_frontend_redirect}" "code")"
  if [[ -z "${developer_cas2_code}" ]]; then
    err "missing developer cas2 code in redirect"
    echo "${developer_cas2_frontend_redirect}" >&2
    exit 1
  fi
  local developer_cas2_token
  developer_cas2_token="$(exchange_code_for_token "developer cas2" "http://localhost:8081/developers/cas/exchange" "${developer_cas2_code}")"
  curl -fsS -H "Authorization: Bearer ${developer_cas2_token}" "http://localhost:8081/developers/profile" >/dev/null

  log "developer cas2 back-channel logout"
  local developer_cas2_logout_request
  developer_cas2_logout_request="$(build_logout_request "${developer_cas2_ticket}")"
  curl -fsS -X POST "${developer_cas2_service_url}" --data-urlencode "logoutRequest=${developer_cas2_logout_request}" >/dev/null
  expect_auth_rejected "developer cas2 revoked token" "http://localhost:8081/developers/profile" "${developer_cas2_token}"

  log "verify admin cas providers"
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-saml1")' >/dev/null
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas1")' >/dev/null
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas2")' >/dev/null
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-mfa")' >/dev/null
  curl -fsS "http://localhost:8081/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-delegated")' >/dev/null

  log "preview admin cas service definition"
  local admin_cas_service_definition
  admin_cas_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas/service-definition")"
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("/admins/cas/callback")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 9
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5174/login"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5174/forbidden"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.proxyPolicy // .proxyPolicy)["@class"] == "org.apereo.cas.services.RestfulRegisteredServiceProxyPolicy"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.proxyPolicy // .proxyPolicy).endpoint == "https://proxy.example.com/policies"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (((.data.proxyPolicy // .proxyPolicy).headers // {})["X-Proxy-Policy"] // "") == "enabled"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.attributeReleasePolicy // .attributeReleasePolicy)["@class"] == "org.apereo.cas.services.DenyAllAttributeReleasePolicy"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).failureMode == "OPEN"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.ExcludedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["BlockedHandler"]
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).expirationDate == "2030-12-31T23:59:59Z"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).deleteWhenExpired == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).notifyWhenExpired == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).notifyWhenDeleted == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].name // "") == "Admin SRE"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].email // "") == "admin-sre@example.com"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].department // "") == "Operations"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].type // "") == "TECHNICAL"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).startingDateTime == "2026-01-01T09:00:00"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).endingDateTime == "2026-12-31T18:00:00"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).zoneId == "Asia/Shanghai"
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).requireAllAttributes == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).caseInsensitive == true
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredAttributes.memberOf // [])[1][]?] == ["internal", "ops"]
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredAttributes.region // [])[1][]?] == ["cn"]
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).rejectedAttributes.status // [])[1][]?] == ["disabled"]
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="GithubClient")
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).permitUndefined == false
  ' >/dev/null
  echo "${admin_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).exclusive == true
  ' >/dev/null

  log "preview admin cas saml1 service definition"
  local admin_cas_saml1_service_definition
  admin_cas_saml1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas-saml1/service-definition")"
  echo "${admin_cas_saml1_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 13
  ' >/dev/null
  echo "${admin_cas_saml1_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${admin_cas_saml1_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${admin_cas_saml1_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5174/login"
  ' >/dev/null
  echo "${admin_cas_saml1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="SAML1")
  ' >/dev/null

  log "preview admin cas1 service definition"
  local admin_cas1_service_definition
  admin_cas1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas1/service-definition")"
  echo "${admin_cas1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS10")
  ' >/dev/null

  log "preview admin cas2 service definition"
  local admin_cas2_service_definition
  admin_cas2_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas2/service-definition")"
  echo "${admin_cas2_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS20")
  ' >/dev/null
  echo "${admin_cas2_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${admin_cas2_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]
  ' >/dev/null
  echo "${admin_cas2_service_definition}" | jq -e '
    (((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true
  ' >/dev/null

  log "preview admin cas header service definition"
  local admin_cas_header_service_definition
  admin_cas_header_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas-header/service-definition")"
  echo "${admin_cas_header_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 3
  ' >/dev/null
  echo "${admin_cas_header_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "HEADER"
  ' >/dev/null
  echo "${admin_cas_header_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")
  ' >/dev/null

  log "preview admin cas mfa service definition"
  local admin_cas_mfa_service_definition
  admin_cas_mfa_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas-mfa/service-definition")"
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 5
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("provider=\\Qcas-mfa\\E")
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-simple")
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AnyAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["SimpleTestUsernamePasswordAuthenticationHandler"]
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    (((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true
  ' >/dev/null

  log "preview admin cas delegated service definition"
  local admin_cas_delegated_service_definition
  admin_cas_delegated_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/admins/cas/cas-delegated/service-definition")"
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("provider=\\Qcas-delegated\\E")
  ' >/dev/null
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="MockOidcClient")
  ' >/dev/null
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy)["@class"] == "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy"
  ' >/dev/null
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).requiredHeaders // {})["X-Portal-Scope"] // "") == "admin"
  ' >/dev/null
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredIpAddressesPatterns // [])[1][]?]
    | any(.[]?; contains("127"))
  ' >/dev/null
  echo "${admin_cas_delegated_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredUserAgentPatterns // [])[1][]?]
    | any(.[]?; contains("^curl/"))
  ' >/dev/null
  echo "${admin_cas_mfa_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null

  log "admin cas authorize"
  local admin_cookie
  local admin_headers
  admin_cookie="$(mktemp)"
  admin_headers="$(mktemp)"
  curl -sS -D "${admin_headers}" -o /dev/null -c "${admin_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas" || true
  local admin_redirect
  admin_redirect="$(extract_header_value "$(cat "${admin_headers}")" "Location")"
  if [[ -z "${admin_redirect}" ]]; then
    err "missing redirect location from admin cas authorize"
    cat "${admin_headers}" >&2
    exit 1
  fi
  local admin_service_encoded
  admin_service_encoded="$(parse_query_param "${admin_redirect}" "service")"
  local admin_service_url
  admin_service_url="$(url_decode "${admin_service_encoded}")"
  local admin_state
  admin_state="$(parse_query_param "${admin_service_url}" "state")"

  log "cas login form (admin)"
  local admin_login_html
  admin_login_html="$(curl -fsS -b "${admin_cookie}" -c "${admin_cookie}" "${admin_redirect}")"
  local admin_execution
  admin_execution="$(extract_html_input_value "${admin_login_html}" "execution")"
  if [[ -z "${admin_execution}" ]]; then
    err "missing execution token from admin cas login page"
    exit 1
  fi
  local admin_login_headers
  admin_login_headers="$(mktemp)"
  curl -sS -D "${admin_login_headers}" -o /dev/null -b "${admin_cookie}" -c "${admin_cookie}" \
    -X POST "${admin_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "service=${admin_service_url}" \
    --data-urlencode "execution=${admin_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_service_redirect
  admin_service_redirect="$(extract_header_value "$(cat "${admin_login_headers}")" "Location")"
  if [[ -z "${admin_service_redirect}" ]]; then
    err "missing callback redirect from admin cas login submit"
    cat "${admin_login_headers}" >&2
    exit 1
  fi
  local admin_ticket
  admin_ticket="$(parse_query_param "${admin_service_redirect}" "ticket")"
  if [[ -z "${admin_ticket}" ]]; then
    err "missing admin cas ticket in redirect"
    echo "${admin_service_redirect}" >&2
    exit 1
  fi

  log "himarket admin cas callback via public service url"
  local admin_callback_headers
  admin_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_callback_headers}" -o /dev/null -b "${admin_cookie}" \
    --data-urlencode "ticket=${admin_ticket}" \
    "${admin_service_url}" || true
  local admin_frontend_redirect
  admin_frontend_redirect="$(extract_header_value "$(cat "${admin_callback_headers}")" "Location")"
  if [[ -z "${admin_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas callback"
    cat "${admin_callback_headers}" >&2
    exit 1
  fi
  local admin_cas_code
  admin_cas_code="$(parse_query_param "${admin_frontend_redirect}" "code")"
  if [[ -z "${admin_cas_code}" ]]; then
    err "missing admin cas code in redirect"
    echo "${admin_frontend_redirect}" >&2
    exit 1
  fi
  local admin_cas_token
  admin_cas_token="$(exchange_code_for_token "admin cas" "http://localhost:8081/admins/cas/exchange" "${admin_cas_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_cas_token}" "http://localhost:8081/admins" >/dev/null

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
  trigger_front_channel_logout "admin cas" "${admin_service_url}" "${admin_ticket}"
  expect_auth_rejected "admin cas revoked token" "http://localhost:8081/admins" "${admin_cas_token}"

  log "admin cas saml1 authorize"
  local admin_saml_cookie
  local admin_saml_headers
  admin_saml_cookie="$(mktemp)"
  admin_saml_headers="$(mktemp)"
  curl -sS -D "${admin_saml_headers}" -o /dev/null -c "${admin_saml_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas-saml1" || true
  local admin_saml_redirect
  admin_saml_redirect="$(extract_header_value "$(cat "${admin_saml_headers}")" "Location")"
  if [[ -z "${admin_saml_redirect}" ]]; then
    err "missing redirect location from admin cas saml1 authorize"
    cat "${admin_saml_headers}" >&2
    exit 1
  fi
  local admin_saml_service_encoded
  admin_saml_service_encoded="$(parse_query_param "${admin_saml_redirect}" "service")"
  local admin_saml_service_url
  admin_saml_service_url="$(url_decode "${admin_saml_service_encoded}")"

  log "cas login form (admin saml1)"
  local admin_saml_login_html
  admin_saml_login_html="$(curl -fsS -b "${admin_saml_cookie}" -c "${admin_saml_cookie}" "${admin_saml_redirect}")"
  local admin_saml_execution
  admin_saml_execution="$(extract_html_input_value "${admin_saml_login_html}" "execution")"
  if [[ -z "${admin_saml_execution}" ]]; then
    err "missing execution token from admin cas saml1 login page"
    exit 1
  fi
  local admin_saml_login_headers
  admin_saml_login_headers="$(mktemp)"
  curl -sS -D "${admin_saml_login_headers}" -o /dev/null -b "${admin_saml_cookie}" -c "${admin_saml_cookie}" \
    -X POST "${admin_saml_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "service=${admin_saml_service_url}" \
    --data-urlencode "execution=${admin_saml_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_saml_service_redirect
  admin_saml_service_redirect="$(extract_header_value "$(cat "${admin_saml_login_headers}")" "Location")"
  if [[ -z "${admin_saml_service_redirect}" ]]; then
    err "missing callback redirect from admin cas saml1 login submit"
    cat "${admin_saml_login_headers}" >&2
    exit 1
  fi
  local admin_saml_ticket
  admin_saml_ticket="$(parse_query_param "${admin_saml_service_redirect}" "ticket")"
  if [[ -z "${admin_saml_ticket}" ]]; then
    err "missing admin cas saml1 ticket in redirect"
    echo "${admin_saml_service_redirect}" >&2
    exit 1
  fi

  log "himarket admin cas saml1 callback via public service url"
  local admin_saml_callback_headers
  admin_saml_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_saml_callback_headers}" -o /dev/null -b "${admin_saml_cookie}" \
    --data-urlencode "ticket=${admin_saml_ticket}" \
    "${admin_saml_service_url}" || true
  local admin_saml_frontend_redirect
  admin_saml_frontend_redirect="$(extract_header_value "$(cat "${admin_saml_callback_headers}")" "Location")"
  if [[ -z "${admin_saml_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas saml1 callback"
    cat "${admin_saml_callback_headers}" >&2
    exit 1
  fi
  local admin_saml_code
  admin_saml_code="$(parse_query_param "${admin_saml_frontend_redirect}" "code")"
  if [[ -z "${admin_saml_code}" ]]; then
    err "missing admin cas saml1 code in redirect"
    echo "${admin_saml_frontend_redirect}" >&2
    exit 1
  fi
  local admin_saml_token
  admin_saml_token="$(exchange_code_for_token "admin cas saml1" "http://localhost:8081/admins/cas/exchange" "${admin_saml_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_saml_token}" "http://localhost:8081/admins" >/dev/null

  log "admin cas saml1 front-channel logout"
  trigger_front_channel_logout "admin cas saml1" "${admin_saml_service_url}" "${admin_saml_ticket}"
  expect_auth_rejected "admin cas saml1 revoked token" "http://localhost:8081/admins" "${admin_saml_token}"

  log "admin cas1 authorize"
  local admin_cas1_cookie
  local admin_cas1_headers
  admin_cas1_cookie="$(mktemp)"
  admin_cas1_headers="$(mktemp)"
  curl -sS -D "${admin_cas1_headers}" -o /dev/null -c "${admin_cas1_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas1" || true
  local admin_cas1_redirect
  admin_cas1_redirect="$(extract_header_value "$(cat "${admin_cas1_headers}")" "Location")"
  if [[ -z "${admin_cas1_redirect}" ]]; then
    err "missing redirect location from admin cas1 authorize"
    cat "${admin_cas1_headers}" >&2
    exit 1
  fi
  local admin_cas1_service_encoded
  admin_cas1_service_encoded="$(parse_query_param "${admin_cas1_redirect}" "service")"
  local admin_cas1_service_url
  admin_cas1_service_url="$(url_decode "${admin_cas1_service_encoded}")"

  log "cas login form (admin cas1)"
  local admin_cas1_login_html
  admin_cas1_login_html="$(curl -fsS -b "${admin_cas1_cookie}" -c "${admin_cas1_cookie}" "${admin_cas1_redirect}")"
  local admin_cas1_execution
  admin_cas1_execution="$(extract_html_input_value "${admin_cas1_login_html}" "execution")"
  if [[ -z "${admin_cas1_execution}" ]]; then
    err "missing execution token from admin cas1 login page"
    exit 1
  fi
  local admin_cas1_login_headers
  admin_cas1_login_headers="$(mktemp)"
  curl -sS -D "${admin_cas1_login_headers}" -o /dev/null -b "${admin_cas1_cookie}" -c "${admin_cas1_cookie}" \
    -X POST "${admin_cas1_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "service=${admin_cas1_service_url}" \
    --data-urlencode "execution=${admin_cas1_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_cas1_service_redirect
  admin_cas1_service_redirect="$(extract_header_value "$(cat "${admin_cas1_login_headers}")" "Location")"
  if [[ -z "${admin_cas1_service_redirect}" ]]; then
    err "missing callback redirect from admin cas1 login submit"
    cat "${admin_cas1_login_headers}" >&2
    exit 1
  fi
  local admin_cas1_ticket
  admin_cas1_ticket="$(parse_query_param "${admin_cas1_service_redirect}" "ticket")"
  if [[ -z "${admin_cas1_ticket}" ]]; then
    err "missing admin cas1 ticket in redirect"
    echo "${admin_cas1_service_redirect}" >&2
    exit 1
  fi

  log "himarket admin cas1 callback via public service url"
  local admin_cas1_callback_headers
  admin_cas1_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_cas1_callback_headers}" -o /dev/null -b "${admin_cas1_cookie}" \
    --data-urlencode "ticket=${admin_cas1_ticket}" \
    "${admin_cas1_service_url}" || true
  local admin_cas1_frontend_redirect
  admin_cas1_frontend_redirect="$(extract_header_value "$(cat "${admin_cas1_callback_headers}")" "Location")"
  if [[ -z "${admin_cas1_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas1 callback"
    cat "${admin_cas1_callback_headers}" >&2
    exit 1
  fi
  local admin_cas1_code
  admin_cas1_code="$(parse_query_param "${admin_cas1_frontend_redirect}" "code")"
  if [[ -z "${admin_cas1_code}" ]]; then
    err "missing admin cas1 code in redirect"
    echo "${admin_cas1_frontend_redirect}" >&2
    exit 1
  fi
  local admin_cas1_token
  admin_cas1_token="$(exchange_code_for_token "admin cas1" "http://localhost:8081/admins/cas/exchange" "${admin_cas1_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_cas1_token}" "http://localhost:8081/admins" >/dev/null

  log "admin cas1 back-channel logout"
  local admin_cas1_logout_request
  admin_cas1_logout_request="$(build_logout_request "${admin_cas1_ticket}")"
  curl -fsS -X POST "${admin_cas1_service_url}" --data-urlencode "logoutRequest=${admin_cas1_logout_request}" >/dev/null
  expect_auth_rejected "admin cas1 revoked token" "http://localhost:8081/admins" "${admin_cas1_token}"

  log "admin cas2 authorize"
  local admin_cas2_cookie
  local admin_cas2_headers
  admin_cas2_cookie="$(mktemp)"
  admin_cas2_headers="$(mktemp)"
  curl -sS -D "${admin_cas2_headers}" -o /dev/null -c "${admin_cas2_cookie}" "http://localhost:8081/admins/cas/authorize?provider=cas2" || true
  local admin_cas2_redirect
  admin_cas2_redirect="$(extract_header_value "$(cat "${admin_cas2_headers}")" "Location")"
  if [[ -z "${admin_cas2_redirect}" ]]; then
    err "missing redirect location from admin cas2 authorize"
    cat "${admin_cas2_headers}" >&2
    exit 1
  fi
  local admin_cas2_service_encoded
  admin_cas2_service_encoded="$(parse_query_param "${admin_cas2_redirect}" "service")"
  local admin_cas2_service_url
  admin_cas2_service_url="$(url_decode "${admin_cas2_service_encoded}")"

  log "cas login form (admin cas2)"
  local admin_cas2_login_html
  admin_cas2_login_html="$(curl -fsS -b "${admin_cas2_cookie}" -c "${admin_cas2_cookie}" "${admin_cas2_redirect}")"
  local admin_cas2_execution
  admin_cas2_execution="$(extract_html_input_value "${admin_cas2_login_html}" "execution")"
  if [[ -z "${admin_cas2_execution}" ]]; then
    err "missing execution token from admin cas2 login page"
    exit 1
  fi
  local admin_cas2_login_headers
  admin_cas2_login_headers="$(mktemp)"
  curl -sS -D "${admin_cas2_login_headers}" -o /dev/null -b "${admin_cas2_cookie}" -c "${admin_cas2_cookie}" \
    -X POST "${admin_cas2_redirect}" \
    --data-urlencode "username=admin" \
    --data-urlencode "password=admin" \
    --data-urlencode "service=${admin_cas2_service_url}" \
    --data-urlencode "execution=${admin_cas2_execution}" \
    --data-urlencode "_eventId=submit" \
    --data-urlencode "geolocation=" || true
  local admin_cas2_service_redirect
  admin_cas2_service_redirect="$(extract_header_value "$(cat "${admin_cas2_login_headers}")" "Location")"
  if [[ -z "${admin_cas2_service_redirect}" ]]; then
    err "missing callback redirect from admin cas2 login submit"
    cat "${admin_cas2_login_headers}" >&2
    exit 1
  fi
  local admin_cas2_ticket
  admin_cas2_ticket="$(parse_query_param "${admin_cas2_service_redirect}" "ticket")"
  if [[ -z "${admin_cas2_ticket}" ]]; then
    err "missing admin cas2 ticket in redirect"
    echo "${admin_cas2_service_redirect}" >&2
    exit 1
  fi

  log "himarket admin cas2 callback via public service url"
  local admin_cas2_callback_headers
  admin_cas2_callback_headers="$(mktemp)"
  curl -sS -G -D "${admin_cas2_callback_headers}" -o /dev/null -b "${admin_cas2_cookie}" \
    --data-urlencode "ticket=${admin_cas2_ticket}" \
    "${admin_cas2_service_url}" || true
  local admin_cas2_frontend_redirect
  admin_cas2_frontend_redirect="$(extract_header_value "$(cat "${admin_cas2_callback_headers}")" "Location")"
  if [[ -z "${admin_cas2_frontend_redirect}" ]]; then
    err "missing frontend redirect from admin cas2 callback"
    cat "${admin_cas2_callback_headers}" >&2
    exit 1
  fi
  local admin_cas2_code
  admin_cas2_code="$(parse_query_param "${admin_cas2_frontend_redirect}" "code")"
  if [[ -z "${admin_cas2_code}" ]]; then
    err "missing admin cas2 code in redirect"
    echo "${admin_cas2_frontend_redirect}" >&2
    exit 1
  fi
  local admin_cas2_token
  admin_cas2_token="$(exchange_code_for_token "admin cas2" "http://localhost:8081/admins/cas/exchange" "${admin_cas2_code}")"
  curl -fsS -H "Authorization: Bearer ${admin_cas2_token}" "http://localhost:8081/admins" >/dev/null

  log "admin cas2 back-channel logout"
  local admin_cas2_logout_request
  admin_cas2_logout_request="$(build_logout_request "${admin_cas2_ticket}")"
  curl -fsS -X POST "${admin_cas2_service_url}" --data-urlencode "logoutRequest=${admin_cas2_logout_request}" >/dev/null
  expect_auth_rejected "admin cas2 revoked token" "http://localhost:8081/admins" "${admin_cas2_token}"
}
