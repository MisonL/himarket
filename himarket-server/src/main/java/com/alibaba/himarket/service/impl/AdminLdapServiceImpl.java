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
import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.entity.Administrator;
import com.alibaba.himarket.repository.AdministratorRepository;
import com.alibaba.himarket.service.AdminLdapService;
import com.alibaba.himarket.service.idp.LdapAuthenticator;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.LdapConfig;
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
public class AdminLdapServiceImpl implements AdminLdapService {

    private final AdminAuthConfig adminAuthConfig;

    private final AdministratorRepository administratorRepository;

    private final LdapAuthenticator ldapAuthenticator;

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(adminAuthConfig.getLdapConfigs())
                .filter(configs -> !configs.isEmpty())
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
                                                                .interactiveBrowserLogin(false)
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public AuthResult login(String provider, String username, String password) {
        LdapConfig config = findLdapConfig(provider);
        Map<String, Object> userInfo = ldapAuthenticator.authenticate(config, username, password);
        String adminId = getAdminId(userInfo, config);
        String accessToken = TokenUtil.generateAdminToken(adminId);
        log.info(
                "LDAP authentication successful for admin, provider={}, admin={}",
                provider,
                adminId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    private LdapConfig findLdapConfig(String provider) {
        return Optional.ofNullable(adminAuthConfig.getLdapConfigs())
                .orElse(Collections.emptyList())
                .stream()
                .filter(config -> provider.equals(config.getProvider()) && config.isEnabled())
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.LDAP_CONFIG, provider));
    }

    private String getAdminId(Map<String, Object> userInfo, LdapConfig config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField = StrUtil.blankToDefault(identityMapping.getUserIdField(), "uid");
        String username = getRequiredField(userInfo, userIdField, "LDAP user id");
        Administrator admin =
                administratorRepository
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.UNAUTHORIZED,
                                                "Administrator is not allowed to login"));
        return admin.getAdminId();
    }

    private String getRequiredField(Map<String, Object> userInfo, String field, String label) {
        String value = Convert.toStr(userInfo.get(field));
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, label + " is missing");
        }
        return value;
    }
}
