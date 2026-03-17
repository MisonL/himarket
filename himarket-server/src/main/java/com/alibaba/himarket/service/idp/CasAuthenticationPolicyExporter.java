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
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAuthenticationPolicyCriteriaMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CasAuthenticationPolicyExporter {

    private static final String AUTHENTICATION_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceAuthenticationPolicy";

    Map<String, Object> export(CasAuthenticationPolicyConfig authenticationPolicyConfig) {
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
        policy.put("criteria", exportCriteria(mode, authenticationPolicyConfig));
        return policy;
    }

    private Map<String, Object> exportCriteria(
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
            criteria.put(
                    "handlers",
                    CasServiceDefinitionSupport.typedCollection("java.util.ArrayList", handlers));
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
}
