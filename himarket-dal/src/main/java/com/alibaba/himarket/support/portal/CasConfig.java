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

package com.alibaba.himarket.support.portal;

import com.alibaba.himarket.support.portal.cas.CasAccessStrategyConfig;
import com.alibaba.himarket.support.portal.cas.CasAttributeReleasePolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasLoginConfig;
import com.alibaba.himarket.support.portal.cas.CasMultifactorPolicyConfig;
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat;
import lombok.Data;

@Data
public class CasConfig {

    private String provider;

    private String name;

    private boolean enabled = true;

    /**
     * If true, logout will also redirect to CAS logout endpoint.
     */
    private boolean sloEnabled = false;

    private String serverUrl;

    private String loginEndpoint;

    private String validateEndpoint;

    private String logoutEndpoint;

    private IdentityMapping identityMapping = new IdentityMapping();

    private CasLoginConfig login;

    private CasValidationConfig validation;

    private CasProxyConfig proxy;

    private CasServiceDefinitionConfig serviceDefinition;

    private CasAccessStrategyConfig accessStrategy;

    private CasAttributeReleasePolicyConfig attributeRelease;

    private CasMultifactorPolicyConfig multifactorPolicy;

    public CasLoginConfig resolveLoginConfig() {
        if (login == null) {
            login = new CasLoginConfig();
        }
        return login;
    }

    public CasValidationConfig resolveValidationConfig() {
        if (validation == null) {
            validation = new CasValidationConfig();
        }
        if (validation.getProtocolVersion() == null) {
            validation.setProtocolVersion(resolveLegacyProtocolVersion());
        }
        if (validation.getResponseFormat() == null) {
            validation.setResponseFormat(CasValidationResponseFormat.XML);
        }
        return validation;
    }

    public CasServiceDefinitionConfig resolveServiceDefinition() {
        if (serviceDefinition == null) {
            serviceDefinition = new CasServiceDefinitionConfig();
        }
        return serviceDefinition;
    }

    public CasProxyConfig resolveProxyConfig() {
        if (proxy == null) {
            proxy = new CasProxyConfig();
        }
        return proxy;
    }

    public CasAccessStrategyConfig resolveAccessStrategy() {
        if (accessStrategy == null) {
            accessStrategy = new CasAccessStrategyConfig();
        }
        return accessStrategy;
    }

    public CasAttributeReleasePolicyConfig resolveAttributeRelease() {
        if (attributeRelease == null) {
            attributeRelease = new CasAttributeReleasePolicyConfig();
        }
        return attributeRelease;
    }

    public CasMultifactorPolicyConfig resolveMultifactorPolicy() {
        if (multifactorPolicy == null) {
            multifactorPolicy = new CasMultifactorPolicyConfig();
        }
        return multifactorPolicy;
    }

    private CasProtocolVersion resolveLegacyProtocolVersion() {
        if (validateEndpoint == null) {
            return CasProtocolVersion.CAS3;
        }
        String endpoint = validateEndpoint.toLowerCase();
        if (endpoint.contains("samlvalidate")) {
            return CasProtocolVersion.SAML1;
        }
        if (endpoint.endsWith("/validate")) {
            return CasProtocolVersion.CAS1;
        }
        if (endpoint.contains("servicevalidate")) {
            return endpoint.contains("/p3/") ? CasProtocolVersion.CAS3 : CasProtocolVersion.CAS2;
        }
        return CasProtocolVersion.CAS3;
    }
}
