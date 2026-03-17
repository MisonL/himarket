#!/usr/bin/env bash

prepare_auth_phase_context() {
  log "admin login"
  local login_result
  login_result="$(curl_json POST "http://localhost:8081/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
  local login_code
  login_code="$(echo "${login_result}" | head -n 1)"
  if [[ "${login_code}" != 200 ]]; then
    log "init admin account"
    local init_result
    init_result="$(curl_json POST "http://localhost:8081/admins/init" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    local init_code
    init_code="$(echo "${init_result}" | head -n 1)"
    if [[ "${init_code}" != 200 && "${init_code}" != 409 ]]; then
      err "admin init failed: http=${init_code}"
      echo "${init_result}" | tail -n +2 >&2
      exit 1
    fi

    log "admin login"
    login_result="$(curl_json POST "http://localhost:8081/admins/login" "{\"username\":\"${admin_user}\",\"password\":\"${admin_pass}\"}")"
    login_code="$(echo "${login_result}" | head -n 1)"
    if [[ "${login_code}" != 200 ]]; then
      err "admin login failed: http=${login_code}"
      echo "${login_result}" | tail -n +2 >&2
      exit 1
    fi
  fi
  local login_body
  login_body="$(echo "${login_result}" | tail -n +2)"
  admin_token=""
  admin_token="$(echo "${login_body}" | jq -r '.data.access_token')"
  if [[ -z "${admin_token}" || "${admin_token}" == "null" ]]; then
    err "cannot extract admin token"
    echo "${login_body}" >&2
    exit 1
  fi

  log "get or create portal: ${portal_name}"
  local portals_json
  portals_json="$(curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals?page=0&size=20")"
  local localhost_portal_id
  localhost_portal_id="$(
    echo "${portals_json}" | jq -r '.data.content[]?.portalId' | while read -r candidate_portal_id; do
      [[ -z "${candidate_portal_id}" ]] && continue
      if curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${candidate_portal_id}" \
        | jq -e '.data.portalDomainConfig[]? | select(.domain=="localhost")' >/dev/null; then
        echo "${candidate_portal_id}"
        break
      fi
    done
  )"
  portal_id=""
  if [[ -n "${localhost_portal_id}" && "${localhost_portal_id}" != "null" ]]; then
    portal_id="${localhost_portal_id}"
  else
    portal_id="$(echo "${portals_json}" | jq -r --arg name "${portal_name}" '.data.content[]? | select(.name==$name) | .portalId' | head -n 1)"
  fi
  if [[ -z "${portal_id}" || "${portal_id}" == "null" ]]; then
    local create_resp
    create_resp="$(curl_json POST "http://localhost:8081/portals" "{\"name\":\"${portal_name}\"}" "Authorization: Bearer ${admin_token}")"
    local create_code
    create_code="$(echo "${create_resp}" | head -n 1)"
    if [[ "${create_code}" != 200 ]]; then
      err "create portal failed: http=${create_code}"
      echo "${create_resp}" | tail -n +2 >&2
      exit 1
    fi
    local create_body
    create_body="$(echo "${create_resp}" | tail -n +2)"
    portal_id="$(echo "${create_body}" | jq -r '.data.portalId')"
  fi
  if [[ -z "${portal_id}" || "${portal_id}" == "null" ]]; then
    err "cannot resolve portalId"
    exit 1
  fi

  if [[ -z "${localhost_portal_id}" || "${localhost_portal_id}" == "null" ]]; then
    log "bind domain localhost to portal: ${portal_id}"
    local bind_body
    bind_body='{"domain":"localhost","type":"CUSTOM","protocol":"HTTP"}'
    local bind_resp
    bind_resp="$(curl_json POST "http://localhost:8081/portals/${portal_id}/domains" "${bind_body}" "Authorization: Bearer ${admin_token}")"
    local bind_code
    bind_code="$(echo "${bind_resp}" | head -n 1)"
    if [[ "${bind_code}" != 200 && "${bind_code}" != 409 ]]; then
      local bind_body_resp
      bind_body_resp="$(echo "${bind_resp}" | tail -n +2)"
      if ! echo "${bind_body_resp}" | grep -q "Duplicate entry 'localhost'"; then
        err "bind domain failed: http=${bind_code}"
        echo "${bind_body_resp}" >&2
        exit 1
      fi
    fi
  else
    log "reuse existing localhost-bound portal: ${portal_id}"
  fi

  log "update portal auth configs"
  local portal_detail
  portal_detail="$(curl -sS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}")"
  local new_setting
  new_setting="$(echo "${portal_detail}" | jq \
    --arg frontendRedirectUrl "${frontend_redirect_url}" \
    --arg casServerUrl "http://localhost:${cas_http_port}/cas" \
    '
    .data.portalSettingConfig
    | .frontendRedirectUrl = $frontendRedirectUrl
    | .casConfigs = [
        {
          "provider": "cas",
          "name": "CAS",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": {
            "protocolVersion": "CAS3",
            "responseFormat": "JSON"
          },
          "proxy": {
            "enabled": true,
            "callbackPath": "http://himarket-server:8080/developers/cas/proxy-callback",
            "targetServicePattern": "^http://localhost:5173/.*$",
            "proxyEndpoint": "http://cas:8080/cas/proxy",
            "policyMode": "REGEX",
            "useServiceId": true,
            "exactMatch": true
          },
          "serviceDefinition": {
            "evaluationOrder": 7,
            "responseType": "POST",
            "logoutType": "FRONT_CHANNEL",
            "logoutUrl": "http://localhost:5173/login"
          },
          "attributeRelease": {
            "mode": "RETURN_ALL",
            "allowedAttributes": ["user", "mail", "displayName"]
          },
          "multifactorPolicy": {
            "providers": ["mfa-duo"],
            "bypassEnabled": true,
            "failureMode": "CLOSED",
            "bypassPrincipalAttributeName": "memberOf",
            "bypassPrincipalAttributeValue": "internal",
            "bypassIfMissingPrincipalAttribute": true,
            "forceExecution": true
          },
          "authenticationPolicy": {
            "criteriaMode": "ALLOWED",
            "requiredAuthenticationHandlers": [
              "AcceptUsersAuthenticationHandler",
              "LdapAuthenticationHandler"
            ],
            "tryAll": true
          },
          "expirationPolicy": {
            "expirationDate": "2030-12-31T23:59:59Z",
            "deleteWhenExpired": true,
            "notifyWhenExpired": true,
            "notifyWhenDeleted": true
          },
          "contacts": [
            {
              "name": "Portal SRE",
              "email": "sre@example.com",
              "phone": "+86-21-12345678",
              "department": "Platform",
              "type": "TECHNICAL"
            }
          ],
          "accessStrategy": {
            "enabled": true,
            "ssoEnabled": true,
            "startingDateTime": "2026-01-01T09:00:00",
            "endingDateTime": "2026-12-31T18:00:00",
            "zoneId": "Asia/Shanghai",
            "requireAllAttributes": true,
            "caseInsensitive": true,
            "requiredAttributes": {
              "memberOf": ["internal", "ops"],
              "region": ["cn"]
            },
            "rejectedAttributes": {
              "status": ["disabled"]
            },
            "unauthorizedRedirectUrl": "http://localhost:5173/forbidden",
            "delegatedAuthenticationPolicy": {
              "allowedProviders": ["GithubClient"],
              "permitUndefined": false,
              "exclusive": true
            }
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-saml1",
          "name": "CAS SAML1",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/samlValidate",
          "validation": {
            "protocolVersion": "SAML1",
            "responseFormat": "XML"
          },
          "authenticationPolicy": {
            "criteriaMode": "NOT_PREVENTED",
            "requiredAuthenticationHandlers": ["PreventedHandler"]
          },
          "serviceDefinition": {
            "evaluationOrder": 11,
            "responseType": "POST",
            "logoutType": "FRONT_CHANNEL",
            "logoutUrl": "http://localhost:5173/login"
          },
          "attributeRelease": {
            "allowedAttributes": ["user", "mail"]
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas1",
          "name": "CAS1",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/validate",
          "validation": {
            "protocolVersion": "CAS1",
            "responseFormat": "XML"
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas2",
          "name": "CAS2",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/serviceValidate",
          "validation": {
            "protocolVersion": "CAS2",
            "responseFormat": "XML"
          },
          "authenticationPolicy": {
            "criteriaMode": "ALL",
            "requiredAuthenticationHandlers": [
              "AcceptUsersAuthenticationHandler",
              "LdapAuthenticationHandler"
            ],
            "tryAll": true
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-header",
          "name": "CAS Header",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": {
            "protocolVersion": "CAS3",
            "responseFormat": "JSON"
          },
          "serviceDefinition": {
            "evaluationOrder": 3,
            "responseType": "HEADER"
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-mfa",
          "name": "CAS MFA",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": {
            "protocolVersion": "CAS3",
            "responseFormat": "JSON"
          },
          "serviceDefinition": {
            "evaluationOrder": 5
          },
          "multifactorPolicy": {
            "providers": ["mfa-simple"],
            "forceExecution": true
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-delegated",
          "name": "CAS Delegated",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": {
            "protocolVersion": "CAS3",
            "responseFormat": "JSON"
          },
          "serviceDefinition": {
            "evaluationOrder": 4
          },
          "accessStrategy": {
            "enabled": true,
            "ssoEnabled": true,
            "unauthorizedRedirectUrl": "http://localhost:5173/delegated-forbidden",
            "delegatedAuthenticationPolicy": {
              "allowedProviders": ["MockOidcClient"],
              "permitUndefined": false,
              "exclusive": true
            },
            "httpRequest": {
              "ipAddressPattern": "^127\\.0\\.0\\.1$",
              "userAgentPattern": "^curl/.*$",
              "headers": {
                "X-Portal-Scope": "developer"
              }
            }
          },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "email" }
        }
      ]
    | .ldapConfigs = [
        {
          "provider": "ldap",
          "name": "LDAP",
          "enabled": true,
          "serverUrl": "ldap://openldap:389",
          "baseDn": "ou=people,dc=example,dc=org",
          "bindDn": "cn=admin,dc=example,dc=org",
          "bindPassword": "admin",
          "userSearchFilter": "(uid={0})",
          "identityMapping": { "userIdField": "uid", "userNameField": "cn", "emailField": "mail" }
        }
      ]
    | .oauth2Configs = [
        {
          "provider": "jwt-bearer",
          "name": "JWT Bearer",
          "enabled": true,
          "grantType": "JWT_BEARER",
          "jwtBearerConfig": {
            "issuer": "http://jwks-server/issuer",
            "jwkSetUri": "http://jwks-server/jwks.json",
            "audiences": ["himarket-api"]
          },
          "identityMapping": { "userIdField": "sub", "userNameField": "name", "emailField": "email" }
        }
      ]
    ')"
  local update_payload
  update_payload="$(jq -n --argjson setting "${new_setting}" '{ portalSettingConfig: $setting }')"
  local update_resp
  update_resp="$(curl_json PUT "http://localhost:8081/portals/${portal_id}" "${update_payload}" "Authorization: Bearer ${admin_token}")"
  local update_code
  update_code="$(echo "${update_resp}" | head -n 1)"
  if [[ "${update_code}" != 200 ]]; then
    err "update portal failed: http=${update_code}"
    echo "${update_resp}" | tail -n +2 >&2
    exit 1
  fi

  log "verify developer cas providers"
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-saml1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas1")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas2")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-mfa")' >/dev/null
  curl -fsS "http://localhost:8081/developers/cas/providers" | jq -e '.data[]? | select(.provider=="cas-delegated")' >/dev/null

  log "preview portal cas service definition"
  local portal_cas_service_definition
  portal_cas_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas/service-definition")"
  echo "${portal_cas_service_definition}" | jq -e '
    .data.serviceId? // .serviceId
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.proxyPolicy // .proxyPolicy).pattern // "") | contains("/developers/cas/proxy-callback")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 7
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5173/forbidden"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.proxyPolicy // .proxyPolicy)["@class"] == "org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.proxyPolicy // .proxyPolicy).useServiceId == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.proxyPolicy // .proxyPolicy).exactMatch == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.attributeReleasePolicy // .attributeReleasePolicy)["@class"] == "org.apereo.cas.services.ReturnAllAttributeReleasePolicy"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-duo")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).bypassEnabled == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).failureMode == "CLOSED"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).principalAttributeNameTrigger == "memberOf"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).principalAttributeValueToMatch == "internal"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).bypassIfMissingPrincipalAttribute == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.authenticationPolicy // .authenticationPolicy)["@class"] == "org.apereo.cas.services.DefaultRegisteredServiceAuthenticationPolicy"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllowedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).expirationDate == "2030-12-31T23:59:59Z"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).deleteWhenExpired == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).notifyWhenExpired == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.expirationPolicy // .expirationPolicy).notifyWhenDeleted == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].name // "") == "Portal SRE"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].email // "") == "sre@example.com"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].department // "") == "Platform"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.contacts // [])[0].type // "") == "TECHNICAL"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).startingDateTime == "2026-01-01T09:00:00"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).endingDateTime == "2026-12-31T18:00:00"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).zoneId == "Asia/Shanghai"
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).requireAllAttributes == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy).caseInsensitive == true
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredAttributes.memberOf // [])[1][]?] == ["internal", "ops"]
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredAttributes.region // [])[1][]?] == ["cn"]
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).rejectedAttributes.status // [])[1][]?] == ["disabled"]
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="GithubClient")
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).permitUndefined == false
  ' >/dev/null
  echo "${portal_cas_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).exclusive == true
  ' >/dev/null

  log "preview portal cas saml1 service definition"
  local portal_cas_saml1_service_definition
  portal_cas_saml1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-saml1/service-definition")"
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 11
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "POST"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.logoutType // .logoutType) == "FRONT_CHANNEL"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.logoutUrl // .logoutUrl) == "http://localhost:5173/login"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="SAML1")
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.NotPreventedAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${portal_cas_saml1_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["PreventedHandler"]
  ' >/dev/null

  log "preview portal cas1 service definition"
  local portal_cas1_service_definition
  portal_cas1_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas1/service-definition")"
  echo "${portal_cas1_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS10")
  ' >/dev/null

  log "preview portal cas2 service definition"
  local portal_cas2_service_definition
  portal_cas2_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas2/service-definition")"
  echo "${portal_cas2_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS20")
  ' >/dev/null
  echo "${portal_cas2_service_definition}" | jq -e '
    ((.data.authenticationPolicy // .authenticationPolicy).criteria // {})["@class"] == "org.apereo.cas.services.AllAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria"
  ' >/dev/null
  echo "${portal_cas2_service_definition}" | jq -e '
    [(((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).handlers // [])[1][]?] == ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"]
  ' >/dev/null
  echo "${portal_cas2_service_definition}" | jq -e '
    (((.data.authenticationPolicy // .authenticationPolicy).criteria // {}).tryAll // false) == true
  ' >/dev/null

  log "preview portal cas header service definition"
  local portal_cas_header_service_definition
  portal_cas_header_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-header/service-definition")"
  echo "${portal_cas_header_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 3
  ' >/dev/null
  echo "${portal_cas_header_service_definition}" | jq -e '
    (.data.responseType // .responseType) == "HEADER"
  ' >/dev/null
  echo "${portal_cas_header_service_definition}" | jq -e '
    (.data.supportedProtocols // .supportedProtocols)[1][]? | select(.=="CAS30")
  ' >/dev/null

  log "preview portal cas mfa service definition"
  local portal_cas_mfa_service_definition
  portal_cas_mfa_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-mfa/service-definition")"
  echo "${portal_cas_mfa_service_definition}" | jq -e '
    (.data.evaluationOrder // .evaluationOrder) == 5
  ' >/dev/null
  echo "${portal_cas_mfa_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("provider=\\Qcas-mfa\\E")
  ' >/dev/null
  echo "${portal_cas_mfa_service_definition}" | jq -e '
    ((.data.multifactorPolicy // .multifactorPolicy).multifactorAuthenticationProviders // [])[1][]? | select(.=="mfa-simple")
  ' >/dev/null

  log "preview portal cas delegated service definition"
  local portal_cas_delegated_service_definition
  portal_cas_delegated_service_definition="$(curl -fsS -H "Authorization: Bearer ${admin_token}" "http://localhost:8081/portals/${portal_id}/cas/cas-delegated/service-definition")"
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    (.data.serviceId // .serviceId) | contains("provider=\\Qcas-delegated\\E")
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).delegatedAuthenticationPolicy // {}).allowedProviders // [])[1][]? | select(.=="MockOidcClient")
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    ((.data.accessStrategy // .accessStrategy).unauthorizedRedirectUrl // "") == "http://localhost:5173/delegated-forbidden"
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    (.data.accessStrategy // .accessStrategy)["@class"] == "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy"
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredIpAddressesPatterns // [])[1][]?]
    | any(.[]?; contains("127"))
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    [((.data.accessStrategy // .accessStrategy).requiredUserAgentPatterns // [])[1][]?]
    | any(.[]?; contains("^curl/"))
  ' >/dev/null
  echo "${portal_cas_delegated_service_definition}" | jq -e '
    (((.data.accessStrategy // .accessStrategy).requiredHeaders // {})["X-Portal-Scope"] // "") == "developer"
  ' >/dev/null
  echo "${portal_cas_mfa_service_definition}" | jq -e '
    (.data.multifactorPolicy // .multifactorPolicy).forceExecution == true
  ' >/dev/null
}
