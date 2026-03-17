import {
  AuthenticationType,
  CasConfig,
  GrantType,
  LdapConfig,
  OAuth2Config,
  OidcConfig,
  ThirdPartyAuthConfig,
} from "@/types";
import {
  formatCommaSeparated,
  formatJsonArrayObject,
  formatJsonObject,
  stringifyServiceContacts,
  ThirdPartyAuthFormValues,
} from "./formValueUtils";

export function buildFormFieldsFromConfig(
  config: ThirdPartyAuthConfig
): ThirdPartyAuthFormValues {
  if (config.type === AuthenticationType.OIDC) {
    const oidcConfig = config as OidcConfig & { type: AuthenticationType.OIDC };
    const hasManualEndpoints = Boolean(
      oidcConfig.authCodeConfig?.authorizationEndpoint &&
      oidcConfig.authCodeConfig?.tokenEndpoint &&
      oidcConfig.authCodeConfig?.userInfoEndpoint
    );

    return {
      provider: oidcConfig.provider,
      name: oidcConfig.name,
      enabled: oidcConfig.enabled,
      type: oidcConfig.type,
      configMode: hasManualEndpoints ? "manual" : "auto",
      ...oidcConfig.authCodeConfig,
      oidcGrantType: oidcConfig.grantType || "AUTHORIZATION_CODE",
      userIdField:
        oidcConfig.identityMapping?.userIdField ||
        oidcConfig.authCodeConfig?.identityMapping?.userIdField,
      userNameField:
        oidcConfig.identityMapping?.userNameField ||
        oidcConfig.authCodeConfig?.identityMapping?.userNameField,
      emailField:
        oidcConfig.identityMapping?.emailField ||
        oidcConfig.authCodeConfig?.identityMapping?.emailField,
    };
  }

  if (config.type === AuthenticationType.OAUTH2) {
    const oauth2Config = config as OAuth2Config & {
      type: AuthenticationType.OAUTH2;
    };
    const hasJwks = Boolean(oauth2Config.jwtBearerConfig?.jwkSetUri);

    return {
      provider: oauth2Config.provider,
      name: oauth2Config.name,
      enabled: oauth2Config.enabled,
      type: oauth2Config.type,
      oauth2GrantType: oauth2Config.grantType || GrantType.JWT_BEARER,
      oauth2JwtValidationMode: hasJwks ? "JWKS" : "PUBLIC_KEYS",
      oauth2Issuer: oauth2Config.jwtBearerConfig?.issuer,
      oauth2JwkSetUri: oauth2Config.jwtBearerConfig?.jwkSetUri,
      oauth2Audiences: oauth2Config.jwtBearerConfig?.audiences || [],
      userIdField: oauth2Config.identityMapping?.userIdField,
      userNameField: oauth2Config.identityMapping?.userNameField,
      emailField: oauth2Config.identityMapping?.emailField,
      publicKeys: oauth2Config.jwtBearerConfig?.publicKeys || [],
    };
  }

  if (config.type === AuthenticationType.CAS) {
    const casConfig = config as CasConfig & { type: AuthenticationType.CAS };
    return {
      provider: casConfig.provider,
      name: casConfig.name,
      enabled: casConfig.enabled,
      sloEnabled: casConfig.sloEnabled ?? false,
      type: casConfig.type,
      serverUrl: casConfig.serverUrl,
      loginEndpoint: casConfig.loginEndpoint,
      validateEndpoint: casConfig.validateEndpoint,
      logoutEndpoint: casConfig.logoutEndpoint,
      loginGateway: casConfig.login?.gateway ?? false,
      loginRenew: casConfig.login?.renew ?? false,
      loginWarn: casConfig.login?.warn ?? false,
      loginRememberMe: casConfig.login?.rememberMe ?? false,
      validationProtocolVersion:
        casConfig.validation?.protocolVersion || "CAS3",
      validationResponseFormat: casConfig.validation?.responseFormat || "XML",
      proxyEnabled: casConfig.proxy?.enabled ?? false,
      proxyCallbackPath: casConfig.proxy?.callbackPath,
      proxyCallbackUrlPattern: casConfig.proxy?.callbackUrlPattern,
      proxyEndpoint: casConfig.proxy?.proxyEndpoint,
      proxyTargetServicePattern: casConfig.proxy?.targetServicePattern,
      proxyPolicyMode: casConfig.proxy?.policyMode || "REGEX",
      proxyUseServiceId: casConfig.proxy?.useServiceId ?? false,
      proxyExactMatch: casConfig.proxy?.exactMatch ?? false,
      proxyPolicyEndpoint: casConfig.proxy?.policyEndpoint,
      proxyPolicyHeaders: formatJsonObject(casConfig.proxy?.policyHeaders),
      serviceDefinitionServiceIdPattern:
        casConfig.serviceDefinition?.serviceIdPattern,
      serviceDefinitionServiceId: casConfig.serviceDefinition?.serviceId,
      serviceDefinitionEvaluationOrder:
        casConfig.serviceDefinition?.evaluationOrder ?? 0,
      serviceDefinitionResponseType:
        casConfig.serviceDefinition?.responseType || "REDIRECT",
      serviceDefinitionLogoutType: casConfig.serviceDefinition?.logoutType,
      serviceDefinitionLogoutUrl: casConfig.serviceDefinition?.logoutUrl,
      accessStrategyEnabled: casConfig.accessStrategy?.enabled ?? true,
      accessStrategySsoEnabled: casConfig.accessStrategy?.ssoEnabled ?? true,
      accessStrategyUnauthorizedRedirectUrl:
        casConfig.accessStrategy?.unauthorizedRedirectUrl,
      accessStrategyStartingDateTime:
        casConfig.accessStrategy?.startingDateTime,
      accessStrategyEndingDateTime: casConfig.accessStrategy?.endingDateTime,
      accessStrategyZoneId: casConfig.accessStrategy?.zoneId,
      accessStrategyRequireAllAttributes:
        casConfig.accessStrategy?.requireAllAttributes ?? false,
      accessStrategyCaseInsensitive:
        casConfig.accessStrategy?.caseInsensitive ?? false,
      accessStrategyRequiredAttributes: formatJsonArrayObject(
        casConfig.accessStrategy?.requiredAttributes
      ),
      accessStrategyRejectedAttributes: formatJsonArrayObject(
        casConfig.accessStrategy?.rejectedAttributes
      ),
      delegatedAllowedProviders: formatCommaSeparated(
        casConfig.accessStrategy?.delegatedAuthenticationPolicy
          ?.allowedProviders
      ),
      delegatedPermitUndefined:
        casConfig.accessStrategy?.delegatedAuthenticationPolicy
          ?.permitUndefined ?? true,
      delegatedExclusive:
        casConfig.accessStrategy?.delegatedAuthenticationPolicy?.exclusive ??
        false,
      httpRequestIpAddressPattern:
        casConfig.accessStrategy?.httpRequest?.ipAddressPattern,
      httpRequestUserAgentPattern:
        casConfig.accessStrategy?.httpRequest?.userAgentPattern,
      httpRequestHeaders: formatJsonObject(
        casConfig.accessStrategy?.httpRequest?.headers
      ),
      attributeReleaseAllowedAttributes: formatCommaSeparated(
        casConfig.attributeRelease?.allowedAttributes
      ),
      attributeReleaseMode:
        casConfig.attributeRelease?.mode || "RETURN_ALLOWED",
      multifactorProviders: formatCommaSeparated(
        casConfig.multifactorPolicy?.providers
      ),
      multifactorFailureMode:
        casConfig.multifactorPolicy?.failureMode || "UNDEFINED",
      multifactorBypassEnabled:
        casConfig.multifactorPolicy?.bypassEnabled ?? false,
      multifactorBypassPrincipalAttributeName:
        casConfig.multifactorPolicy?.bypassPrincipalAttributeName,
      multifactorBypassPrincipalAttributeValue:
        casConfig.multifactorPolicy?.bypassPrincipalAttributeValue,
      multifactorBypassIfMissingPrincipalAttribute:
        casConfig.multifactorPolicy?.bypassIfMissingPrincipalAttribute ?? false,
      multifactorForceExecution:
        casConfig.multifactorPolicy?.forceExecution ?? false,
      authenticationPolicyCriteriaMode:
        casConfig.authenticationPolicy?.criteriaMode || "ALLOWED",
      authenticationPolicyRequiredHandlers: formatCommaSeparated(
        casConfig.authenticationPolicy?.requiredAuthenticationHandlers
      ),
      authenticationPolicyExcludedHandlers: formatCommaSeparated(
        casConfig.authenticationPolicy?.excludedAuthenticationHandlers
      ),
      authenticationPolicyTryAll:
        casConfig.authenticationPolicy?.tryAll ?? false,
      expirationPolicyExpirationDate:
        casConfig.expirationPolicy?.expirationDate,
      expirationPolicyDeleteWhenExpired:
        casConfig.expirationPolicy?.deleteWhenExpired ?? false,
      expirationPolicyNotifyWhenExpired:
        casConfig.expirationPolicy?.notifyWhenExpired ?? false,
      expirationPolicyNotifyWhenDeleted:
        casConfig.expirationPolicy?.notifyWhenDeleted ?? false,
      serviceContacts: stringifyServiceContacts(casConfig.contacts),
      userIdField: casConfig.identityMapping?.userIdField,
      userNameField: casConfig.identityMapping?.userNameField,
      emailField: casConfig.identityMapping?.emailField,
    };
  }

  const ldapConfig = config as LdapConfig & { type: AuthenticationType.LDAP };
  return {
    provider: ldapConfig.provider,
    name: ldapConfig.name,
    enabled: ldapConfig.enabled,
    type: ldapConfig.type,
    serverUrl: ldapConfig.serverUrl,
    baseDn: ldapConfig.baseDn,
    bindDn: ldapConfig.bindDn,
    bindPassword: ldapConfig.bindPassword,
    userSearchFilter: ldapConfig.userSearchFilter,
    userIdField: ldapConfig.identityMapping?.userIdField,
    userNameField: ldapConfig.identityMapping?.userNameField,
    emailField: ldapConfig.identityMapping?.emailField,
  };
}
