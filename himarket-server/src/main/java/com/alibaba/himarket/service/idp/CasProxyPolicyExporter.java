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
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyPolicyMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class CasProxyPolicyExporter {

    private static final String REFUSE_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RefuseRegisteredServiceProxyPolicy";

    private static final String REGEX_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RegexMatchingRegisteredServiceProxyPolicy";

    private static final String REST_PROXY_POLICY_CLASS =
            "org.apereo.cas.services.RestfulRegisteredServiceProxyPolicy";

    Map<String, Object> export(
            CasProxyConfig proxyConfig, String frontendBaseUrl, String defaultProxyCallbackPath) {
        CasProxyPolicyMode policyMode = resolveProxyPolicyMode(proxyConfig);
        if (!Boolean.TRUE.equals(proxyConfig.getEnabled())
                || policyMode == CasProxyPolicyMode.REFUSE) {
            return Map.of("@class", REFUSE_PROXY_POLICY_CLASS);
        }
        if (policyMode == CasProxyPolicyMode.REST) {
            return exportRestPolicy(proxyConfig);
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

    private Map<String, Object> exportRestPolicy(CasProxyConfig proxyConfig) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", REST_PROXY_POLICY_CLASS);
        policy.put("endpoint", proxyConfig.getPolicyEndpoint());
        Map<String, String> configuredHeaders =
                CasServiceDefinitionSupport.resolveStringMap(
                        proxyConfig.getPolicyHeaders(), proxyConfig.getPolicyHeadersJson());
        if (!configuredHeaders.isEmpty()) {
            Map<String, String> headers = new LinkedHashMap<>();
            configuredHeaders.forEach(
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
}
