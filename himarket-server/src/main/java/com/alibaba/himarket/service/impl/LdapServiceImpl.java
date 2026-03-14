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

package com.alibaba.himarket.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.LdapService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.LdapAuthenticator;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LdapServiceImpl implements LdapService {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final ContextHolder contextHolder;

    private final LdapAuthenticator ldapAuthenticator;

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .map(PortalResult::getPortalSettingConfig)
                .map(PortalSettingConfig::getLdapConfigs)
                .filter(configs -> configs != null && !configs.isEmpty())
                .map(
                        configs ->
                                configs.stream()
                                        .filter(LdapConfig::isEnabled)
                                        .map(
                                                config ->
                                                        IdpResult.builder()
                                                                .provider(config.getProvider())
                                                                .name(config.getName())
                                                                .type("LDAP")
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public AuthResult login(String provider, String username, String password) {
        LdapConfig config = findLdapConfig(provider);
        Map<String, Object> userInfo = ldapAuthenticator.authenticate(config, username, password);
        String developerId = createOrGetDeveloper(userInfo, config);
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        log.info(
                "LDAP authentication successful, provider: {}, developer: {}",
                config.getProvider(),
                developerId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    private LdapConfig findLdapConfig(String provider) {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .map(PortalResult::getPortalSettingConfig)
                .map(PortalSettingConfig::getLdapConfigs)
                .orElse(Collections.emptyList())
                .stream()
                .filter(config -> provider.equals(config.getProvider()) && config.isEnabled())
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.LDAP_CONFIG, provider));
    }

    private String createOrGetDeveloper(Map<String, Object> userInfo, LdapConfig config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField = StrUtil.blankToDefault(identityMapping.getUserIdField(), "uid");
        String userNameField = StrUtil.blankToDefault(identityMapping.getUserNameField(), "cn");
        String emailField = StrUtil.blankToDefault(identityMapping.getEmailField(), "mail");

        String userId = getRequiredField(userInfo, userIdField, "LDAP user id");
        String userName = getRequiredField(userInfo, userNameField, "LDAP user name");
        String email = Convert.toStr(userInfo.get(emailField));

        return Optional.ofNullable(
                        developerService.getExternalDeveloper(config.getProvider(), userId))
                .map(DeveloperResult::getDeveloperId)
                .orElseGet(
                        () -> {
                            CreateExternalDeveloperParam param =
                                    CreateExternalDeveloperParam.builder()
                                            .provider(config.getProvider())
                                            .subject(userId)
                                            .displayName(userName)
                                            .email(email)
                                            .authType(DeveloperAuthType.LDAP)
                                            .build();
                            return developerService.createExternalDeveloper(param).getDeveloperId();
                        });
    }

    private String getRequiredField(Map<String, Object> userInfo, String field, String label) {
        String value = Convert.toStr(userInfo.get(field));
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, label + " is missing");
        }
        return value;
    }
}
