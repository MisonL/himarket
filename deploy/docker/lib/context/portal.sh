#!/usr/bin/env bash

ensure_localhost_portal() {
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
    local bind_body='{"domain":"localhost","type":"CUSTOM","protocol":"HTTP"}'
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
}

configure_portal_auth() {
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
          "validation": { "protocolVersion": "CAS3", "responseFormat": "JSON" },
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
          "attributeRelease": { "mode": "RETURN_ALL", "allowedAttributes": ["user", "mail", "displayName"] },
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
            "requiredAuthenticationHandlers": ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"],
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
            "requiredAttributes": { "memberOf": ["internal", "ops"], "region": ["cn"] },
            "rejectedAttributes": { "status": ["disabled"] },
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
          "validation": { "protocolVersion": "SAML1", "responseFormat": "XML" },
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
          "attributeRelease": { "allowedAttributes": ["user", "mail"] },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas1",
          "name": "CAS1",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/validate",
          "validation": { "protocolVersion": "CAS1", "responseFormat": "XML" },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas2",
          "name": "CAS2",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/serviceValidate",
          "validation": { "protocolVersion": "CAS2", "responseFormat": "XML" },
          "authenticationPolicy": {
            "criteriaMode": "ALL",
            "requiredAuthenticationHandlers": ["AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"],
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
          "validation": { "protocolVersion": "CAS3", "responseFormat": "JSON" },
          "serviceDefinition": { "evaluationOrder": 3, "responseType": "HEADER" },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-mfa",
          "name": "CAS MFA",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": { "protocolVersion": "CAS3", "responseFormat": "JSON" },
          "serviceDefinition": { "evaluationOrder": 5 },
          "multifactorPolicy": { "providers": ["mfa-simple"], "forceExecution": true },
          "identityMapping": { "userIdField": "user", "userNameField": "user", "emailField": "mail" }
        },
        {
          "provider": "cas-delegated",
          "name": "CAS Delegated",
          "enabled": true,
          "sloEnabled": true,
          "serverUrl": $casServerUrl,
          "validateEndpoint": "http://cas:8080/cas/p3/serviceValidate",
          "validation": { "protocolVersion": "CAS3", "responseFormat": "JSON" },
          "serviceDefinition": { "evaluationOrder": 4 },
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
              "headers": { "X-Portal-Scope": "developer" }
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
}
