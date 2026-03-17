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
import com.alibaba.himarket.support.portal.cas.CasMultifactorPolicyConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class CasMultifactorPolicyExporter {

    private static final String MULTIFACTOR_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceMultifactorPolicy";

    Map<String, Object> export(CasMultifactorPolicyConfig multifactorPolicyConfig) {
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
                CasServiceDefinitionSupport.typedCollection(
                        "java.util.LinkedHashSet", multifactorPolicyConfig.getProviders()));
        return policy;
    }
}
