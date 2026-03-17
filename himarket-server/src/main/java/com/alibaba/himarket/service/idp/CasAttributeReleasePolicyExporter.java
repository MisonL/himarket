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
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

final class CasAttributeReleasePolicyExporter {

    private static final String ATTRIBUTE_RELEASE_POLICY_CLASS =
            "org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy";

    private static final String ATTRIBUTE_RELEASE_POLICY_ALL_CLASS =
            "org.apereo.cas.services.ReturnAllAttributeReleasePolicy";

    private static final String ATTRIBUTE_RELEASE_POLICY_DENY_CLASS =
            "org.apereo.cas.services.DenyAllAttributeReleasePolicy";

    Map<String, Object> export(
            CasAttributeReleasePolicyConfig attributeReleasePolicyConfig,
            IdentityMapping identityMapping) {
        CasAttributeReleasePolicyMode mode =
                resolveAttributeReleaseMode(attributeReleasePolicyConfig);
        Map<String, Object> attributeReleasePolicy = new LinkedHashMap<>();
        attributeReleasePolicy.put("@class", resolveAttributeReleasePolicyClass(mode));
        if (mode == CasAttributeReleasePolicyMode.RETURN_ALLOWED) {
            attributeReleasePolicy.put(
                    "allowedAttributes",
                    CasServiceDefinitionSupport.typedCollection(
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
            CasServiceDefinitionSupport.addIfPresent(attributes, identityMapping.getUserIdField());
            CasServiceDefinitionSupport.addIfPresent(
                    attributes, identityMapping.getUserNameField());
            CasServiceDefinitionSupport.addIfPresent(attributes, identityMapping.getEmailField());
        }
        attributes.removeIf(StrUtil::isBlank);
        return attributes;
    }
}
