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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceLogoutType;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import java.util.LinkedHashMap;
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

    private final PortalService portalService;

    private final AdminAuthConfig adminAuthConfig;

    private final CasProxyPolicyExporter proxyPolicyExporter = new CasProxyPolicyExporter();

    private final CasAccessStrategyExporter accessStrategyExporter =
            new CasAccessStrategyExporter();

    private final CasAttributeReleasePolicyExporter attributeReleasePolicyExporter =
            new CasAttributeReleasePolicyExporter();

    private final CasMultifactorPolicyExporter multifactorPolicyExporter =
            new CasMultifactorPolicyExporter();

    private final CasAuthenticationPolicyExporter authenticationPolicyExporter =
            new CasAuthenticationPolicyExporter();

    private final CasExpirationPolicyExporter expirationPolicyExporter =
            new CasExpirationPolicyExporter();

    private final CasContactsExporter contactsExporter = new CasContactsExporter();

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
                CasServiceDefinitionSupport.typedCollection(
                        "java.util.HashSet", resolveProtocols(casConfig)));
        json.put("logoutType", resolveLogoutType(serviceDefinition, casConfig));
        json.put(
                "proxyPolicy",
                proxyPolicyExporter.export(
                        casConfig.resolveProxyConfig(), frontendBaseUrl, proxyCallbackPath));
        json.put(
                "accessStrategy", accessStrategyExporter.export(casConfig.resolveAccessStrategy()));
        json.put(
                "attributeReleasePolicy",
                attributeReleasePolicyExporter.export(
                        casConfig.resolveAttributeRelease(), casConfig.getIdentityMapping()));
        Map<String, Object> multifactorPolicy =
                multifactorPolicyExporter.export(casConfig.resolveMultifactorPolicy());
        if (!multifactorPolicy.isEmpty()) {
            json.put("multifactorPolicy", multifactorPolicy);
        }
        Map<String, Object> authenticationPolicy =
                authenticationPolicyExporter.export(casConfig.resolveAuthenticationPolicy());
        if (!authenticationPolicy.isEmpty()) {
            json.put("authenticationPolicy", authenticationPolicy);
        }
        Map<String, Object> expirationPolicy =
                expirationPolicyExporter.export(casConfig.resolveExpirationPolicy());
        if (!expirationPolicy.isEmpty()) {
            json.put("expirationPolicy", expirationPolicy);
        }
        List<Object> contacts = contactsExporter.export(casConfig.getContacts());
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
}
