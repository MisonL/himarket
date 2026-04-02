import {
  AuthenticationType,
  AuthCodeConfig,
  CasConfig,
  GrantType,
  LdapConfig,
  OAuth2Config,
  OidcConfig,
  ThirdPartyAuthConfig,
} from "@/types";
import {
  parseCommaSeparated,
  parseOptionalNumber,
  parseServiceContacts,
  parseStringArrayMap,
  parseStringMap,
  ThirdPartyAuthFormValues,
} from "./formValueUtils";

export function buildConfigFromFormValues(
  selectedType: AuthenticationType | null,
  values: ThirdPartyAuthFormValues
): ThirdPartyAuthConfig {
  const formValues = values as Record<string, any>;

  if (selectedType === AuthenticationType.OIDC) {
    let authCodeConfig: AuthCodeConfig;

    if (formValues.configMode === "auto") {
      authCodeConfig = {
        clientId: formValues.clientId,
        clientSecret: formValues.clientSecret,
        scopes: formValues.scopes,
        issuer: formValues.issuer,
        authorizationEndpoint: "",
        tokenEndpoint: "",
        userInfoEndpoint: "",
        jwkSetUri: "",
        identityMapping:
          formValues.userIdField ||
          formValues.userNameField ||
          formValues.emailField
            ? {
                userIdField: formValues.userIdField || null,
                userNameField: formValues.userNameField || null,
                emailField: formValues.emailField || null,
              }
            : undefined,
      };
    } else {
      authCodeConfig = {
        clientId: formValues.clientId,
        clientSecret: formValues.clientSecret,
        scopes: formValues.scopes,
        issuer: formValues.issuer || "",
        authorizationEndpoint: formValues.authorizationEndpoint,
        tokenEndpoint: formValues.tokenEndpoint,
        userInfoEndpoint: formValues.userInfoEndpoint,
        jwkSetUri: formValues.jwkSetUri || "",
        identityMapping:
          formValues.userIdField ||
          formValues.userNameField ||
          formValues.emailField
            ? {
                userIdField: formValues.userIdField || null,
                userNameField: formValues.userNameField || null,
                emailField: formValues.emailField || null,
              }
            : undefined,
      };
    }

    return {
      provider: formValues.provider,
      name: formValues.name,
      logoUrl: null,
      enabled: formValues.enabled ?? true,
      grantType: formValues.oidcGrantType || ("AUTHORIZATION_CODE" as const),
      authCodeConfig,
      identityMapping: authCodeConfig.identityMapping,
      type: AuthenticationType.OIDC,
    } as OidcConfig & { type: AuthenticationType.OIDC };
  }

  if (selectedType === AuthenticationType.CAS) {
    return {
      provider: formValues.provider,
      name: formValues.name,
      enabled: formValues.enabled ?? true,
      sloEnabled: formValues.sloEnabled ?? false,
      serverUrl: formValues.serverUrl,
      loginEndpoint: formValues.loginEndpoint || "",
      validateEndpoint: formValues.validateEndpoint || "",
      logoutEndpoint: formValues.logoutEndpoint || "",
      login: {
        gateway: formValues.loginGateway ?? false,
        renew: formValues.loginRenew ?? false,
        warn: formValues.loginWarn ?? false,
        rememberMe: formValues.loginRememberMe ?? false,
      },
      validation: {
        protocolVersion: formValues.validationProtocolVersion || "CAS3",
        responseFormat: formValues.validationResponseFormat || "XML",
      },
      proxy: {
        enabled: formValues.proxyEnabled ?? false,
        callbackPath: formValues.proxyCallbackPath || undefined,
        callbackUrlPattern: formValues.proxyCallbackUrlPattern || undefined,
        proxyEndpoint: formValues.proxyEndpoint || undefined,
        targetServicePattern: formValues.proxyTargetServicePattern || undefined,
        policyMode: formValues.proxyPolicyMode || "REGEX",
        useServiceId: formValues.proxyUseServiceId ?? false,
        exactMatch: formValues.proxyExactMatch ?? false,
        policyEndpoint: formValues.proxyPolicyEndpoint || undefined,
        policyHeaders: parseStringMap(formValues.proxyPolicyHeaders),
      },
      serviceDefinition: {
        serviceIdPattern:
          formValues.serviceDefinitionServiceIdPattern || undefined,
        serviceId: parseOptionalNumber(formValues.serviceDefinitionServiceId),
        evaluationOrder:
          parseOptionalNumber(formValues.serviceDefinitionEvaluationOrder) ?? 0,
        responseType: formValues.serviceDefinitionResponseType || "REDIRECT",
        logoutType: formValues.serviceDefinitionLogoutType || undefined,
        logoutUrl: formValues.serviceDefinitionLogoutUrl || undefined,
      },
      accessStrategy: {
        enabled: formValues.accessStrategyEnabled ?? true,
        ssoEnabled: formValues.accessStrategySsoEnabled ?? true,
        unauthorizedRedirectUrl:
          formValues.accessStrategyUnauthorizedRedirectUrl || undefined,
        startingDateTime:
          formValues.accessStrategyStartingDateTime || undefined,
        endingDateTime: formValues.accessStrategyEndingDateTime || undefined,
        zoneId: formValues.accessStrategyZoneId || undefined,
        requireAllAttributes:
          formValues.accessStrategyRequireAllAttributes ?? false,
        caseInsensitive: formValues.accessStrategyCaseInsensitive ?? false,
        requiredAttributes: parseStringArrayMap(
          formValues.accessStrategyRequiredAttributes,
          "Required Attributes"
        ),
        rejectedAttributes: parseStringArrayMap(
          formValues.accessStrategyRejectedAttributes,
          "Rejected Attributes"
        ),
        delegatedAuthenticationPolicy: {
          allowedProviders: parseCommaSeparated(
            formValues.delegatedAllowedProviders
          ),
          permitUndefined: formValues.delegatedPermitUndefined ?? true,
          exclusive: formValues.delegatedExclusive ?? false,
        },
        httpRequest: {
          ipAddressPattern: formValues.httpRequestIpAddressPattern || undefined,
          userAgentPattern: formValues.httpRequestUserAgentPattern || undefined,
          headers: parseStringMap(formValues.httpRequestHeaders),
        },
      },
      attributeRelease: {
        mode: formValues.attributeReleaseMode || "RETURN_ALLOWED",
        allowedAttributes: parseCommaSeparated(
          formValues.attributeReleaseAllowedAttributes
        ),
      },
      multifactorPolicy: {
        providers: parseCommaSeparated(formValues.multifactorProviders),
        failureMode: formValues.multifactorFailureMode || "UNDEFINED",
        bypassEnabled: formValues.multifactorBypassEnabled ?? false,
        bypassPrincipalAttributeName:
          formValues.multifactorBypassPrincipalAttributeName || undefined,
        bypassPrincipalAttributeValue:
          formValues.multifactorBypassPrincipalAttributeValue || undefined,
        bypassIfMissingPrincipalAttribute:
          formValues.multifactorBypassIfMissingPrincipalAttribute ?? false,
        forceExecution: formValues.multifactorForceExecution ?? false,
      },
      authenticationPolicy: {
        criteriaMode: formValues.authenticationPolicyCriteriaMode || "ALLOWED",
        requiredAuthenticationHandlers: parseCommaSeparated(
          formValues.authenticationPolicyRequiredHandlers
        ),
        excludedAuthenticationHandlers: parseCommaSeparated(
          formValues.authenticationPolicyExcludedHandlers
        ),
        tryAll: formValues.authenticationPolicyTryAll ?? false,
      },
      expirationPolicy: {
        expirationDate: formValues.expirationPolicyExpirationDate || undefined,
        deleteWhenExpired:
          formValues.expirationPolicyDeleteWhenExpired ?? false,
        notifyWhenExpired:
          formValues.expirationPolicyNotifyWhenExpired ?? false,
        notifyWhenDeleted:
          formValues.expirationPolicyNotifyWhenDeleted ?? false,
      },
      contacts: parseServiceContacts(formValues.serviceContacts),
      identityMapping: {
        userIdField: formValues.userIdField || null,
        userNameField: formValues.userNameField || null,
        emailField: formValues.emailField || null,
      },
      type: AuthenticationType.CAS,
    } as CasConfig & { type: AuthenticationType.CAS };
  }

  if (selectedType === AuthenticationType.LDAP) {
    return {
      provider: formValues.provider,
      name: formValues.name,
      enabled: formValues.enabled ?? true,
      serverUrl: formValues.serverUrl,
      baseDn: formValues.baseDn,
      bindDn: formValues.bindDn || "",
      bindPassword: formValues.bindPassword || "",
      userSearchFilter: formValues.userSearchFilter || "(uid={0})",
      identityMapping: {
        userIdField: formValues.userIdField || null,
        userNameField: formValues.userNameField || null,
        emailField: formValues.emailField || null,
      },
      type: AuthenticationType.LDAP,
    } as LdapConfig & { type: AuthenticationType.LDAP };
  }

  if (selectedType === AuthenticationType.OAUTH2) {
    const grantType = formValues.oauth2GrantType || GrantType.JWT_BEARER;
    const validationMode = formValues.oauth2JwtValidationMode || "PUBLIC_KEYS";

    return {
      provider: formValues.provider,
      name: formValues.name,
      enabled: formValues.enabled ?? true,
      grantType,
      jwtBearerConfig:
        grantType === GrantType.JWT_BEARER
          ? validationMode === "JWKS"
            ? {
                issuer: formValues.oauth2Issuer,
                jwkSetUri: formValues.oauth2JwkSetUri,
                audiences: formValues.oauth2Audiences || [],
                authorizationEndpoint:
                  formValues.oauth2AuthorizationEndpoint || undefined,
                authorizationServiceField:
                  formValues.oauth2AuthorizationServiceField || undefined,
                acquireMode: formValues.oauth2AcquireMode || "DIRECT",
                ticketExchangeUrl:
                  formValues.oauth2TicketExchangeUrl || undefined,
                ticketExchangeMethod:
                  formValues.oauth2TicketExchangeMethod || undefined,
                ticketExchangeTicketField:
                  formValues.oauth2TicketExchangeTicketField || undefined,
                ticketExchangeTokenField:
                  formValues.oauth2TicketExchangeTokenField || undefined,
                ticketExchangeServiceField:
                  formValues.oauth2TicketExchangeServiceField || undefined,
                userInfoEndpoint: formValues.oauth2UserInfoEndpoint || undefined,
                identitySource: formValues.oauth2IdentitySource || "CLAIMS",
                tokenSource: formValues.oauth2TokenSource || "QUERY",
                publicKeys: [],
              }
            : {
                authorizationEndpoint:
                  formValues.oauth2AuthorizationEndpoint || undefined,
                authorizationServiceField:
                  formValues.oauth2AuthorizationServiceField || undefined,
                acquireMode: formValues.oauth2AcquireMode || "DIRECT",
                ticketExchangeUrl:
                  formValues.oauth2TicketExchangeUrl || undefined,
                ticketExchangeMethod:
                  formValues.oauth2TicketExchangeMethod || undefined,
                ticketExchangeTicketField:
                  formValues.oauth2TicketExchangeTicketField || undefined,
                ticketExchangeTokenField:
                  formValues.oauth2TicketExchangeTokenField || undefined,
                ticketExchangeServiceField:
                  formValues.oauth2TicketExchangeServiceField || undefined,
                userInfoEndpoint: formValues.oauth2UserInfoEndpoint || undefined,
                identitySource: formValues.oauth2IdentitySource || "CLAIMS",
                tokenSource: formValues.oauth2TokenSource || "QUERY",
                publicKeys: formValues.publicKeys || [],
              }
          : undefined,
      trustedHeaderConfig:
        grantType === GrantType.TRUSTED_HEADER
          ? {
              enabled: true,
              trustedProxyCidrs: formValues.trustedProxyCidrs || [],
              trustedProxyHosts: formValues.trustedProxyHosts || [],
              userIdHeader: formValues.trustedUserIdHeader || undefined,
              userNameHeader: formValues.trustedUserNameHeader || undefined,
              emailHeader: formValues.trustedEmailHeader || undefined,
              groupsHeader: formValues.trustedGroupsHeader || undefined,
              rolesHeader: formValues.trustedRolesHeader || undefined,
              valueSeparator: formValues.trustedValueSeparator || undefined,
            }
          : undefined,
      identityMapping: {
        userIdField: formValues.userIdField || null,
        userNameField: formValues.userNameField || null,
        emailField: formValues.emailField || null,
      },
      type: AuthenticationType.OAUTH2,
    } as OAuth2Config & { type: AuthenticationType.OAUTH2 };
  }

  throw new Error("未选择认证类型");
}
