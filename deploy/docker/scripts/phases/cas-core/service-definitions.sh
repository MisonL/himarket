#!/usr/bin/env bash

verify_admin_cas_service_definitions() {
  log "verify admin cas providers"
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-saml1")' >/dev/null
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas1")' >/dev/null
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas2")' >/dev/null
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-mfa")' >/dev/null
  curl -fsS "${HIMARKET_BASE_URL}/admins/cas/providers" | jq -e '.data[]? | select(.provider=="cas-delegated")' >/dev/null

  log "preview admin cas service definition"
  local cas_definition
  cas_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas/service-definition")"
  echo "${cas_definition}" | jq -e '(.data.serviceId // .serviceId) | contains("/admins/cas/callback")' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 9' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.responseType // .responseType) == "POST"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.logoutType // .logoutType) == "FRONT_CHANNEL"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.logoutUrl // .logoutUrl) == "http://localhost:5174/login"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5174/forbidden"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.proxyPolicy // .proxyPolicy)["@class"] == "org.apereo.cas.services.RestfulRegisteredServiceProxyPolicy"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.proxyPolicy // .proxyPolicy).endpoint == "https://proxy.example.com/policies"' >/dev/null
  echo "${cas_definition}" | jq -e '(((.data.proxyPolicy // .proxyPolicy).headers // {})["X-Proxy-Policy"] // "") == "enabled"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.attributeReleasePolicy // .attributeReleasePolicy)["@class"] == "org.apereo.cas.services.DenyAllAttributeReleasePolicy"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).failureMode == "OPEN"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).forceExecution == true' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.ExcludedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${cas_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["BlockedHandler"]' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).expirationDate == "2030-12-31T23:59:59Z"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).deleteWhenExpired == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).notifyWhenExpired == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).notifyWhenDeleted == true' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].name // "") == "Admin SRE"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].email // "") == "admin-sre@example.com"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].department // "") == "Operations"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].type // "") == "TECHNICAL"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.accessStrategy // .accessStrategy).startingDateTime == "2026-01-01T09:00:00"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.accessStrategy // .accessStrategy).endingDateTime == "2026-12-31T18:00:00"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.accessStrategy // .accessStrategy).zoneId == "Asia/Shanghai"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.accessStrategy // .accessStrategy).requireAllAttributes == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.accessStrategy // .accessStrategy).caseInsensitive == true' >/dev/null
  echo "${cas_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredAttributes.memberOf // [])[1][]?] == ["internal", "ops"]' >/dev/null
  echo "${cas_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredAttributes.region // [])[1][]?] == ["cn"]' >/dev/null
  echo "${cas_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).rejectedAttributes.status // [])[1][]?] == ["disabled"]' >/dev/null
  echo "${cas_definition}" | jq -e '(((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="GithubClient")' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).permitUndefined == false' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).exclusive == true' >/dev/null

  log "preview admin cas saml1 service definition"
  local saml1_definition
  saml1_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas-saml1/service-definition")"
  echo "${saml1_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 13' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.responseType // .responseType) == "POST"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.logoutType // .logoutType) == "FRONT_CHANNEL"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.logoutUrl // .logoutUrl) == "http://localhost:5174/login"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="SAML1")' >/dev/null

  log "preview admin cas1 service definition"
  curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas1/service-definition" \
    | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS10")' >/dev/null

  log "preview admin cas2 service definition"
  local cas2_definition
  cas2_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas2/service-definition")"
  echo "${cas2_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS20")' >/dev/null
  echo "${cas2_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${cas2_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]' >/dev/null
  echo "${cas2_definition}" | jq -e '(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true' >/dev/null

  log "preview admin cas header service definition"
  local header_definition
  header_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas-header/service-definition")"
  echo "${header_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 3' >/dev/null
  echo "${header_definition}" | jq -e '(.data.responseType // .responseType) == "HEADER"' >/dev/null
  echo "${header_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")' >/dev/null

  log "preview admin cas mfa service definition"
  local mfa_definition
  mfa_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas-mfa/service-definition")"
  echo "${mfa_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 5' >/dev/null
  echo "${mfa_definition}" | jq -e '(.data.serviceId // .serviceId) | contains("provider=\\Qcas-mfa\\E")' >/dev/null
  echo "${mfa_definition}" | jq -e '((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-simple")' >/dev/null
  echo "${mfa_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AnyAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${mfa_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["SimpleTestUsernamePasswordAuthenticationHandler"]' >/dev/null
  echo "${mfa_definition}" | jq -e '(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true' >/dev/null
  echo "${mfa_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).forceExecution == true' >/dev/null

  log "preview admin cas delegated service definition"
  local delegated_definition
  delegated_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "${HIMARKET_BASE_URL}/admins/cas/cas-delegated/service-definition")"
  echo "${delegated_definition}" | jq -e '(.data.serviceId // .serviceId) | contains("provider=\\Qcas-delegated\\E")' >/dev/null
  echo "${delegated_definition}" | jq -e '(((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="MockOidcClient")' >/dev/null
  echo "${delegated_definition}" | jq -e '(.data.accessStrategy // .accessStrategy)["@class"] == "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy"' >/dev/null
  echo "${delegated_definition}" | jq -e '(((.data.accessStrategy // .accessStrategy).requiredHeaders // {})["X-Portal-Scope"] // "") == "admin"' >/dev/null
  echo "${delegated_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredIpAddressesPatterns // [])[1][]?] | any(.[]?; contains("127"))' >/dev/null
  echo "${delegated_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredUserAgentPatterns // [])[1][]?] | any(.[]?; contains("^curl/"))' >/dev/null
}
