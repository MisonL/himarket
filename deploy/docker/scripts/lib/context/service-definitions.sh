#!/usr/bin/env bash

verify_portal_cas_service_definitions() {
  log "verify developer cas providers"
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-saml1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas2")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-mfa")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-delegated")' >/dev/null

  log "verify developer oauth2 browser providers"
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="cas-jwt-validate-json" and .interactiveBrowserLogin==true)' >/dev/null
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="cas-jwt-validate-xml" and .interactiveBrowserLogin==true)' >/dev/null
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="cas-jwt-validate-saml" and .interactiveBrowserLogin==true)' >/dev/null
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="cas-jwt-ticket" and .interactiveBrowserLogin==true)' >/dev/null
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="jwt-bearer" and .directTokenLogin==true)' >/dev/null
  curl -fsS "http://localhost:8081/developers/oauth2/providers" | jq -e '.data[]? | select(.provider=="trusted-header" and .trustedHeaderLogin==true)' >/dev/null

  log "preview portal cas service definition"
  local cas_definition
  cas_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas/service-definition")"
  echo "${cas_definition}" | jq -e '.data.serviceId? // .serviceId' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.proxyPolicy // .proxyPolicy).pattern // "") | contains("/developers/cas/proxy-callback")' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 7' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.responseType // .responseType) == "POST"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.logoutType // .logoutType) == "FRONT_CHANNEL"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5173/forbidden"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.proxyPolicy // .proxyPolicy)["@class"] == "org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.proxyPolicy // .proxyPolicy).useServiceId == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.proxyPolicy // .proxyPolicy).exactMatch == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.attributeReleasePolicy // .attributeReleasePolicy)["@class"] == "org.apereo.cas.services.ReturnAllAttributeReleasePolicy"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).failureMode == "CLOSED"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).principalAttributeNameTrigger == "memberOf"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).principalAttributeValueToMatch == "internal"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).bypassIfMissingPrincipalAttribute == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).forceExecution == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.authenticationPolicy // .authenticationPolicy)["@class"] == "org.apereo.cas.services.DefaultRegisteredServiceAuthenticationPolicy"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllowedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${cas_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]' >/dev/null
  echo "${cas_definition}" | jq -e '(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).expirationDate == "2030-12-31T23:59:59Z"' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).deleteWhenExpired == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).notifyWhenExpired == true' >/dev/null
  echo "${cas_definition}" | jq -e '(.data.expirationPolicy // .expirationPolicy).notifyWhenDeleted == true' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].name // "") == "Portal SRE"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].email // "") == "sre@example.com"' >/dev/null
  echo "${cas_definition}" | jq -e '((.data.contacts // [])[0].department // "") == "Platform"' >/dev/null
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

  log "preview portal cas saml1 service definition"
  local saml1_definition
  saml1_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-saml1/service-definition")"
  echo "${saml1_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 11' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.responseType // .responseType) == "POST"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.logoutType // .logoutType) == "FRONT_CHANNEL"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"' >/dev/null
  echo "${saml1_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="SAML1")' >/dev/null
  echo "${saml1_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.NotPreventedAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${saml1_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["PreventedHandler"]' >/dev/null

  log "preview portal cas1 service definition"
  curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas1/service-definition" \
    | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS10")' >/dev/null

  log "preview portal cas2 service definition"
  local cas2_definition
  cas2_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas2/service-definition")"
  echo "${cas2_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS20")' >/dev/null
  echo "${cas2_definition}" | jq -e '((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"' >/dev/null
  echo "${cas2_definition}" | jq -e '[(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]' >/dev/null
  echo "${cas2_definition}" | jq -e '(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true' >/dev/null

  log "preview portal cas header service definition"
  local header_definition
  header_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-header/service-definition")"
  echo "${header_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 3' >/dev/null
  echo "${header_definition}" | jq -e '(.data.responseType // .responseType) == "HEADER"' >/dev/null
  echo "${header_definition}" | jq -e '(.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")' >/dev/null

  log "preview portal cas mfa service definition"
  local mfa_definition
  mfa_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-mfa/service-definition")"
  echo "${mfa_definition}" | jq -e '(.data.evaluationOrder // .evaluationOrder) == 5' >/dev/null
  echo "${mfa_definition}" | jq -e '(.data.serviceId // .serviceId) | contains("provider=\\Qcas-mfa\\E")' >/dev/null
  echo "${mfa_definition}" | jq -e '((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-simple")' >/dev/null
  echo "${mfa_definition}" | jq -e '(.data.multifactorPolicy // .multifactorPolicy).forceExecution == true' >/dev/null

  log "preview portal cas delegated service definition"
  local delegated_definition
  delegated_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-delegated/service-definition")"
  echo "${delegated_definition}" | jq -e '(.data.serviceId // .serviceId) | contains("provider=\\Qcas-delegated\\E")' >/dev/null
  echo "${delegated_definition}" | jq -e '(((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="MockOidcClient")' >/dev/null
  echo "${delegated_definition}" | jq -e '((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5173/delegated-forbidden"' >/dev/null
  echo "${delegated_definition}" | jq -e '(.data.accessStrategy // .accessStrategy)["@class"] == "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy"' >/dev/null
  echo "${delegated_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredIpAddressesPatterns // [])[1][]?] | any(.[]?; contains("127"))' >/dev/null
  echo "${delegated_definition}" | jq -e '[((.data.accessStrategy // .accessStrategy).requiredUserAgentPatterns // [])[1][]?] | any(.[]?; contains("^curl/"))' >/dev/null
  echo "${delegated_definition}" | jq -e '(((.data.accessStrategy // .accessStrategy).requiredHeaders // {})["X-Portal-Scope"] // "") == "developer"' >/dev/null
}
