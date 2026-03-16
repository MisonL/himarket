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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.cas.CasAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyMode;
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyCriteriaMode;
import com.alibaba.himarket.support.portal.cas.CasDelegatedAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasExpirationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasHttpRequestAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasMultifactorPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyPolicyMode;
import com.alibaba.himarket.support.portal.cas.CasServiceContactConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceLogoutType;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CasServiceDefinitionServiceImpl implements CasServiceDefinitionService {

    private static final String CAS_REGISTERED_SERVICE =
            "org.apereo.cas.services.CasRegisteredService";

    private static final String ACCESS_STRATEGY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy";

    private static final String HTTP_REQUEST_ACCESS_STRATEGY_CLASS =
            "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy";

    private static final String ATTRIBUTE_RELEASE_POLICY_CLASS =
            "org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy";

    private static final String ATTRIBUTE_RELEASE_POLICY_ALL_CLASS =
            "org.apereo.cas.services.ReturnAllAttributeReleasePolicy";

    private static final String ATTRIBUTE_RELEASE_POLICY_DENY_CLASS =
            "org.apereo.cas.services.DenyAllAttributeReleasePolicy";

    private static final String MULTIFACTOR_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceMultifactorPolicy";

    private static final String DELEGATED_AUTH_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceDelegatedAuthenticationPolicy";

    private static final String AUTHENTICATION_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceAuthenticationPolicy";

    private static final String EXPIRATION_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceExpirationPolicy";

    private static final String REFUSE_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RefuseRegisteredServiceProxyPolicy";

    private static final String REGEX_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy";

    private static final String REST_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RestfulRegisteredServiceProxyPolicy";

    private final PortalService portalService;

    private final AdminAuthConfig adminAuthConfig;

    @Override
    public Map<String, Object> exportPortalServiceDefinition(String portalId, String provider) {
        PortalResult portal = portalService.getPortal(portalId);
        CasConfig casConfig =
                Optional.ofNullable(portal.getPortalSettingConfig())
                        .map(setting -> setting.getCasConfigs())
                        .orElse(List.of())
                        .stream()
                        .filter(
                                config ->
                                        provider.equals(config.getProvider()) && config.isEnabled())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                "CAS provider is not configured for portal"));
        String frontendBaseUrl =
                Optional.ofNullable(portal.getPortalSettingConfig())
                        .map(setting -> setting.getFrontendRedirectUrl())
                        .filter(StrUtil::isNotBlank)
                        .map(url -> StrUtil.removeSuffix(url, "/"))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_PARAMETER,
                                                "Portal frontend redirect URL is not configured"));
        return buildServiceDefinition(
                "portal",
                portalId,
                provider,
                frontendBaseUrl,
                "/developers/cas/callback",
                "/developers/cas/proxy-callback",
                casConfig);
    }

    @Override
    public Map<String, Object> exportAdminServiceDefinition(String provider) {
        CasConfig casConfig =
                Optional.ofNullable(adminAuthConfig.getCasConfigs()).orElse(List.of()).stream()
                        .filter(
                                config ->
                                        provider.equals(config.getProvider()) && config.isEnabled())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                "CAS provider is not configured for admin"));
        String frontendBaseUrl =
                Optional.ofNullable(adminAuthConfig.getFrontendRedirectUrl())
                        .filter(StrUtil::isNotBlank)
                        .map(url -> StrUtil.removeSuffix(url, "/"))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_PARAMETER,
                                                "Admin frontend redirect URL is not configured"));
        return buildServiceDefinition(
                "admin",
                "admin",
                provider,
                frontendBaseUrl,
                "/admins/cas/callback",
                "/admins/cas/proxy-callback",
                casConfig);
    }

    private Map<String, Object> buildServiceDefinition(
            String scope,
            String ownerId,
            String provider,
            String frontendBaseUrl,
            String callbackPath,
            String proxyCallbackPath,
            CasConfig casConfig) {
        CasServiceDefinitionConfig serviceDefinition = casConfig.resolveServiceDefinition();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("@class", CAS_REGISTERED_SERVICE);
        json.put(
                "serviceId",
                resolveServiceIdPattern(
                        serviceDefinition, frontendBaseUrl, callbackPath, provider));
        json.put("name", StrUtil.format("HiMarket {} CAS {}", StrUtil.upperFirst(scope), provider));
        json.put("id", resolveRegisteredServiceId(serviceDefinition, scope, ownerId, provider));
        json.put("evaluationOrder", resolveEvaluationOrder(serviceDefinition));
        json.put("responseType", resolveResponseType(serviceDefinition.getResponseType()));
        json.put(
                "supportedProtocols",
                typedCollection("java.util.HashSet", resolveProtocols(casConfig)));
        json.put("logoutType", resolveLogoutType(serviceDefinition, casConfig));
        json.put(
                "proxyPolicy",
                buildProxyPolicy(
                        casConfig.resolveProxyConfig(), frontendBaseUrl, proxyCallbackPath));
        json.put("accessStrategy", buildAccessStrategy(casConfig.resolveAccessStrategy()));
        json.put(
                "attributeReleasePolicy",
                buildAttributeReleasePolicy(
                        casConfig.resolveAttributeRelease(), casConfig.getIdentityMapping()));
        Map<String, Object> multifactorPolicy =
                buildMultifactorPolicy(casConfig.resolveMultifactorPolicy());
        if (!multifactorPolicy.isEmpty()) {
            json.put("multifactorPolicy", multifactorPolicy);
        }
        Map<String, Object> authenticationPolicy =
                buildAuthenticationPolicy(casConfig.resolveAuthenticationPolicy());
        if (!authenticationPolicy.isEmpty()) {
            json.put("authenticationPolicy", authenticationPolicy);
        }
        Map<String, Object> expirationPolicy =
                buildExpirationPolicy(casConfig.resolveExpirationPolicy());
        if (!expirationPolicy.isEmpty()) {
            json.put("expirationPolicy", expirationPolicy);
        }
        List<Object> contacts = buildContacts(casConfig.getContacts());
        if (!contacts.isEmpty()) {
            json.put("contacts", contacts);
        }
        if (StrUtil.isNotBlank(serviceDefinition.getLogoutUrl())) {
            json.put("logoutUrl", serviceDefinition.getLogoutUrl());
        }
        return json;
    }

    private String resolveServiceIdPattern(
            CasServiceDefinitionConfig serviceDefinition,
            String frontendBaseUrl,
            String callbackPath,
            String provider) {
        if (StrUtil.isNotBlank(serviceDefinition.getServiceIdPattern())) {
            return serviceDefinition.getServiceIdPattern();
        }
        return "^"
                + Pattern.quote(frontendBaseUrl)
                + "/api(?:/[^/?#]+)*"
                + Pattern.quote(callbackPath)
                + "\\?provider="
                + Pattern.quote(provider)
                + "(?:&.*)?$";
    }

    private long resolveRegisteredServiceId(
            CasServiceDefinitionConfig serviceDefinition,
            String scope,
            String ownerId,
            String provider) {
        if (serviceDefinition.getServiceId() != null && serviceDefinition.getServiceId() > 0) {
            return serviceDefinition.getServiceId();
        }
        return Integer.toUnsignedLong(Objects.hash(scope, ownerId, provider)) + 1000L;
    }

    private int resolveEvaluationOrder(CasServiceDefinitionConfig serviceDefinition) {
        return Optional.ofNullable(serviceDefinition.getEvaluationOrder()).orElse(0);
    }

    private String resolveResponseType(CasServiceResponseType responseType) {
        return Optional.ofNullable(responseType).orElse(CasServiceResponseType.REDIRECT).name();
    }

    private String resolveLogoutType(
            CasServiceDefinitionConfig serviceDefinition, CasConfig casConfig) {
        if (serviceDefinition.getLogoutType() != null) {
            return serviceDefinition.getLogoutType().name();
        }
        return casConfig.isSloEnabled()
                ? CasServiceLogoutType.BACK_CHANNEL.name()
                : CasServiceLogoutType.NONE.name();
    }

    private List<String> resolveProtocols(CasConfig casConfig) {
        return switch (casConfig.resolveValidationConfig().getProtocolVersion()) {
            case CAS1 -> List.of("CAS10");
            case CAS2 -> List.of("CAS20");
            case CAS3 -> List.of("CAS30");
            case SAML1 -> List.of("SAML1");
        };
    }

    private Map<String, Object> buildProxyPolicy(
            CasProxyConfig proxyConfig, String frontendBaseUrl, String defaultProxyCallbackPath) {
        CasProxyPolicyMode policyMode = resolveProxyPolicyMode(proxyConfig);
        if (!Boolean.TRUE.equals(proxyConfig.getEnabled())
                || policyMode == CasProxyPolicyMode.REFUSE) {
            return Map.of("@class", REFUSE_PROXY_POLICY_CLASS);
        }
        if (policyMode == CasProxyPolicyMode.REST) {
            return buildRestProxyPolicy(proxyConfig);
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", REGEX_PROXY_POLICY_CLASS);
        policy.put(
                "pattern",
                resolveProxyCallbackPattern(
                        proxyConfig, frontendBaseUrl, defaultProxyCallbackPath));
        policy.put(
                "useServiceId", Optional.ofNullable(proxyConfig.getUseServiceId()).orElse(false));
        policy.put("exactMatch", Optional.ofNullable(proxyConfig.getExactMatch()).orElse(false));
        return policy;
    }

    private CasProxyPolicyMode resolveProxyPolicyMode(CasProxyConfig proxyConfig) {
        if (proxyConfig.getPolicyMode() == null) {
            return CasProxyPolicyMode.REGEX;
        }
        return proxyConfig.getPolicyMode();
    }

    private Map<String, Object> buildRestProxyPolicy(CasProxyConfig proxyConfig) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", REST_PROXY_POLICY_CLASS);
        policy.put("endpoint", proxyConfig.getPolicyEndpoint());
        if (proxyConfig.getPolicyHeaders() != null && !proxyConfig.getPolicyHeaders().isEmpty()) {
            Map<String, String> headers = new LinkedHashMap<>();
            proxyConfig
                    .getPolicyHeaders()
                    .forEach(
                            (key, value) -> {
                                if (StrUtil.isNotBlank(key) && StrUtil.isNotBlank(value)) {
                                    headers.put(key, value);
                                }
                            });
            if (!headers.isEmpty()) {
                policy.put("headers", headers);
            }
        }
        return policy;
    }

    private String resolveProxyCallbackPattern(
            CasProxyConfig proxyConfig, String frontendBaseUrl, String defaultProxyCallbackPath) {
        if (StrUtil.isNotBlank(proxyConfig.getCallbackUrlPattern())) {
            return proxyConfig.getCallbackUrlPattern();
        }
        String callbackPath =
                StrUtil.blankToDefault(proxyConfig.getCallbackPath(), defaultProxyCallbackPath);
        if (StrUtil.startWithAnyIgnoreCase(callbackPath, "http://", "https://")) {
            return "^" + Pattern.quote(callbackPath) + "(?:\\?.*)?$";
        }
        return "^"
                + Pattern.quote(frontendBaseUrl)
                + "/api(?:/[^/?#]+)*"
                + Pattern.quote(callbackPath)
                + "(?:\\?.*)?$";
    }

    private Map<String, Object> buildAccessStrategy(CasAccessStrategyConfig accessStrategyConfig) {
        Map<String, Object> accessStrategy = new LinkedHashMap<>();
        CasHttpRequestAccessStrategyConfig httpRequestConfig =
                accessStrategyConfig != null ? accessStrategyConfig.getHttpRequest() : null;
        accessStrategy.put(
                "@class",
                hasHttpRequestAccessStrategy(httpRequestConfig)
                        ? HTTP_REQUEST_ACCESS_STRATEGY_CLASS
                        : ACCESS_STRATEGY_CLASS);
        accessStrategy.put(
                "enabled", Optional.ofNullable(accessStrategyConfig.getEnabled()).orElse(true));
        accessStrategy.put(
                "ssoEnabled",
                Optional.ofNullable(accessStrategyConfig.getSsoEnabled()).orElse(true));
        if (StrUtil.isNotBlank(accessStrategyConfig.getUnauthorizedRedirectUrl())) {
            accessStrategy.put(
                    "unauthorizedRedirectUrl", accessStrategyConfig.getUnauthorizedRedirectUrl());
        }
        if (StrUtil.isNotBlank(accessStrategyConfig.getStartingDateTime())) {
            accessStrategy.put("startingDateTime", accessStrategyConfig.getStartingDateTime());
        }
        if (StrUtil.isNotBlank(accessStrategyConfig.getEndingDateTime())) {
            accessStrategy.put("endingDateTime", accessStrategyConfig.getEndingDateTime());
        }
        if (StrUtil.isNotBlank(accessStrategyConfig.getZoneId())) {
            accessStrategy.put("zoneId", accessStrategyConfig.getZoneId());
        }
        accessStrategy.put(
                "requireAllAttributes",
                Optional.ofNullable(accessStrategyConfig.getRequireAllAttributes()).orElse(false));
        accessStrategy.put(
                "caseInsensitive",
                Optional.ofNullable(accessStrategyConfig.getCaseInsensitive()).orElse(false));
        appendAccessAttributes(
                accessStrategy, "requiredAttributes", accessStrategyConfig.getRequiredAttributes());
        appendAccessAttributes(
                accessStrategy, "rejectedAttributes", accessStrategyConfig.getRejectedAttributes());
        Map<String, Object> delegatedAuthenticationPolicy =
                buildDelegatedAuthenticationPolicy(
                        accessStrategyConfig.getDelegatedAuthenticationPolicy());
        if (!delegatedAuthenticationPolicy.isEmpty()) {
            accessStrategy.put("delegatedAuthenticationPolicy", delegatedAuthenticationPolicy);
        }
        if (hasHttpRequestAccessStrategy(httpRequestConfig)) {
            appendHttpRequestAccessStrategy(accessStrategy, httpRequestConfig);
        }
        return accessStrategy;
    }

    private boolean hasHttpRequestAccessStrategy(
            CasHttpRequestAccessStrategyConfig httpRequestConfig) {
        return httpRequestConfig != null
                && (StrUtil.isNotBlank(httpRequestConfig.getIpAddressPattern())
                        || StrUtil.isNotBlank(httpRequestConfig.getUserAgentPattern())
                        || (httpRequestConfig.getHeaders() != null
                                && !httpRequestConfig.getHeaders().isEmpty()));
    }

    private void appendHttpRequestAccessStrategy(
            Map<String, Object> accessStrategy,
            CasHttpRequestAccessStrategyConfig httpRequestConfig) {
        if (StrUtil.isNotBlank(httpRequestConfig.getIpAddressPattern())) {
            accessStrategy.put(
                    "requiredIpAddressesPatterns",
                    typedCollection(
                            "java.util.ArrayList",
                            List.of(httpRequestConfig.getIpAddressPattern())));
        }
        if (StrUtil.isNotBlank(httpRequestConfig.getUserAgentPattern())) {
            accessStrategy.put(
                    "requiredUserAgentPatterns",
                    typedCollection(
                            "java.util.ArrayList",
                            List.of(httpRequestConfig.getUserAgentPattern())));
        }
        if (httpRequestConfig.getHeaders() != null && !httpRequestConfig.getHeaders().isEmpty()) {
            Map<String, String> headers = new LinkedHashMap<>();
            httpRequestConfig
                    .getHeaders()
                    .forEach(
                            (key, value) -> {
                                if (StrUtil.isNotBlank(key) && StrUtil.isNotBlank(value)) {
                                    headers.put(key, value);
                                }
                            });
            if (!headers.isEmpty()) {
                accessStrategy.put("requiredHeaders", headers);
            }
        }
    }

    private void appendAccessAttributes(
            Map<String, Object> accessStrategy,
            String fieldName,
            Map<String, List<String>> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        Map<String, Object> exportedAttributes = new LinkedHashMap<>();
        attributes.forEach(
                (key, values) -> {
                    if (StrUtil.isBlank(key) || CollUtil.isEmpty(values)) {
                        return;
                    }
                    List<String> filteredValues =
                            values.stream().filter(StrUtil::isNotBlank).toList();
                    if (filteredValues.isEmpty()) {
                        return;
                    }
                    exportedAttributes.put(
                            key, typedCollection("java.util.LinkedHashSet", filteredValues));
                });
        if (!exportedAttributes.isEmpty()) {
            accessStrategy.put(fieldName, exportedAttributes);
        }
    }

    private Map<String, Object> buildDelegatedAuthenticationPolicy(
            CasDelegatedAuthenticationPolicyConfig delegatedAuthenticationPolicy) {
        if (delegatedAuthenticationPolicy == null) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", DELEGATED_AUTH_POLICY_CLASS);
        policy.put(
                "permitUndefined",
                Optional.ofNullable(delegatedAuthenticationPolicy.getPermitUndefined())
                        .orElse(true));
        policy.put(
                "exclusive",
                Optional.ofNullable(delegatedAuthenticationPolicy.getExclusive()).orElse(false));
        if (CollUtil.isNotEmpty(delegatedAuthenticationPolicy.getAllowedProviders())) {
            policy.put(
                    "allowedProviders",
                    typedCollection(
                            "java.util.ArrayList",
                            delegatedAuthenticationPolicy.getAllowedProviders()));
        }
        return policy;
    }

    private Map<String, Object> buildAttributeReleasePolicy(
            CasAttributeReleasePolicyConfig attributeReleasePolicyConfig,
            IdentityMapping identityMapping) {
        CasAttributeReleasePolicyMode mode =
                resolveAttributeReleaseMode(attributeReleasePolicyConfig);
        Map<String, Object> attributeReleasePolicy = new LinkedHashMap<>();
        attributeReleasePolicy.put("@class", resolveAttributeReleasePolicyClass(mode));
        if (mode == CasAttributeReleasePolicyMode.RETURN_ALLOWED) {
            attributeReleasePolicy.put(
                    "allowedAttributes",
                    typedCollection(
                            "java.util.ArrayList",
                            new ArrayList<>(
                                    resolveAllowedAttributes(
                                            attributeReleasePolicyConfig, identityMapping))));
        }
        return attributeReleasePolicy;
    }

    private CasAttributeReleasePolicyMode resolveAttributeReleaseMode(
            CasAttributeReleasePolicyConfig attributeReleasePolicyConfig) {
        if (attributeReleasePolicyConfig == null
                || attributeReleasePolicyConfig.getMode() == null) {
            return CasAttributeReleasePolicyMode.RETURN_ALLOWED;
        }
        return attributeReleasePolicyConfig.getMode();
    }

    private String resolveAttributeReleasePolicyClass(CasAttributeReleasePolicyMode mode) {
        return switch (mode) {
            case RETURN_ALL -> ATTRIBUTE_RELEASE_POLICY_ALL_CLASS;
            case DENY_ALL -> ATTRIBUTE_RELEASE_POLICY_DENY_CLASS;
            case RETURN_ALLOWED -> ATTRIBUTE_RELEASE_POLICY_CLASS;
        };
    }

    private LinkedHashSet<String> resolveAllowedAttributes(
            CasAttributeReleasePolicyConfig attributeReleasePolicyConfig,
            IdentityMapping identityMapping) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        attributes.add("user");
        if (attributeReleasePolicyConfig != null
                && CollUtil.isNotEmpty(attributeReleasePolicyConfig.getAllowedAttributes())) {
            attributeReleasePolicyConfig.getAllowedAttributes().stream()
                    .filter(StrUtil::isNotBlank)
                    .forEach(attributes::add);
            return attributes;
        }
        if (identityMapping != null) {
            addIfPresent(attributes, identityMapping.getUserIdField());
            addIfPresent(attributes, identityMapping.getUserNameField());
            addIfPresent(attributes, identityMapping.getEmailField());
        }
        attributes.removeIf(StrUtil::isBlank);
        return attributes;
    }

    private Map<String, Object> buildMultifactorPolicy(
            CasMultifactorPolicyConfig multifactorPolicyConfig) {
        if (multifactorPolicyConfig == null
                || CollUtil.isEmpty(multifactorPolicyConfig.getProviders())) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", MULTIFACTOR_POLICY_CLASS);
        policy.put(
                "bypassEnabled",
                Optional.ofNullable(multifactorPolicyConfig.getBypassEnabled()).orElse(false));
        policy.put(
                "forceExecution",
                Optional.ofNullable(multifactorPolicyConfig.getForceExecution()).orElse(false));
        if (multifactorPolicyConfig.getFailureMode() != null) {
            policy.put("failureMode", multifactorPolicyConfig.getFailureMode().name());
        }
        if (StrUtil.isNotBlank(multifactorPolicyConfig.getBypassPrincipalAttributeName())) {
            policy.put(
                    "principalAttributeNameTrigger",
                    multifactorPolicyConfig.getBypassPrincipalAttributeName());
        }
        if (StrUtil.isNotBlank(multifactorPolicyConfig.getBypassPrincipalAttributeValue())) {
            policy.put(
                    "principalAttributeValueToMatch",
                    multifactorPolicyConfig.getBypassPrincipalAttributeValue());
        }
        if (Boolean.TRUE.equals(multifactorPolicyConfig.getBypassIfMissingPrincipalAttribute())) {
            policy.put("bypassIfMissingPrincipalAttribute", true);
        }
        policy.put(
                "multifactorAuthenticationProviders",
                typedCollection("java.util.LinkedHashSet", multifactorPolicyConfig.getProviders()));
        return policy;
    }

    private Map<String, Object> buildAuthenticationPolicy(
            CasAuthenticationPolicyConfig authenticationPolicyConfig) {
        if (authenticationPolicyConfig == null
                || (CollUtil.isEmpty(authenticationPolicyConfig.getRequiredAuthenticationHandlers())
                        && CollUtil.isEmpty(
                                authenticationPolicyConfig.getExcludedAuthenticationHandlers()))) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", AUTHENTICATION_POLICY_CLASS);
        CasAuthenticationPolicyCriteriaMode mode =
                Optional.ofNullable(authenticationPolicyConfig.getCriteriaMode())
                        .orElse(CasAuthenticationPolicyCriteriaMode.ALLOWED);
        policy.put("criteria", buildAuthenticationPolicyCriteria(mode, authenticationPolicyConfig));
        return policy;
    }

    private Map<String, Object> buildAuthenticationPolicyCriteria(
            CasAuthenticationPolicyCriteriaMode mode,
            CasAuthenticationPolicyConfig authenticationPolicyConfig) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("@class", resolveAuthenticationCriteriaClass(mode));
        List<String> handlers =
                switch (mode) {
                    case EXCLUDED -> authenticationPolicyConfig.getExcludedAuthenticationHandlers();
                    case NOT_PREVENTED, ALLOWED, ANY, ALL ->
                            authenticationPolicyConfig.getRequiredAuthenticationHandlers();
                };
        if (CollUtil.isNotEmpty(handlers)) {
            criteria.put("handlers", typedCollection("java.util.ArrayList", handlers));
        }
        if (mode == CasAuthenticationPolicyCriteriaMode.ALLOWED
                || mode == CasAuthenticationPolicyCriteriaMode.ANY
                || mode == CasAuthenticationPolicyCriteriaMode.ALL) {
            criteria.put(
                    "tryAll",
                    Optional.ofNullable(authenticationPolicyConfig.getTryAll()).orElse(false));
        }
        return criteria;
    }

    private String resolveAuthenticationCriteriaClass(CasAuthenticationPolicyCriteriaMode mode) {
        return switch (mode) {
            case EXCLUDED ->
                    "org.apereo.cas.services.ExcludedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria";
            case ANY ->
                    "org.apereo.cas.services.AnyAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria";
            case ALL ->
                    "org.apereo.cas.services.AllAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria";
            case NOT_PREVENTED ->
                    "org.apereo.cas.services.NotPreventedAuthenticationHandlerRegisteredServiceAuthenticationPolicyCriteria";
            case ALLOWED ->
                    "org.apereo.cas.services.AllowedAuthenticationHandlersRegisteredServiceAuthenticationPolicyCriteria";
        };
    }

    private Map<String, Object> buildExpirationPolicy(
            CasExpirationPolicyConfig expirationPolicyConfig) {
        if (expirationPolicyConfig == null
                || (StrUtil.isBlank(expirationPolicyConfig.getExpirationDate())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getDeleteWhenExpired())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getNotifyWhenExpired())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getNotifyWhenDeleted()))) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", EXPIRATION_POLICY_CLASS);
        if (StrUtil.isNotBlank(expirationPolicyConfig.getExpirationDate())) {
            policy.put("expirationDate", expirationPolicyConfig.getExpirationDate());
        }
        policy.put(
                "deleteWhenExpired",
                Optional.ofNullable(expirationPolicyConfig.getDeleteWhenExpired()).orElse(false));
        policy.put(
                "notifyWhenExpired",
                Optional.ofNullable(expirationPolicyConfig.getNotifyWhenExpired()).orElse(false));
        policy.put(
                "notifyWhenDeleted",
                Optional.ofNullable(expirationPolicyConfig.getNotifyWhenDeleted()).orElse(false));
        return policy;
    }

    private List<Object> buildContacts(List<CasServiceContactConfig> contacts) {
        if (CollUtil.isEmpty(contacts)) {
            return List.of();
        }
        List<Object> exportedContacts = new ArrayList<>();
        contacts.forEach(
                contact -> {
                    if (contact == null
                            || (StrUtil.isAllBlank(
                                    contact.getName(),
                                    contact.getEmail(),
                                    contact.getPhone(),
                                    contact.getDepartment(),
                                    contact.getType()))) {
                        return;
                    }
                    Map<String, Object> exportedContact = new LinkedHashMap<>();
                    exportedContact.put(
                            "@class", "org.apereo.cas.services.DefaultRegisteredServiceContact");
                    if (StrUtil.isNotBlank(contact.getName())) {
                        exportedContact.put("name", contact.getName());
                    }
                    if (StrUtil.isNotBlank(contact.getEmail())) {
                        exportedContact.put("email", contact.getEmail());
                    }
                    if (StrUtil.isNotBlank(contact.getPhone())) {
                        exportedContact.put("phone", contact.getPhone());
                    }
                    if (StrUtil.isNotBlank(contact.getDepartment())) {
                        exportedContact.put("department", contact.getDepartment());
                    }
                    if (StrUtil.isNotBlank(contact.getType())) {
                        exportedContact.put("type", contact.getType());
                    }
                    exportedContacts.add(exportedContact);
                });
        return exportedContacts;
    }

    private List<Object> typedCollection(String typeName, List<String> values) {
        return List.of(typeName, values);
    }

    private void addIfPresent(LinkedHashSet<String> attributes, String value) {
        if (StrUtil.isNotBlank(value)) {
            attributes.add(value);
        }
    }
}
