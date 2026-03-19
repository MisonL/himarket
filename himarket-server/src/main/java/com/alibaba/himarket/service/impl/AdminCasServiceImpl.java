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
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.idp.CasAuthorizeOptions;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.CasProxyTicketResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.idp.IdpState;
import com.alibaba.himarket.entity.Administrator;
import com.alibaba.himarket.repository.AdministratorRepository;
import com.alibaba.himarket.service.AdminCasService;
import com.alibaba.himarket.service.idp.AdminFrontendUrlResolver;
import com.alibaba.himarket.service.idp.CasLogoutRequestParser;
import com.alibaba.himarket.service.idp.CasProxyTicketClient;
import com.alibaba.himarket.service.idp.CasTicketValidator;
import com.alibaba.himarket.service.idp.FrontendApiUrlBuilder;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import com.alibaba.himarket.service.idp.session.CasLoginContext;
import com.alibaba.himarket.service.idp.session.CasSessionScope;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.cas.CasLoginConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceLogoutType;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
@RequiredArgsConstructor
public class AdminCasServiceImpl implements AdminCasService {

    private static final Logger log = LoggerFactory.getLogger(AdminCasServiceImpl.class);

    private final AdminAuthConfig adminAuthConfig;

    private final AuthSessionConfig authSessionConfig;

    private final AdministratorRepository administratorRepository;

    private final ContextHolder contextHolder;

    private final AuthSessionStore authSessionStore;

    private final CasTicketValidator casTicketValidator;

    private final CasProxyTicketClient casProxyTicketClient;

    private final CasLogoutRequestParser casLogoutRequestParser;

    private final AdminFrontendUrlResolver adminFrontendUrlResolver;

    private final IdpStateCodec idpStateCodec;

    @Override
    public IdpAuthorizeResult buildAuthorizationResult(
            String provider,
            String apiPrefix,
            CasAuthorizeOptions options,
            HttpServletRequest request) {
        CasConfig config = findCasConfig(provider);
        String state = encodeState(createState(provider, apiPrefix));
        String serviceUrl = buildServiceCallbackUrl(apiPrefix, provider, state);
        String loginUrl = buildLoginUrl(config, serviceUrl, options);
        return IdpAuthorizeResult.builder().redirectUrl(loginUrl).state(state).build();
    }

    @Override
    public String handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response) {
        IdpStateCookie.assertAdminCasStateCookieMatches(request, state);
        IdpState idpState = parseState(state);
        CasConfig config = findCasConfig(idpState.getProvider());
        String serviceUrl =
                buildServiceCallbackUrl(idpState.getApiPrefix(), idpState.getProvider(), state);
        Map<String, Object> userInfo =
                casTicketValidator.validate(
                        config,
                        ticket,
                        serviceUrl,
                        resolveProxyCallbackUrl(config, idpState.getApiPrefix()));
        long expirationPolicy = IdpConstants.DEFAULT_EXPIRATION_MILLIS;
        if (cn.hutool.core.convert.Convert.toBool(
                userInfo.get("longTermAuthenticationRequestTokenUsed"), false)) {
            expirationPolicy = 14 * IdpConstants.DEFAULT_EXPIRATION_MILLIS;
        }

        String adminId = getAdminId(userInfo, config);
        String code =
                issueLoginCode(
                        config.getProvider(),
                        adminId,
                        ticket,
                        resolveProxyGrantingTicketIou(config, userInfo),
                        expirationPolicy);
        IdpStateCookie.clearAdminCasStateCookie(request, response);
        return buildFrontendRedirectUrl(code);
    }

    @Override
    public AuthResult exchangeCode(String code) {
        CasLoginContext loginContext = consumeLoginContext(code);

        // Systemic Governance: Enforce Max Sessions Per User
        int maxSessions = authSessionConfig.getCas().getMaxSessionsPerUser();
        if (maxSessions > 0) {
            authSessionStore.revokeUserSessions(loginContext.getScope(), loginContext.getUserId());
        }

        String accessToken = TokenUtil.generateAdminToken(loginContext.getUserId());
        authSessionStore.bindCasSessionToken(
                loginContext.getScope(),
                loginContext.getUserId(),
                loginContext.getSessionIndex(),
                accessToken);
        bindProxyGrantingTicket(loginContext);

        // Systemic Governance: Align Token TTL with Lease Buffer (Risk B)
        long defaultExpiresIn = TokenUtil.getTokenExpiresIn();
        long maxSafetyExpiresIn = IdpConstants.SECONDS_PER_DAY;
        if (loginContext.getTokenExpiresIn() != null) {
            maxSafetyExpiresIn = loginContext.getTokenExpiresIn();
        }

        java.time.Duration leaseBuffer = authSessionConfig.getCas().getSessionLeaseBuffer();
        if (leaseBuffer != null) {
            maxSafetyExpiresIn = Math.max(0, maxSafetyExpiresIn - leaseBuffer.toSeconds());
        }
        long expiresIn = Math.min(defaultExpiresIn, maxSafetyExpiresIn);

        return AuthResult.of(accessToken, expiresIn);
    }

    @Override
    public int handleLogoutRequest(String logoutRequest) {
        String sessionIndex = casLogoutRequestParser.parseSessionIndex(logoutRequest);
        return authSessionStore.revokeCasSession(CasSessionScope.ADMIN, sessionIndex);
    }

    @Override
    public void handleProxyCallback(String pgtIou, String pgtId) {
        authSessionStore.saveCasProxyGrantingTicket(
                pgtIou, pgtId, authSessionConfig.getCas().getLoginCodeTtl());
    }

    @Override
    public CasProxyTicketResult issueProxyTicket(String provider, String targetService) {
        CasConfig config = findCasConfig(provider);
        assertProxyEnabled(config);
        String proxyGrantingTicket =
                authSessionStore.getCasProxyGrantingTicket(
                        CasSessionScope.ADMIN, provider, contextHolder.getUser());
        if (StrUtil.isBlank(proxyGrantingTicket)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "CAS proxy granting ticket is not available for current admin session");
        }
        return CasProxyTicketResult.builder()
                .proxyTicket(
                        casProxyTicketClient.requestProxyTicket(
                                config, proxyGrantingTicket, targetService))
                .build();
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
                                                                .sloEnabled(
                                                                        isSingleLogoutEnabled(
                                                                                config))
                                                                .interactiveBrowserLogin(
                                                                        isInteractiveLoginProvider(
                                                                                config))
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public String buildLogoutRedirectUrl(String provider) {
        CasConfig config = findCasConfig(provider);
        String serviceUrl = adminFrontendUrlResolver.buildCallbackUrl("/login");
        CasServiceLogoutType logoutType = resolveLogoutType(config);
        if (logoutType == CasServiceLogoutType.NONE) {
            return serviceUrl;
        }
        String endpoint =
                StrUtil.blankToDefault(config.getLogoutEndpoint(), IdpConstants.CAS_LOGOUT_PATH);
        String logoutUrl = joinUrl(config.getServerUrl(), endpoint);
        return logoutUrl
                + "?"
                + IdpConstants.SERVICE
                + "="
                + UriUtils.encode(serviceUrl, StandardCharsets.UTF_8);
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

    private String buildLoginUrl(CasConfig config, String serviceUrl, CasAuthorizeOptions options) {
        CasLoginConfig loginConfig = config.resolveLoginConfig();
        boolean gateway =
                resolveLoginFlag(
                        options, CasAuthorizeOptions::getGateway, loginConfig.getGateway());
        boolean renew =
                resolveLoginFlag(options, CasAuthorizeOptions::getRenew, loginConfig.getRenew());
        boolean warn =
                resolveLoginFlag(options, CasAuthorizeOptions::getWarn, loginConfig.getWarn());
        boolean rememberMe =
                resolveLoginFlag(
                        options, CasAuthorizeOptions::getRememberMe, loginConfig.getRememberMe());
        if (gateway && renew) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "CAS authorize request cannot enable gateway and renew together");
        }

        String encodedServiceUrl = UriUtils.encode(serviceUrl, StandardCharsets.UTF_8);
        StringBuilder builder =
                new StringBuilder(buildLoginUrl(config))
                        .append("?")
                        .append(IdpConstants.SERVICE)
                        .append("=")
                        .append(encodedServiceUrl);
        if (gateway) {
            builder.append("&").append(IdpConstants.GATEWAY).append("=true");
        }
        if (renew) {
            builder.append("&").append(IdpConstants.RENEW).append("=true");
        }
        if (warn) {
            builder.append("&").append(IdpConstants.WARN).append("=true");
        }
        if (rememberMe) {
            builder.append("&").append(IdpConstants.REMEMBER_ME).append("=true");
        }
        if (resolveResponseType(config) == CasServiceResponseType.HEADER) {
            builder.append("&")
                    .append(IdpConstants.METHOD)
                    .append("=")
                    .append(CasServiceResponseType.HEADER.name());
        }
        return builder.toString();
    }

    private boolean resolveLoginFlag(
            CasAuthorizeOptions options,
            java.util.function.Function<CasAuthorizeOptions, Boolean> accessor,
            Boolean fallback) {
        return options != null && accessor.apply(options) != null
                ? Boolean.TRUE.equals(accessor.apply(options))
                : Boolean.TRUE.equals(fallback);
    }

    private String buildServiceCallbackUrl(String apiPrefix, String provider, String state) {
        String callbackUrl =
                FrontendApiUrlBuilder.buildApiUrl(
                        adminFrontendUrlResolver.getFrontendBaseUrl(),
                        apiPrefix,
                        "/admins/cas/callback");
        return callbackUrl
                + "?"
                + IdpConstants.PROVIDER
                + "="
                + provider
                + "&"
                + IdpConstants.STATE
                + "="
                + state;
    }

    private boolean isSingleLogoutEnabled(CasConfig config) {
        return resolveLogoutType(config) != CasServiceLogoutType.NONE;
    }

    private boolean isInteractiveLoginProvider(CasConfig config) {
        return resolveResponseType(config) != CasServiceResponseType.HEADER;
    }

    private CasServiceLogoutType resolveLogoutType(CasConfig config) {
        CasServiceLogoutType logoutType = config.resolveServiceDefinition().getLogoutType();
        if (logoutType != null) {
            return logoutType;
        }
        return config.isSloEnabled()
                ? CasServiceLogoutType.BACK_CHANNEL
                : CasServiceLogoutType.NONE;
    }

    private CasServiceResponseType resolveResponseType(CasConfig config) {
        return Optional.ofNullable(config.resolveServiceDefinition().getResponseType())
                .orElse(CasServiceResponseType.REDIRECT);
    }

    private String buildFrontendRedirectUrl(String code) {
        return adminFrontendUrlResolver.buildCallbackUrl("/cas/callback")
                + "?"
                + IdpConstants.CODE
                + "="
                + code;
    }

    private String resolveProxyCallbackUrl(CasConfig config, String apiPrefix) {
        if (!Boolean.TRUE.equals(config.resolveProxyConfig().getEnabled())) {
            return null;
        }
        String callbackPath = config.resolveProxyConfig().getCallbackPath();
        if (StrUtil.startWithAnyIgnoreCase(callbackPath, "http://", "https://")) {
            return callbackPath;
        }
        return FrontendApiUrlBuilder.buildApiUrl(
                adminFrontendUrlResolver.getFrontendBaseUrl(),
                apiPrefix,
                StrUtil.blankToDefault(callbackPath, "/admins/cas/proxy-callback"));
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

    private String issueLoginCode(
            String provider,
            String userId,
            String sessionIndex,
            String proxyGrantingTicketIou,
            long expirationPolicyMillis) {
        String code = IdUtil.fastSimpleUUID();
        authSessionStore.saveCasLoginContext(
                code,
                new CasLoginContext(
                        CasSessionScope.ADMIN,
                        provider,
                        userId,
                        sessionIndex,
                        proxyGrantingTicketIou,
                        expirationPolicyMillis / 1000),
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

    private String resolveProxyGrantingTicketIou(CasConfig config, Map<String, Object> userInfo) {
        if (!Boolean.TRUE.equals(config.resolveProxyConfig().getEnabled())) {
            return null;
        }
        String pgtIou = Convert.toStr(userInfo.get(IdpConstants.PROXY_GRANTING_TICKET));
        if (StrUtil.isBlank(pgtIou)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "CAS validation response is missing proxy granting ticket");
        }
        return pgtIou;
    }

    private void bindProxyGrantingTicket(CasLoginContext loginContext) {
        if (StrUtil.isBlank(loginContext.getProxyGrantingTicketIou())) {
            return;
        }
        String pgtId =
                authSessionStore.consumeCasProxyGrantingTicket(
                        loginContext.getProxyGrantingTicketIou());
        if (StrUtil.isBlank(pgtId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "CAS proxy granting ticket callback has not completed");
        }
        authSessionStore.bindCasProxyGrantingTicket(
                loginContext.getScope(),
                loginContext.getProvider(),
                loginContext.getUserId(),
                loginContext.getSessionIndex(),
                pgtId);
    }

    private void assertProxyEnabled(CasConfig config) {
        CasProxyConfig proxyConfig = config.resolveProxyConfig();
        if (!Boolean.TRUE.equals(proxyConfig.getEnabled())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS proxy is not enabled for this provider");
        }
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
