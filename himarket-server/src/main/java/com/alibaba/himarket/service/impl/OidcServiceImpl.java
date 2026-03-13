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
import cn.hutool.json.JSONUtil;
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
import com.alibaba.himarket.dto.result.idp.IdpTokenResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.OidcService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.gateway.factory.HTTPClientFactory;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import com.alibaba.himarket.service.idp.OidcIdTokenVerifier;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.OidcConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class OidcServiceImpl implements OidcService {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final RestTemplate restTemplate = HTTPClientFactory.createRestTemplate();

    private final ContextHolder contextHolder;

    private final OidcIdTokenVerifier oidcIdTokenVerifier;

    private final PortalFrontendUrlResolver portalFrontendUrlResolver;

    private final IdpStateCodec idpStateCodec;

    @Override
    public IdpAuthorizeResult buildAuthorizationResult(
            String provider, String apiPrefix, HttpServletRequest request) {
        OidcConfig oidcConfig = findOidcConfig(provider);
        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();

        IdpState idpState = createState(provider, apiPrefix);
        String state = encodeState(idpState);
        String redirectUri = buildRedirectUri();

        // Redirect URL
        String authUrl =
                UriComponentsBuilder.fromUriString(authCodeConfig.getAuthorizationEndpoint())
                        // Authorization code mode
                        .queryParam(IdpConstants.RESPONSE_TYPE, IdpConstants.CODE)
                        .queryParam(IdpConstants.CLIENT_ID, authCodeConfig.getClientId())
                        .queryParam(IdpConstants.REDIRECT_URI, redirectUri)
                        .queryParam(IdpConstants.SCOPE, authCodeConfig.getScopes())
                        .queryParam(IdpConstants.STATE, state)
                        .queryParam(IdpConstants.NONCE, idpState.getNonce())
                        .build()
                        .toUriString();
        return IdpAuthorizeResult.builder().redirectUrl(authUrl).state(state).build();
    }

    @Override
    public AuthResult handleCallback(
            String code, String state, HttpServletRequest request, HttpServletResponse response) {
        IdpStateCookie.assertOidcStateCookieMatches(request, state);

        // Parse state to get provider info
        IdpState idpState = parseState(state);
        String provider = idpState.getProvider();

        OidcConfig oidcConfig = findOidcConfig(provider);

        // Request token with authorization code
        IdpTokenResult tokenResult = requestToken(code, oidcConfig, request);

        // Get user info, prefer ID Token, fallback to UserInfo endpoint
        Map<String, Object> userInfo = getUserInfo(tokenResult, oidcConfig, idpState);

        // Handle user authentication
        String developerId = createOrGetDeveloper(userInfo, oidcConfig);
        String accessToken = TokenUtil.generateDeveloperToken(developerId);

        IdpStateCookie.clearOidcStateCookie(request, response);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getOidcConfigs() != null)
                .map(portal -> portal.getPortalSettingConfig().getOidcConfigs())
                // Get enabled OIDC configs for current portal
                .map(
                        configs ->
                                configs.stream()
                                        .filter(OidcConfig::isEnabled)
                                        .map(
                                                config ->
                                                        IdpResult.builder()
                                                                .provider(config.getProvider())
                                                                .name(config.getName())
                                                                .type("OIDC")
                                                                .build())
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private String buildRedirectUri() {
        return portalFrontendUrlResolver.buildCallbackUrl("/oidc/callback");
    }

    private OidcConfig findOidcConfig(String provider) {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getOidcConfigs() != null)
                // Filter by provider
                .flatMap(
                        portal ->
                                portal.getPortalSettingConfig().getOidcConfigs().stream()
                                        .filter(
                                                config ->
                                                        provider.equals(config.getProvider())
                                                                && config.isEnabled())
                                        .findFirst())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.OIDC_CONFIG, provider));
    }

    private IdpState createState(String provider, String apiPrefix) {
        return IdpState.builder()
                .provider(provider)
                .timestamp(System.currentTimeMillis())
                .nonce(IdUtil.fastSimpleUUID())
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing OIDC provider");
        }
        if (StrUtil.isBlank(idpState.getNonce())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing OIDC nonce");
        }

        return idpState;
    }

    private IdpTokenResult requestToken(
            String code, OidcConfig oidcConfig, HttpServletRequest request) {
        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();
        String redirectUri = buildRedirectUri();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(IdpConstants.GRANT_TYPE, GrantType.AUTHORIZATION_CODE.getType());
        params.add(IdpConstants.CODE, code);
        params.add(IdpConstants.REDIRECT_URI, redirectUri);
        params.add(IdpConstants.CLIENT_ID, authCodeConfig.getClientId());
        params.add(IdpConstants.CLIENT_SECRET, authCodeConfig.getClientSecret());

        log.info(
                "Request OIDC tokens, provider={}, tokenEndpoint={}",
                oidcConfig.getProvider(),
                authCodeConfig.getTokenEndpoint());
        return executeRequest(
                authCodeConfig.getTokenEndpoint(),
                HttpMethod.POST,
                null,
                params,
                IdpTokenResult.class);
    }

    private Map<String, Object> getUserInfo(
            IdpTokenResult tokenResult, OidcConfig oidcConfig, IdpState idpState) {
        // Prefer ID Token
        if (StrUtil.isNotBlank(tokenResult.getIdToken())) {
            log.info("Get user info form id token");
            return parseUserInfo(tokenResult.getIdToken(), oidcConfig, idpState);
        }

        // Fallback: use UserInfo endpoint
        log.warn("ID Token not available, falling back to UserInfo endpoint");
        if (StrUtil.isBlank(tokenResult.getAccessToken())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to get OIDC user info");
        }

        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();
        if (StrUtil.isBlank(authCodeConfig.getUserInfoEndpoint())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "OIDC config missing user info endpoint");
        }

        return requestUserInfo(tokenResult.getAccessToken(), authCodeConfig, oidcConfig);
    }

    private Map<String, Object> parseUserInfo(
            String idToken, OidcConfig oidcConfig, IdpState idpState) {
        Map<String, Object> userInfo =
                oidcIdTokenVerifier.verifyAndExtractClaims(
                        idToken, oidcConfig.getAuthCodeConfig(), idpState.getNonce());

        log.info("Successfully extracted user info from ID Token, sub: {}", userInfo.get("sub"));
        return userInfo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestUserInfo(
            String accessToken, AuthCodeConfig authCodeConfig, OidcConfig oidcConfig) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            log.info("Fetching user info from endpoint: {}", authCodeConfig.getUserInfoEndpoint());
            Map<String, Object> userInfo =
                    executeRequest(
                            authCodeConfig.getUserInfoEndpoint(),
                            HttpMethod.GET,
                            headers,
                            null,
                            Map.class);

            log.info(
                    "Successfully fetched user info from endpoint, sub: {}",
                    userInfo == null ? null : userInfo.get("sub"));
            return userInfo;
        } catch (Exception e) {
            log.error(
                    "Failed to fetch user info from endpoint: {}",
                    authCodeConfig.getUserInfoEndpoint(),
                    e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to get user info");
        }
    }

    private String createOrGetDeveloper(Map<String, Object> userInfo, OidcConfig config) {
        IdentityMapping identityMapping = config.getIdentityMapping();
        // userId & userName & email
        String userIdField =
                StrUtil.isBlank(identityMapping.getUserIdField())
                        ? IdpConstants.SUBJECT
                        : identityMapping.getUserIdField();
        String userNameField =
                StrUtil.isBlank(identityMapping.getUserNameField())
                        ? IdpConstants.NAME
                        : identityMapping.getUserNameField();
        String emailField =
                StrUtil.isBlank(identityMapping.getEmailField())
                        ? IdpConstants.EMAIL
                        : identityMapping.getEmailField();

        Object userIdObj = userInfo.get(userIdField);
        Object userNameObj = userInfo.get(userNameField);
        Object emailObj = userInfo.get(emailField);

        String userId = Convert.toStr(userIdObj);
        String userName = Convert.toStr(userNameObj);
        String email = Convert.toStr(emailObj);
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(userName)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing user ID or user name in ID Token");
        }

        // Reuse existing developer or create new
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
                                            .authType(DeveloperAuthType.OIDC)
                                            .build();

                            return developerService.createExternalDeveloper(param).getDeveloperId();
                        });
    }

    private <T> T executeRequest(
            String url,
            HttpMethod method,
            HttpHeaders headers,
            Object body,
            Class<T> responseType) {
        HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
        log.info("Executing HTTP request to: {}", url);
        ResponseEntity<String> response =
                restTemplate.exchange(url, method, requestEntity, String.class);

        log.info("Received HTTP response from: {}, status: {}", url, response.getStatusCode());

        return JSONUtil.toBean(response.getBody(), responseType);
    }
}
