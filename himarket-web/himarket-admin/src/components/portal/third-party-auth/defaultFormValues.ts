import { AuthenticationType, GrantType } from "@/types";

export function buildDefaultFormValues(type: AuthenticationType) {
  if (type === AuthenticationType.OAUTH2) {
    return {
      oauth2GrantType: GrantType.JWT_BEARER,
      oauth2JwtValidationMode: "PUBLIC_KEYS",
      oauth2AcquireMode: "DIRECT",
      oauth2IdentitySource: "CLAIMS",
      oauth2TokenSource: "QUERY",
      oauth2AuthorizationServiceField: "service",
      oauth2TicketExchangeMethod: "POST",
      oauth2TicketExchangeTicketField: "ticket",
      oauth2TicketExchangeTokenField: "access_token",
      oauth2TicketExchangeServiceField: "service",
      trustedUserIdHeader: "X-Forwarded-User",
      trustedUserNameHeader: "X-Forwarded-Name",
      trustedEmailHeader: "X-Forwarded-Email",
      trustedGroupsHeader: "X-Forwarded-Groups",
      trustedRolesHeader: "X-Forwarded-Roles",
      trustedValueSeparator: ",",
      trustedProxyCidrs: [],
      trustedProxyHosts: [],
      enabled: true,
    };
  }

  if (type === AuthenticationType.CAS) {
    return {
      enabled: true,
      validationProtocolVersion: "CAS3",
      validationResponseFormat: "XML",
      loginGateway: false,
      loginRenew: false,
      loginWarn: false,
      loginRememberMe: false,
      proxyEnabled: false,
      accessStrategyEnabled: true,
      accessStrategySsoEnabled: true,
      accessStrategyStartingDateTime: undefined,
      accessStrategyEndingDateTime: undefined,
      accessStrategyZoneId: undefined,
      accessStrategyRequireAllAttributes: false,
      accessStrategyCaseInsensitive: false,
      delegatedPermitUndefined: true,
      delegatedExclusive: false,
      attributeReleaseMode: "RETURN_ALLOWED",
      multifactorFailureMode: "UNDEFINED",
      multifactorBypassEnabled: false,
      multifactorBypassIfMissingPrincipalAttribute: false,
      multifactorForceExecution: false,
      authenticationPolicyCriteriaMode: "ALLOWED",
      authenticationPolicyTryAll: false,
      expirationPolicyDeleteWhenExpired: false,
      expirationPolicyNotifyWhenExpired: false,
      expirationPolicyNotifyWhenDeleted: false,
      serviceContacts: "",
      serviceDefinitionResponseType: "REDIRECT",
      serviceDefinitionEvaluationOrder: 0,
    };
  }

  if (type === AuthenticationType.LDAP) {
    return {
      enabled: true,
      userSearchFilter: "(uid={0})",
    };
  }

  if (type === AuthenticationType.OIDC) {
    return {
      enabled: true,
      configMode: "auto",
    };
  }

  return {};
}
