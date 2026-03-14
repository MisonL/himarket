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
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.config.AuthSessionConfig;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.idp.IdpState;
import com.alibaba.himarket.entity.Administrator;
import com.alibaba.himarket.repository.AdministratorRepository;
import com.alibaba.himarket.service.AdminCasService;
import com.alibaba.himarket.service.idp.AdminFrontendUrlResolver;
import com.alibaba.himarket.service.idp.CasLogoutRequestParser;
import com.alibaba.himarket.service.idp.CasTicketValidator;
import com.alibaba.himarket.service.idp.FrontendApiUrlBuilder;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import com.alibaba.himarket.service.idp.session.CasLoginContext;
import com.alibaba.himarket.service.idp.session.CasSessionScope;
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
public class AdminCasServiceImpl implements AdminCasService {

    private final AdminAuthConfig adminAuthConfig;

    private final AuthSessionConfig authSessionConfig;

    private final AdministratorRepository administratorRepository;

    private final AuthSessionStore authSessionStore;

    private final CasTicketValidator casTicketValidator;

    private final CasLogoutRequestParser casLogoutRequestParser;

    private final AdminFrontendUrlResolver adminFrontendUrlResolver;

    private final IdpStateCodec idpStateCodec;

    @Override
    public IdpAuthorizeResult buildAuthorizationResult(
            String provider, String apiPrefix, HttpServletRequest request) {
        CasConfig config = findCasConfig(provider);
        String state = encodeState(createState(provider, apiPrefix));
        String serviceUrl = buildServiceCallbackUrl(apiPrefix, state);
        String loginUrl =
                UriComponentsBuilder.fromUriString(buildLoginUrl(config))
                        .queryParam(IdpConstants.SERVICE, serviceUrl)
                        .build()
                        .toUriString();
        return IdpAuthorizeResult.builder().redirectUrl(loginUrl).state(state).build();
    }

    @Override
    public String handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response) {
        IdpStateCookie.assertAdminCasStateCookieMatches(request, state);
        IdpState idpState = parseState(state);
        CasConfig config = findCasConfig(idpState.getProvider());
        String serviceUrl = buildServiceCallbackUrl(idpState.getApiPrefix(), state);
        Map<String, Object> userInfo = casTicketValidator.validate(config, ticket, serviceUrl);
        String adminId = getAdminId(userInfo, config);
        String code = issueLoginCode(config.getProvider(), adminId, ticket);
        IdpStateCookie.clearAdminCasStateCookie(request, response);
        return buildFrontendRedirectUrl(code);
    }

    @Override
    public AuthResult exchangeCode(String code) {
        CasLoginContext loginContext = consumeLoginContext(code);
        String accessToken = TokenUtil.generateAdminToken(loginContext.getUserId());
        authSessionStore.bindCasSessionToken(
                loginContext.getScope(), loginContext.getSessionIndex(), accessToken);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public int handleLogoutRequest(String logoutRequest) {
        String sessionIndex = casLogoutRequestParser.parseSessionIndex(logoutRequest);
        return authSessionStore.revokeCasSession(CasSessionScope.ADMIN, sessionIndex);
    }

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(adminAuthConfig.getCasConfigs())
                .filter(configs -> !configs.isEmpty())
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
                                                                .sloEnabled(config.isSloEnabled())
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public String buildLogoutRedirectUrl(String provider) {
        CasConfig config = findCasConfig(provider);
        if (!config.isSloEnabled()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS SLO is not enabled for this provider");
        }

        String endpoint =
                StrUtil.blankToDefault(config.getLogoutEndpoint(), IdpConstants.CAS_LOGOUT_PATH);
        String logoutUrl = joinUrl(config.getServerUrl(), endpoint);
        String serviceUrl = adminFrontendUrlResolver.buildCallbackUrl("/login");
        return UriComponentsBuilder.fromUriString(logoutUrl)
                .queryParam(IdpConstants.SERVICE, serviceUrl)
                .build()
                .toUriString();
    }

    private CasConfig findCasConfig(String provider) {
        return Optional.ofNullable(adminAuthConfig.getCasConfigs())
                .orElse(Collections.emptyList())
                .stream()
                .filter(config -> provider.equals(config.getProvider()) && config.isEnabled())
                .findFirst()
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

    private String buildServiceCallbackUrl(String apiPrefix, String state) {
        String callbackUrl =
                FrontendApiUrlBuilder.buildApiUrl(
                        adminFrontendUrlResolver.getFrontendBaseUrl(),
                        apiPrefix,
                        "/admins/cas/callback");
        return UriComponentsBuilder.fromUriString(callbackUrl)
                .queryParam(IdpConstants.STATE, state)
                .build()
                .toUriString();
    }

    private String buildFrontendRedirectUrl(String code) {
        return UriComponentsBuilder.fromUriString(
                        adminFrontendUrlResolver.buildCallbackUrl("/cas/callback"))
                .queryParam(IdpConstants.CODE, code)
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

        Long timestamp = idpState.getTimestamp();
        if (timestamp == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid state");
        }
        if (System.currentTimeMillis() - timestamp > IdpConstants.IDP_STATE_TTL_MILLIS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request has expired");
        }
        if (StrUtil.isBlank(idpState.getProvider())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing CAS provider");
        }
        return idpState;
    }

    private String issueLoginCode(String provider, String userId, String sessionIndex) {
        String code = IdUtil.fastSimpleUUID();
        authSessionStore.saveCasLoginContext(
                code,
                new CasLoginContext(CasSessionScope.ADMIN, provider, userId, sessionIndex),
                authSessionConfig.getCas().getLoginCodeTtl());
        return code;
    }

    private CasLoginContext consumeLoginContext(String code) {
        CasLoginContext loginContext = authSessionStore.consumeCasLoginContext(code);
        if (loginContext == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS login code is invalid or expired");
        }
        if (loginContext.getScope() != CasSessionScope.ADMIN) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS login code does not belong to admin flow");
        }
        return loginContext;
    }

    private String getAdminId(Map<String, Object> userInfo, CasConfig config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField = StrUtil.blankToDefault(identityMapping.getUserIdField(), "user");

        String username = getRequiredField(userInfo, userIdField, "CAS user id");
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

    private String joinUrl(String baseUrl, String path) {
        if (StrUtil.startWithAnyIgnoreCase(path, "http://", "https://")) {
            return path;
        }
        return StrUtil.removeSuffix(baseUrl, "/") + StrUtil.addPrefixIfNot(path, "/");
    }
}
