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
import com.alibaba.himarket.support.portal.cas.CasAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasDelegatedAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasHttpRequestAccessStrategyConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CasAccessStrategyExporter {

    private static final String ACCESS_STRATEGY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy";

    private static final String HTTP_REQUEST_ACCESS_STRATEGY_CLASS =
            "org.apereo.cas.services.HttpRequestRegisteredServiceAccessStrategy";

    private static final String DELEGATED_AUTH_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceDelegatedAuthenticationPolicy";

    Map<String, Object> export(CasAccessStrategyConfig accessStrategyConfig) {
        Map<String, Object> accessStrategy = new LinkedHashMap<>();
        if (accessStrategyConfig == null) {
            return accessStrategy;
        }
        CasHttpRequestAccessStrategyConfig httpRequestConfig =
                accessStrategyConfig.getHttpRequest();
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
                accessStrategy,
                "requiredAttributes",
                CasServiceDefinitionSupport.resolveStringListMap(
                        accessStrategyConfig.getRequiredAttributes(),
                        accessStrategyConfig.getRequiredAttributesJson()));
        appendAccessAttributes(
                accessStrategy,
                "rejectedAttributes",
                CasServiceDefinitionSupport.resolveStringListMap(
                        accessStrategyConfig.getRejectedAttributes(),
                        accessStrategyConfig.getRejectedAttributesJson()));
        Map<String, Object> delegatedAuthenticationPolicy =
                exportDelegatedAuthenticationPolicy(
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
                        || !CasServiceDefinitionSupport.resolveStringMap(
                                        httpRequestConfig.getHeaders(),
                                        httpRequestConfig.getHeadersJson())
                                .isEmpty());
    }

    private void appendHttpRequestAccessStrategy(
            Map<String, Object> accessStrategy,
            CasHttpRequestAccessStrategyConfig httpRequestConfig) {
        if (StrUtil.isNotBlank(httpRequestConfig.getIpAddressPattern())) {
            accessStrategy.put(
                    "requiredIpAddressesPatterns",
                    CasServiceDefinitionSupport.typedCollection(
                            "java.util.ArrayList",
                            List.of(httpRequestConfig.getIpAddressPattern())));
        }
        if (StrUtil.isNotBlank(httpRequestConfig.getUserAgentPattern())) {
            accessStrategy.put(
                    "requiredUserAgentPatterns",
                    CasServiceDefinitionSupport.typedCollection(
                            "java.util.ArrayList",
                            List.of(httpRequestConfig.getUserAgentPattern())));
        }
        Map<String, String> configuredHeaders =
                CasServiceDefinitionSupport.resolveStringMap(
                        httpRequestConfig.getHeaders(), httpRequestConfig.getHeadersJson());
        if (!configuredHeaders.isEmpty()) {
            Map<String, String> headers = new LinkedHashMap<>();
            configuredHeaders.forEach(
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
                            key,
                            CasServiceDefinitionSupport.typedCollection(
                                    "java.util.LinkedHashSet", filteredValues));
                });
        if (!exportedAttributes.isEmpty()) {
            accessStrategy.put(fieldName, exportedAttributes);
        }
    }

    private Map<String, Object> exportDelegatedAuthenticationPolicy(
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
                    CasServiceDefinitionSupport.typedCollection(
                            "java.util.ArrayList",
                            delegatedAuthenticationPolicy.getAllowedProviders()));
        }
        return policy;
    }
}
