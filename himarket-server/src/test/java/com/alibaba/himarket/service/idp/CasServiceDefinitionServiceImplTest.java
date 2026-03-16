/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.cas.CasAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyMode;
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyCriteriaMode;
import com.alibaba.himarket.support.portal.cas.CasDelegatedAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasHttpRequestAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasMultifactorFailureMode;
import com.alibaba.himarket.support.portal.cas.CasMultifactorPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyPolicyMode;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceLogoutType;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CasServiceDefinitionServiceImplTest {

    @Mock private PortalService portalService;

    @Test
    void exportPortalServiceDefinitionShouldDeriveRegexAndPolicies() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setEnabled(true);
        casConfig.setSloEnabled(true);
        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("uid");
        identityMapping.setEmailField("mail");
        casConfig.setIdentityMapping(identityMapping);

        CasValidationConfig validationConfig = new CasValidationConfig();
        validationConfig.setProtocolVersion(CasProtocolVersion.CAS3);
        validationConfig.setResponseFormat(CasValidationResponseFormat.JSON);
        casConfig.setValidation(validationConfig);

        CasAttributeReleasePolicyConfig attributeRelease = new CasAttributeReleasePolicyConfig();
        attributeRelease.setAllowedAttributes(List.of("uid", "mail", "displayName"));
        casConfig.setAttributeRelease(attributeRelease);
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        proxyConfig.setUseServiceId(true);
        proxyConfig.setExactMatch(true);
        casConfig.setProxy(proxyConfig);

        CasMultifactorPolicyConfig multifactorPolicy = new CasMultifactorPolicyConfig();
        multifactorPolicy.setProviders(List.of("mfa-duo"));
        multifactorPolicy.setFailureMode(CasMultifactorFailureMode.CLOSED);
        multifactorPolicy.setBypassPrincipalAttributeName("memberOf");
        multifactorPolicy.setBypassPrincipalAttributeValue("internal");
        multifactorPolicy.setBypassIfMissingPrincipalAttribute(true);
        casConfig.setMultifactorPolicy(multifactorPolicy);
        CasAuthenticationPolicyConfig authenticationPolicy = new CasAuthenticationPolicyConfig();
        authenticationPolicy.setCriteriaMode(CasAuthenticationPolicyCriteriaMode.ALLOWED);
        authenticationPolicy.setRequiredAuthenticationHandlers(
                List.of("AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler"));
        authenticationPolicy.setTryAll(true);
        casConfig.setAuthenticationPolicy(authenticationPolicy);

        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setFrontendRedirectUrl("https://portal.example.com");
        settingConfig.setCasConfigs(List.of(casConfig));
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalId("portal-1");
        portalResult.setPortalSettingConfig(settingConfig);
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceDefinitionServiceImpl service =
                new CasServiceDefinitionServiceImpl(portalService, new AdminAuthConfig());

        Map<String, Object> definition = service.exportPortalServiceDefinition("portal-1", "cas");
        String serviceId = (String) definition.get("serviceId");

        assertEquals("org.apereo.cas.services.CasRegisteredService", definition.get("@class"));
        assertTrue(serviceId.contains("/developers/cas/callback"));
        assertTrue(serviceId.contains("provider=\\Qcas\\E"));
        assertEquals("BACK_CHANNEL", definition.get("logoutType"));
        assertEquals("REDIRECT", definition.get("responseType"));

        @SuppressWarnings("unchecked")
        List<Object> protocols = (List<Object>) definition.get("supportedProtocols");
        assertEquals("java.util.HashSet", protocols.get(0));
        assertEquals(List.of("CAS30"), protocols.get(1));

        @SuppressWarnings("unchecked")
        Map<String, Object> attributePolicy =
                (Map<String, Object>) definition.get("attributeReleasePolicy");
        assertEquals(
                "org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy",
                attributePolicy.get("@class"));
        assertEquals(
                List.of("java.util.ArrayList", List.of("user", "uid", "mail", "displayName")),
                attributePolicy.get("allowedAttributes"));

        @SuppressWarnings("unchecked")
        Map<String, Object> multifactor = (Map<String, Object>) definition.get("multifactorPolicy");
        assertEquals(
                "org.apereo.cas.services.DefaultRegisteredServiceMultifactorPolicy",
                multifactor.get("@class"));
        assertEquals("CLOSED", multifactor.get("failureMode"));
        assertEquals("memberOf", multifactor.get("principalAttributeNameTrigger"));
        assertEquals("internal", multifactor.get("principalAttributeValueToMatch"));
        assertEquals(true, multifactor.get("bypassIfMissingPrincipalAttribute"));
        @SuppressWarnings("unchecked")
        Map<String, Object> authenticationPolicyJson =
                (Map<String, Object>) definition.get("authenticationPolicy");
        assertEquals(
                "org.apereo.cas.services.DefaultRegisteredServiceAuthenticationPolicy",
                authenticationPolicyJson.get("@class"));
        @SuppressWarnings("unchecked")
        Map<String, Object> authenticationCriteria =
                (Map<String, Object>) authenticationPolicyJson.get("criteria");
        assertEquals(
                "org.apereo.cas.services.AllowedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria",
                authenticationCriteria.get("@class"));
        assertEquals(
                List.of(
                        "java.util.ArrayList",
                        List.of("AcceptUsersAuthenticationHandler", "LdapAuthenticationHandler")),
                authenticationCriteria.get("handlers"));
        assertEquals(true, authenticationCriteria.get("tryAll"));
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyPolicy = (Map<String, Object>) definition.get("proxyPolicy");
        assertEquals(
                "org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy",
                proxyPolicy.get("@class"));
        assertEquals(true, proxyPolicy.get("useServiceId"));
        assertEquals(true, proxyPolicy.get("exactMatch"));
        assertTrue(
                ((String) proxyPolicy.get("pattern")).contains("/developers/cas/proxy-callback"));
    }

    @Test
    void exportAdminServiceDefinitionShouldHonorOverrides() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setEnabled(true);

        CasServiceDefinitionConfig serviceDefinition = new CasServiceDefinitionConfig();
        serviceDefinition.setServiceIdPattern("^https://admin.example.com/custom/callback$");
        serviceDefinition.setServiceId(2001L);
        serviceDefinition.setEvaluationOrder(10);
        serviceDefinition.setResponseType(CasServiceResponseType.POST);
        serviceDefinition.setLogoutType(CasServiceLogoutType.FRONT_CHANNEL);
        serviceDefinition.setLogoutUrl("https://admin.example.com/login");
        casConfig.setServiceDefinition(serviceDefinition);

        CasValidationConfig validationConfig = new CasValidationConfig();
        validationConfig.setProtocolVersion(CasProtocolVersion.CAS2);
        casConfig.setValidation(validationConfig);

        CasAccessStrategyConfig accessStrategy = new CasAccessStrategyConfig();
        accessStrategy.setEnabled(false);
        accessStrategy.setSsoEnabled(false);
        accessStrategy.setUnauthorizedRedirectUrl("https://admin.example.com/forbidden");
        accessStrategy.setStartingDateTime("2026-01-01T09:00:00");
        accessStrategy.setEndingDateTime("2026-12-31T18:00:00");
        accessStrategy.setZoneId("Asia/Shanghai");
        accessStrategy.setRequireAllAttributes(true);
        accessStrategy.setCaseInsensitive(true);
        accessStrategy.setRequiredAttributes(
                Map.of("memberOf", List.of("internal", "ops"), "region", List.of("cn")));
        accessStrategy.setRejectedAttributes(Map.of("status", List.of("disabled")));
        CasDelegatedAuthenticationPolicyConfig delegated =
                new CasDelegatedAuthenticationPolicyConfig();
        delegated.setAllowedProviders(List.of("GithubClient"));
        delegated.setExclusive(true);
        accessStrategy.setDelegatedAuthenticationPolicy(delegated);
        CasHttpRequestAccessStrategyConfig httpRequest = new CasHttpRequestAccessStrategyConfig();
        httpRequest.setIpAddressPattern("^127\\.0\\.0\\.1$");
        httpRequest.setUserAgentPattern("^curl/.*$");
        httpRequest.setHeaders(Map.of("X-Portal-Scope", "admin"));
        accessStrategy.setHttpRequest(httpRequest);
        casConfig.setAccessStrategy(accessStrategy);
        CasAttributeReleasePolicyConfig attributeRelease = new CasAttributeReleasePolicyConfig();
        attributeRelease.setMode(CasAttributeReleasePolicyMode.DENY_ALL);
        casConfig.setAttributeRelease(attributeRelease);
        CasAuthenticationPolicyConfig authenticationPolicy = new CasAuthenticationPolicyConfig();
        authenticationPolicy.setCriteriaMode(CasAuthenticationPolicyCriteriaMode.EXCLUDED);
        authenticationPolicy.setExcludedAuthenticationHandlers(List.of("BlockedHandler"));
        casConfig.setAuthenticationPolicy(authenticationPolicy);

        AdminAuthConfig adminAuthConfig = new AdminAuthConfig();
        adminAuthConfig.setFrontendRedirectUrl("https://admin.example.com");
        adminAuthConfig.setCasConfigs(List.of(casConfig));

        CasServiceDefinitionServiceImpl service =
                new CasServiceDefinitionServiceImpl(portalService, adminAuthConfig);

        Map<String, Object> definition = service.exportAdminServiceDefinition("cas");

        assertEquals("^https://admin.example.com/custom/callback$", definition.get("serviceId"));
        assertEquals(2001L, definition.get("id"));
        assertEquals(10, definition.get("evaluationOrder"));
        assertEquals("POST", definition.get("responseType"));
        assertEquals("FRONT_CHANNEL", definition.get("logoutType"));
        assertEquals("https://admin.example.com/login", definition.get("logoutUrl"));

        @SuppressWarnings("unchecked")
        Map<String, Object> accessStrategyJson =
                (Map<String, Object>) definition.get("accessStrategy");
        assertEquals(false, accessStrategyJson.get("enabled"));
        assertEquals(false, accessStrategyJson.get("ssoEnabled"));
        assertEquals(
                "https://admin.example.com/forbidden",
                accessStrategyJson.get("unauthorizedRedirectUrl"));
        assertEquals("2026-01-01T09:00:00", accessStrategyJson.get("startingDateTime"));
        assertEquals("2026-12-31T18:00:00", accessStrategyJson.get("endingDateTime"));
        assertEquals("Asia/Shanghai", accessStrategyJson.get("zoneId"));
        assertEquals(true, accessStrategyJson.get("requireAllAttributes"));
        assertEquals(true, accessStrategyJson.get("caseInsensitive"));
        assertEquals(
                "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy",
                accessStrategyJson.get("@class"));
        assertEquals(
                List.of("java.util.ArrayList", List.of("^127\\.0\\.0\\.1$")),
                accessStrategyJson.get("requiredIpAddressesPatterns"));
        assertEquals(
                List.of("java.util.ArrayList", List.of("^curl/.*$")),
                accessStrategyJson.get("requiredUserAgentPatterns"));
        assertEquals(Map.of("X-Portal-Scope", "admin"), accessStrategyJson.get("requiredHeaders"));
        assertEquals(
                Map.of(
                        "memberOf", List.of("java.util.LinkedHashSet", List.of("internal", "ops")),
                        "region", List.of("java.util.LinkedHashSet", List.of("cn"))),
                accessStrategyJson.get("requiredAttributes"));
        assertEquals(
                Map.of("status", List.of("java.util.LinkedHashSet", List.of("disabled"))),
                accessStrategyJson.get("rejectedAttributes"));

        @SuppressWarnings("unchecked")
        Map<String, Object> delegatedJson =
                (Map<String, Object>) accessStrategyJson.get("delegatedAuthenticationPolicy");
        assertEquals(
                "org.apereo.cas.services.DefaultRegisteredServiceDelegatedAuthenticationPolicy",
                delegatedJson.get("@class"));
        @SuppressWarnings("unchecked")
        Map<String, Object> authenticationPolicyJson =
                (Map<String, Object>) definition.get("authenticationPolicy");
        @SuppressWarnings("unchecked")
        Map<String, Object> authenticationCriteria =
                (Map<String, Object>) authenticationPolicyJson.get("criteria");
        assertEquals(
                "org.apereo.cas.services.ExcludedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria",
                authenticationCriteria.get("@class"));
        assertEquals(
                List.of("java.util.ArrayList", List.of("BlockedHandler")),
                authenticationCriteria.get("handlers"));
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyPolicy = (Map<String, Object>) definition.get("proxyPolicy");
        assertEquals(
                "org.apereo.cas.services.RefuseRegisteredServiceProxyPolicy",
                proxyPolicy.get("@class"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attributeReleaseJson =
                (Map<String, Object>) definition.get("attributeReleasePolicy");
        assertEquals(
                "org.apereo.cas.services.DenyAllAttributeReleasePolicy",
                attributeReleaseJson.get("@class"));
    }

    @Test
    void exportPortalServiceDefinitionShouldSupportReturnAllAttributes() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setEnabled(true);
        CasAttributeReleasePolicyConfig attributeRelease = new CasAttributeReleasePolicyConfig();
        attributeRelease.setMode(CasAttributeReleasePolicyMode.RETURN_ALL);
        casConfig.setAttributeRelease(attributeRelease);

        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setFrontendRedirectUrl("https://portal.example.com");
        settingConfig.setCasConfigs(List.of(casConfig));
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalId("portal-1");
        portalResult.setPortalSettingConfig(settingConfig);
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceDefinitionServiceImpl service =
                new CasServiceDefinitionServiceImpl(portalService, new AdminAuthConfig());

        Map<String, Object> definition = service.exportPortalServiceDefinition("portal-1", "cas");

        @SuppressWarnings("unchecked")
        Map<String, Object> attributePolicy =
                (Map<String, Object>) definition.get("attributeReleasePolicy");
        assertEquals(
                "org.apereo.cas.services.ReturnAllAttributeReleasePolicy",
                attributePolicy.get("@class"));
        assertEquals(false, attributePolicy.containsKey("allowedAttributes"));
    }

    @Test
    void exportPortalServiceDefinitionShouldSupportRestProxyPolicy() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setEnabled(true);
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        proxyConfig.setPolicyMode(CasProxyPolicyMode.REST);
        proxyConfig.setPolicyEndpoint("https://proxy.example.com/policies");
        proxyConfig.setPolicyHeaders(Map.of("X-Proxy-Policy", "enabled"));
        casConfig.setProxy(proxyConfig);

        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setFrontendRedirectUrl("https://portal.example.com");
        settingConfig.setCasConfigs(List.of(casConfig));
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalId("portal-1");
        portalResult.setPortalSettingConfig(settingConfig);
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceDefinitionServiceImpl service =
                new CasServiceDefinitionServiceImpl(portalService, new AdminAuthConfig());

        Map<String, Object> definition = service.exportPortalServiceDefinition("portal-1", "cas");

        @SuppressWarnings("unchecked")
        Map<String, Object> proxyPolicy = (Map<String, Object>) definition.get("proxyPolicy");
        assertEquals(
                "org.apereo.cas.services.RestfulRegisteredServiceProxyPolicy",
                proxyPolicy.get("@class"));
        assertEquals("https://proxy.example.com/policies", proxyPolicy.get("endpoint"));
        assertEquals(Map.of("X-Proxy-Policy", "enabled"), proxyPolicy.get("headers"));
    }
}
