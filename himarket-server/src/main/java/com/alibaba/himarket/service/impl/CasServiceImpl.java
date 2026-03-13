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
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.idp.IdpState;
import com.alibaba.himarket.service.CasService;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.CasTicketValidator;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasServiceImpl implements CasService {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final ContextHolder contextHolder;

    private final CasTicketValidator casTicketValidator;

    private final PortalFrontendUrlResolver portalFrontendUrlResolver;

    private final IdpStateCodec idpStateCodec;

    @Override
    public IdpAuthorizeResult buildAuthorizationResult(
            String provider, String apiPrefix, HttpServletRequest request) {
        CasConfig config = findCasConfig(provider);
        String state = encodeState(createState(provider, apiPrefix));
        String serviceUrl = buildFrontendCallbackUrl(state);
        String loginUrl =
                UriComponentsBuilder.fromUriString(buildLoginUrl(config))
                        .queryParam(IdpConstants.SERVICE, serviceUrl)
                        .build()
                        .toUriString();
        return IdpAuthorizeResult.builder().redirectUrl(loginUrl).state(state).build();
    }

    @Override
    public AuthResult handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response) {
        IdpStateCookie.assertCasStateCookieMatches(request, state);
        IdpState idpState = parseState(state);
        CasConfig config = findCasConfig(idpState.getProvider());
        String serviceUrl = buildFrontendCallbackUrl(state);
        Map<String, Object> userInfo = casTicketValidator.validate(config, ticket, serviceUrl);
        String developerId = createOrGetDeveloper(userInfo, config);
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        IdpStateCookie.clearCasStateCookie(request, response);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getCasConfigs() != null)
                .map(portal -> portal.getPortalSettingConfig().getCasConfigs())
                .map(
                        configs ->
                                configs.stream()
                                        .filter(CasConfig::isEnabled)
                                        .map(
                                                config ->
                                                        IdpResult.builder()
                                                                .provider(config.getProvider())
                                                                .name(config.getName())
                                                                .type("CAS")
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private CasConfig findCasConfig(String provider) {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getCasConfigs() != null)
                .flatMap(
                        portal ->
                                portal.getPortalSettingConfig().getCasConfigs().stream()
                                        .filter(
                                                config ->
                                                        provider.equals(config.getProvider())
                                                                && config.isEnabled())
                                        .findFirst())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CAS_CONFIG, provider));
    }

    private String buildLoginUrl(CasConfig config) {
        String endpoint =
                StrUtil.blankToDefault(config.getLoginEndpoint(), IdpConstants.CAS_LOGIN_PATH);
        return joinUrl(config.getServerUrl(), endpoint);
    }

    private String buildFrontendCallbackUrl(String state) {
        return UriComponentsBuilder.fromUriString(
                        portalFrontendUrlResolver.buildCallbackUrl("/cas/callback"))
                .queryParam(IdpConstants.STATE, state)
                .build()
                .toUriString();
    }

    private IdpState createState(String provider, String apiPrefix) {
        return IdpState.builder()
                .provider(provider)
                .timestamp(System.currentTimeMillis())
                .apiPrefix(apiPrefix)
                .build();
    }

    private String encodeState(IdpState state) {
        return idpStateCodec.encode(state);
    }

    private IdpState parseState(String encodedState) {
        IdpState idpState = idpStateCodec.decode(encodedState);
        if (idpState.getTimestamp() != null
                && System.currentTimeMillis() - idpState.getTimestamp()
                        > IdpConstants.IDP_STATE_TTL_MILLIS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request has expired");
        }
        if (StrUtil.isBlank(idpState.getProvider())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing CAS provider");
        }
        return idpState;
    }

    private String createOrGetDeveloper(Map<String, Object> userInfo, CasConfig config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField = StrUtil.blankToDefault(identityMapping.getUserIdField(), "user");
        String userNameField = StrUtil.blankToDefault(identityMapping.getUserNameField(), "user");
        String emailField = StrUtil.blankToDefault(identityMapping.getEmailField(), "mail");

        String userId = getRequiredField(userInfo, userIdField, "CAS user id");
        String userName = getRequiredField(userInfo, userNameField, "CAS user name");
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
                                            .authType(DeveloperAuthType.CAS)
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

    private String joinUrl(String baseUrl, String path) {
        if (StrUtil.startWithAnyIgnoreCase(path, "http://", "https://")) {
            return path;
        }
        return StrUtil.removeSuffix(baseUrl, "/") + StrUtil.addPrefixIfNot(path, "/");
    }
}
