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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.params.idp.OAuth2BrowserLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2DirectLoginParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.idp.IdpState;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.OAuth2Service;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.gateway.factory.HTTPClientFactory;
import com.alibaba.himarket.service.idp.CasJsonTicketValidationParser;
import com.alibaba.himarket.service.idp.CasSamlTicketValidationParser;
import com.alibaba.himarket.service.idp.CasTicketValidationParser;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.JwtBearerTokenVerifier;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.service.idp.TrustedHeaderIdentityResolver;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtAlgorithm;
import com.alibaba.himarket.support.enums.JwtDirectIdentitySource;
import com.alibaba.himarket.support.enums.JwtDirectTokenSource;
import com.alibaba.himarket.support.portal.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2ServiceImpl implements OAuth2Service {

    private static final List<String> DEFAULT_BROWSER_TOKEN_FIELDS =
            List.of("access_token", "id_token", "jwt", "token");

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final IdpService idpService;

    private final ContextHolder contextHolder;

    private final JwtBearerTokenVerifier jwtBearerTokenVerifier;

    private final PortalFrontendUrlResolver portalFrontendUrlResolver;

    private final IdpStateCodec idpStateCodec;

    private final TrustedHeaderIdentityResolver trustedHeaderIdentityResolver;

    private final CasTicketValidationParser casTicketValidationParser;

    private final CasJsonTicketValidationParser casJsonTicketValidationParser;

    private final CasSamlTicketValidationParser casSamlTicketValidationParser;

    private final RestTemplate restTemplate = HTTPClientFactory.createRestTemplate();

    @Override
    public AuthResult authenticate(String grantType, String jwtToken) {
        if (!GrantType.JWT_BEARER.getType().equals(grantType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported grant type");
        }

        JWT unverified = parseJwtUnverified(jwtToken);
        if (isLegacyJwtBearer(unverified)) {
            return authenticateLegacy(unverified, jwtToken);
        }
        return authenticateStandard(unverified, jwtToken);
    }

    @Override
    public AuthResult authenticateDirect(OAuth2DirectLoginParam param) {
        OAuth2Config config = findJwtBearerConfig(param.getProvider());
        requireJwtVerificationConfig(config);
        return issueDeveloperToken(config, resolveDirectIdentity(config, param.getJwt()));
    }

    private JWT parseJwtUnverified(String jwtToken) {
        try {
            return JWTUtil.parseToken(jwtToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JWT");
        }
    }

    private boolean isLegacyJwtBearer(JWT jwt) {
        String kid = Convert.toStr(jwt.getHeader(JwtConstants.HEADER_KID), null);
        String provider = Convert.toStr(jwt.getPayload(JwtConstants.PAYLOAD_PROVIDER), null);
        String portalId = Convert.toStr(jwt.getPayload(JwtConstants.PAYLOAD_PORTAL), null);
        return StrUtil.isNotBlank(kid)
                && StrUtil.isNotBlank(provider)
                && StrUtil.isNotBlank(portalId);
    }

    private AuthResult authenticateLegacy(JWT jwt, String jwtToken) {
        String kid = Convert.toStr(jwt.getHeader(JwtConstants.HEADER_KID), null);
        String provider = Convert.toStr(jwt.getPayload(JwtConstants.PAYLOAD_PROVIDER), null);
        String portalId = Convert.toStr(jwt.getPayload(JwtConstants.PAYLOAD_PORTAL), null);

        if (StrUtil.isBlank(kid)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT header missing field kid");
        }
        if (StrUtil.isBlank(provider)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT payload missing field provider");
        }
        if (StrUtil.isBlank(portalId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT payload missing field portal");
        }

        // Get OAuth2 config by provider
        PortalResult portal = portalService.getPortal(portalId);
        List<OAuth2Config> oauth2Configs =
                Optional.ofNullable(portal.getPortalSettingConfig())
                        .map(PortalSettingConfig::getOauth2Configs)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                portalId));

        OAuth2Config oAuth2Config =
                oauth2Configs.stream()
                        // JWT Bearer mode
                        .filter(OAuth2Config::isEnabled)
                        .filter(config -> config.getGrantType() == GrantType.JWT_BEARER)
                        .filter(
                                config ->
                                        config.getJwtBearerConfig() != null
                                                && CollUtil.isNotEmpty(
                                                        config.getJwtBearerConfig()
                                                                .getPublicKeys()))
                        // Provider identifier
                        .filter(config -> config.getProvider().equals(provider))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                provider));

        // Find public key by kid
        JwtBearerConfig jwtConfig = oAuth2Config.getJwtBearerConfig();
        PublicKeyConfig publicKeyConfig =
                jwtConfig.getPublicKeys().stream()
                        .filter(key -> kid.equals(key.getKid()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PUBLIC_KEY, kid));

        // Verify signature
        if (!verifySignature(jwt, publicKeyConfig)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT signature verification failed");
        }

        // Validate claims
        validateJwtClaims(jwt);

        // Developer
        String developerId = createOrGetDeveloper(jwt, oAuth2Config);

        // Generate access token
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        log.info(
                "JWT Bearer authentication successful, provider: {}, developer: {}",
                oAuth2Config.getProvider(),
                developerId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public IdpAuthorizeResult buildAuthorizationResult(String provider, String apiPrefix) {
        OAuth2Config config = findJwtBearerConfig(provider);
        JwtBearerConfig jwtBearerConfig = requireJwtDirectConfig(config);
        IdpState state =
                IdpState.builder()
                        .provider(provider)
                        .timestamp(System.currentTimeMillis())
                        .nonce(IdUtil.fastSimpleUUID())
                        .apiPrefix(apiPrefix)
                        .build();
        String encodedState = idpStateCodec.encode(state);
        String callbackUrl = buildOAuth2CallbackUrl(provider, encodedState);
        String redirectUrl =
                appendQueryParam(
                        appendQueryParam(
                                jwtBearerConfig.getAuthorizationEndpoint(),
                                IdpConstants.STATE,
                                encodedState),
                        jwtBearerConfig.resolveAuthorizationServiceField(),
                        callbackUrl);
        return IdpAuthorizeResult.builder().redirectUrl(redirectUrl).state(encodedState).build();
    }

    @Override
    public AuthResult completeBrowserLogin(OAuth2BrowserLoginParam param) {
        IdpState state = parseState(param.getState());
        String provider = resolveProvider(param.getProvider(), state.getProvider());
        OAuth2Config config = findJwtBearerConfig(provider);
        JwtBearerConfig jwtBearerConfig = requireJwtDirectConfig(config);
        return switch (jwtBearerConfig.resolveAcquireMode()) {
            case DIRECT -> authenticateDirectBrowserLogin(config, param.getJwt());
            case TICKET_EXCHANGE ->
                    authenticateTicketExchangeBrowserLogin(
                            config, jwtBearerConfig, param.getTicket(), param.getState());
            case TICKET_VALIDATE ->
                    authenticateTicketValidateBrowserLogin(
                            config, jwtBearerConfig, param.getTicket(), param.getState());
        };
    }

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .map(PortalResult::getPortalSettingConfig)
                .map(PortalSettingConfig::getOauth2Configs)
                .map(
                        configs ->
                                configs.stream()
                                        .filter(OAuth2Config::isEnabled)
                                        .map(
                                                config ->
                                                        IdpResult.builder()
                                                                .provider(config.getProvider())
                                                                .name(config.getName())
                                                                .type("OAUTH2")
                                                                .interactiveBrowserLogin(
                                                                        supportsInteractiveBrowserLogin(
                                                                                config))
                                                                .directTokenLogin(
                                                                        supportsDirectTokenLogin(
                                                                                config))
                                                                .trustedHeaderLogin(
                                                                        supportsTrustedHeaderLogin(
                                                                                config))
                                                                .build())
                                        .filter(
                                                provider ->
                                                        Boolean.TRUE.equals(
                                                                        provider
                                                                                .getInteractiveBrowserLogin())
                                                                || Boolean.TRUE.equals(
                                                                        provider
                                                                                .getDirectTokenLogin())
                                                                || Boolean.TRUE.equals(
                                                                        provider
                                                                                .getTrustedHeaderLogin()))
                                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public AuthResult authenticateTrustedHeader(String provider, HttpServletRequest request) {
        OAuth2Config config = findTrustedHeaderConfig(provider);
        return issueDeveloperToken(config, trustedHeaderIdentityResolver.resolve(request, config));
    }

    private boolean supportsInteractiveBrowserLogin(OAuth2Config config) {
        if (config.getGrantType() != GrantType.JWT_BEARER || config.getJwtBearerConfig() == null) {
            return false;
        }
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        return StrUtil.isNotBlank(jwtBearerConfig.getAuthorizationEndpoint())
                && jwtBearerConfig.resolveTokenSource() != JwtDirectTokenSource.BODY;
    }

    private boolean supportsDirectTokenLogin(OAuth2Config config) {
        return config.getGrantType() == GrantType.JWT_BEARER
                && config.getJwtBearerConfig() != null
                && hasJwtVerificationConfig(config.getJwtBearerConfig());
    }

    private boolean supportsTrustedHeaderLogin(OAuth2Config config) {
        return config.getGrantType() == GrantType.TRUSTED_HEADER
                && config.getTrustedHeaderConfig() != null
                && config.getTrustedHeaderConfig().resolveEnabled();
    }

    private AuthResult authenticateDirectBrowserLogin(OAuth2Config config, String jwtToken) {
        if (StrUtil.isBlank(jwtToken)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing JWT for browser login");
        }
        return issueDeveloperToken(config, resolveDirectIdentity(config, jwtToken));
    }

    private AuthResult authenticateTicketExchangeBrowserLogin(
            OAuth2Config config, JwtBearerConfig jwtBearerConfig, String ticket, String state) {
        if (StrUtil.isBlank(ticket)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing ticket for browser login");
        }
        String jwtToken =
                exchangeTicketForJwt(config.getProvider(), jwtBearerConfig, ticket, state);
        return issueDeveloperToken(config, resolveIdentity(config, jwtToken));
    }

    private AuthResult authenticateTicketValidateBrowserLogin(
            OAuth2Config config, JwtBearerConfig jwtBearerConfig, String ticket, String state) {
        if (StrUtil.isBlank(ticket)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing ticket for browser login");
        }
        Map<String, Object> claims =
                validateTicketForClaims(config.getProvider(), jwtBearerConfig, ticket, state);
        return issueDeveloperToken(config, claims);
    }

    private AuthResult issueDeveloperToken(OAuth2Config config, Map<String, Object> identity) {
        String developerId = createOrGetDeveloper(identity, config, JSONUtil.toJsonStr(identity));
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        log.info(
                "OAuth2 browser authentication successful, provider: {}, developer: {}",
                config.getProvider(),
                developerId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    private Map<String, Object> resolveDirectIdentity(OAuth2Config config, String jwtToken) {
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        if (jwtBearerConfig.resolveIdentitySource() == JwtDirectIdentitySource.USERINFO) {
            verifyJwtAgainstConfig(config, jwtToken);
            return fetchUserInfo(jwtBearerConfig, jwtToken);
        }
        return resolveClaimsFromJwt(config, jwtToken);
    }

    private Map<String, Object> resolveIdentity(OAuth2Config config, String jwtToken) {
        return resolveDirectIdentity(config, jwtToken);
    }

    private Map<String, Object> resolveClaimsFromJwt(OAuth2Config config, String jwtToken) {
        if (hasJwkValidation(config.getJwtBearerConfig())) {
            Jwt verified = jwtBearerTokenVerifier.verify(jwtToken, config.getJwtBearerConfig());
            return new HashMap<>(verified.getClaims());
        }

        JWT parsedJwt = parseJwtUnverified(jwtToken);
        verifyJwtWithPublicKeys(parsedJwt, config);
        validateJwtClaims(parsedJwt);
        validateOptionalIssuerAndAudience(parsedJwt, config.getJwtBearerConfig());
        return new HashMap<>(parsedJwt.getPayloads());
    }

    private void verifyJwtAgainstConfig(OAuth2Config config, String jwtToken) {
        if (hasJwkValidation(config.getJwtBearerConfig())) {
            jwtBearerTokenVerifier.verify(jwtToken, config.getJwtBearerConfig());
            return;
        }
        JWT parsedJwt = parseJwtUnverified(jwtToken);
        verifyJwtWithPublicKeys(parsedJwt, config);
        validateJwtClaims(parsedJwt);
        validateOptionalIssuerAndAudience(parsedJwt, config.getJwtBearerConfig());
    }

    private void verifyJwtWithPublicKeys(JWT jwt, OAuth2Config config) {
        List<PublicKeyConfig> publicKeys = config.getJwtBearerConfig().getPublicKeys();
        if (CollUtil.isEmpty(publicKeys)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} missing JWT verification config",
                            config.getProvider()));
        }

        String kid = Convert.toStr(jwt.getHeader(JwtConstants.HEADER_KID), null);
        List<PublicKeyConfig> matchedKeys =
                StrUtil.isBlank(kid)
                        ? publicKeys
                        : publicKeys.stream()
                                .filter(key -> StrUtil.equals(key.getKid(), kid))
                                .collect(Collectors.toList());
        if (matchedKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT key ID does not match");
        }
        boolean verified = matchedKeys.stream().anyMatch(key -> verifySignature(jwt, key));
        if (!verified) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT signature verification failed");
        }
    }

    private void validateOptionalIssuerAndAudience(JWT jwt, JwtBearerConfig config) {
        if (StrUtil.isNotBlank(config.getIssuer())) {
            String issuer = Convert.toStr(jwt.getPayload(JwtConstants.PAYLOAD_ISS), null);
            if (!StrUtil.equals(config.getIssuer(), issuer)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT issuer does not match");
            }
        }

        if (CollUtil.isNotEmpty(config.getAudiences())) {
            List<String> audiences = normalizeAudiences(jwt.getPayload(JwtConstants.PAYLOAD_AUD));
            if (!audienceMatches(config, audiences)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "JWT audience does not match expected audiences");
            }
        }
    }

    private String exchangeTicketForJwt(
            String provider, JwtBearerConfig jwtBearerConfig, String ticket, String state) {
        String responseBody = performTicketRequest(provider, jwtBearerConfig, ticket, state);
        String tokenField = jwtBearerConfig.resolveTicketExchangeTokenField();
        String jwtToken = JSONUtil.parseObj(responseBody).getByPath(tokenField, String.class);
        if (StrUtil.isBlank(jwtToken)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Ticket exchange response is missing JWT token");
        }
        return jwtToken;
    }

    private Map<String, Object> validateTicketForClaims(
            String provider, JwtBearerConfig jwtBearerConfig, String ticket, String state) {
        String responseBody = performTicketRequest(provider, jwtBearerConfig, ticket, state);
        return normalizeTicketValidationIdentity(parseTicketValidationResponse(responseBody));
    }

    private Map<String, Object> parseTicketValidationResponse(String responseBody) {
        String normalizedBody = StrUtil.trim(responseBody);
        if (StrUtil.isBlank(normalizedBody)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS validation returned empty response");
        }
        if (StrUtil.startWith(normalizedBody, "{")) {
            return casJsonTicketValidationParser.parse(normalizedBody);
        }
        if (!StrUtil.startWith(normalizedBody, "<")) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unsupported CAS validation response format");
        }
        if (looksLikeSamlValidationResponse(normalizedBody)) {
            return casSamlTicketValidationParser.parse(normalizedBody);
        }
        return casTicketValidationParser.parse(normalizedBody);
    }

    private boolean looksLikeSamlValidationResponse(String responseBody) {
        return StrUtil.containsIgnoreCase(responseBody, "StatusCode")
                && StrUtil.containsIgnoreCase(responseBody, "NameIdentifier");
    }

    private Map<String, Object> normalizeTicketValidationIdentity(Map<String, Object> claims) {
        Map<String, Object> normalizedClaims = new HashMap<>(claims);
        if (normalizedClaims.containsKey(IdpConstants.SUBJECT)) {
            return normalizedClaims;
        }
        String user = Convert.toStr(normalizedClaims.get("user"), null);
        if (StrUtil.isNotBlank(user)) {
            normalizedClaims.put(IdpConstants.SUBJECT, user);
        }
        return normalizedClaims;
    }

    private String performTicketRequest(
            String provider, JwtBearerConfig jwtBearerConfig, String ticket, String state) {
        String url = jwtBearerConfig.getTicketExchangeUrl();
        if (isCasSamlValidationRequest(url)) {
            return executeCasSamlValidationRequest(provider, url, ticket, state);
        }

        HttpMethod method = HttpMethod.valueOf(jwtBearerConfig.resolveTicketExchangeMethod());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(jwtBearerConfig.resolveTicketExchangeTicketField(), ticket);
        String serviceField = jwtBearerConfig.resolveTicketExchangeServiceField();
        String callbackUrl = buildOAuth2CallbackUrl(provider, state);
        if (StrUtil.isNotBlank(serviceField)) {
            params.add(serviceField, callbackUrl);
        }

        HttpEntity<?> entity;
        if (method == HttpMethod.GET) {
            String targetUrl =
                    appendQueryParam(
                            url, jwtBearerConfig.resolveTicketExchangeTicketField(), ticket);
            if (StrUtil.isNotBlank(serviceField)) {
                targetUrl = appendQueryParam(targetUrl, serviceField, callbackUrl);
            }
            entity = HttpEntity.EMPTY;
            return executeStringRequest(targetUrl, method, entity);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        entity = new HttpEntity<>(params, headers);
        return executeStringRequest(url, method, entity);
    }

    private boolean isCasSamlValidationRequest(String url) {
        return StrUtil.containsIgnoreCase(url, "samlValidate");
    }

    private String executeCasSamlValidationRequest(
            String provider, String url, String ticket, String state) {
        String targetUrl = appendQueryParam(url, "TARGET", buildOAuth2CallbackUrl(provider, state));
        targetUrl = appendQueryParam(targetUrl, IdpConstants.TICKET, ticket);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        HttpEntity<String> entity =
                new HttpEntity<>(buildCasSamlValidationEnvelope(ticket), headers);
        return executeStringRequest(targetUrl, HttpMethod.POST, entity);
    }

    private String buildCasSamlValidationEnvelope(String ticket) {
        String requestId = "_" + UUID.randomUUID().toString().replace("-", "");
        String issueInstant = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String escapedTicket = escapeXml(ticket);
        return """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
          <SOAP-ENV:Header/>
          <SOAP-ENV:Body>
            <samlp:Request xmlns:samlp="urn:oasis:names:tc:SAML:1.0:protocol" MajorVersion="1" MinorVersion="1" RequestID="%s" IssueInstant="%s">
              <samlp:AssertionArtifact>%s</samlp:AssertionArtifact>
            </samlp:Request>
          </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
        """
                .formatted(requestId, issueInstant, escapedTicket);
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String executeStringRequest(String url, HttpMethod method, HttpEntity<?> entity) {
        return executeStringRequest(URI.create(url), method, entity);
    }

    private String executeStringRequest(URI uri, HttpMethod method, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(uri, method, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || StrUtil.isBlank(response.getBody())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "OAuth2 upstream response is invalid");
            }
            return response.getBody();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("OAuth2 upstream request failed, url={}", uri, ex);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "OAuth2 upstream request failed");
        }
    }

    private Map<String, Object> fetchUserInfo(JwtBearerConfig jwtBearerConfig, String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            ResponseEntity<String> response =
                    restTemplate.exchange(
                            jwtBearerConfig.getUserInfoEndpoint(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || StrUtil.isBlank(response.getBody())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "OAuth2 user info response is invalid");
            }
            return new HashMap<>(JSONUtil.parseObj(response.getBody()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "Failed to fetch OAuth2 user info, endpoint={}",
                    jwtBearerConfig.getUserInfoEndpoint(),
                    ex);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "OAuth2 user info request failed");
        }
    }

    private String resolveProvider(String requestedProvider, String stateProvider) {
        if (StrUtil.isBlank(stateProvider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing OAuth2 provider");
        }
        if (StrUtil.isNotBlank(requestedProvider)
                && !StrUtil.equals(requestedProvider, stateProvider)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "OAuth2 provider does not match state");
        }
        return stateProvider;
    }

    private IdpState parseState(String encodedState) {
        IdpState state = idpStateCodec.decode(encodedState);
        if (state.getTimestamp() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid state");
        }
        if (System.currentTimeMillis() - state.getTimestamp() > IdpConstants.IDP_STATE_TTL_MILLIS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Request has expired");
        }
        return state;
    }

    private OAuth2Config findEnabledConfig(String provider) {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getOauth2Configs() != null)
                .flatMap(
                        portal ->
                                portal.getPortalSettingConfig().getOauth2Configs().stream()
                                        .filter(
                                                config ->
                                                        StrUtil.equals(
                                                                        provider,
                                                                        config.getProvider())
                                                                && config.isEnabled())
                                        .findFirst())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.OAUTH2_CONFIG, provider));
    }

    private OAuth2Config findJwtBearerConfig(String provider) {
        OAuth2Config config = findEnabledConfig(provider);
        if (config.getGrantType() != GrantType.JWT_BEARER) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "OAuth2 config {} does not support JWT bearer login",
                            config.getProvider()));
        }
        return config;
    }

    private OAuth2Config findTrustedHeaderConfig(String provider) {
        OAuth2Config config = findEnabledConfig(provider);
        if (config.getGrantType() != GrantType.TRUSTED_HEADER) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "OAuth2 config {} does not support trusted header login",
                            config.getProvider()));
        }
        return config;
    }

    private JwtBearerConfig requireJwtDirectConfig(OAuth2Config config) {
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        if (jwtBearerConfig == null
                || StrUtil.isBlank(jwtBearerConfig.getAuthorizationEndpoint())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "OAuth2 config {} does not support browser login",
                            config.getProvider()));
        }
        return jwtBearerConfig;
    }

    private boolean hasJwkValidation(JwtBearerConfig jwtBearerConfig) {
        return StrUtil.isNotBlank(jwtBearerConfig.getJwkSetUri());
    }

    private boolean hasJwtVerificationConfig(JwtBearerConfig jwtBearerConfig) {
        return hasJwkValidation(jwtBearerConfig)
                || CollUtil.isNotEmpty(jwtBearerConfig.getPublicKeys());
    }

    private JwtBearerConfig requireJwtVerificationConfig(OAuth2Config config) {
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        if (jwtBearerConfig == null || !hasJwtVerificationConfig(jwtBearerConfig)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "OAuth2 config {} does not support direct JWT login",
                            config.getProvider()));
        }
        return jwtBearerConfig;
    }

    private String buildOAuth2CallbackUrl(String provider, String state) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(
                        portalFrontendUrlResolver.buildCallbackUrl("/oauth2/callback"));
        if (StrUtil.isNotBlank(provider)) {
            builder.queryParam(IdpConstants.PROVIDER, provider);
        }
        if (StrUtil.isNotBlank(state)) {
            builder.queryParam(IdpConstants.STATE, state);
        }
        return builder.build().toUriString();
    }

    private String appendQueryParam(String url, String key, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    private AuthResult authenticateStandard(JWT unverified, String jwtToken) {
        String portalId = contextHolder.getPortal();
        if (StrUtil.isBlank(portalId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing portal context");
        }

        String issuer = Convert.toStr(unverified.getPayload(JwtConstants.PAYLOAD_ISS), null);
        if (StrUtil.isBlank(issuer)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT payload missing field iss");
        }

        List<String> audiences =
                normalizeAudiences(unverified.getPayload(JwtConstants.PAYLOAD_AUD));

        PortalResult portal = portalService.getPortal(portalId);
        List<OAuth2Config> oauth2Configs =
                Optional.ofNullable(portal.getPortalSettingConfig())
                        .map(PortalSettingConfig::getOauth2Configs)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                portalId));

        OAuth2Config matched =
                oauth2Configs.stream()
                        .filter(OAuth2Config::isEnabled)
                        .filter(config -> config.getGrantType() == GrantType.JWT_BEARER)
                        .filter(config -> config.getJwtBearerConfig() != null)
                        .filter(
                                config ->
                                        StrUtil.isNotBlank(
                                                config.getJwtBearerConfig().getJwkSetUri()))
                        .filter(config -> issuer.equals(config.getJwtBearerConfig().getIssuer()))
                        .filter(config -> audienceMatches(config.getJwtBearerConfig(), audiences))
                        .reduce(
                                null,
                                (acc, cur) -> {
                                    if (acc != null) {
                                        throw new BusinessException(
                                                ErrorCode.CONFLICT,
                                                "Multiple OAuth2 configs match this JWT");
                                    }
                                    return cur;
                                });

        if (matched == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.OAUTH2_CONFIG, "JWT issuer/audience");
        }

        Jwt verified = jwtBearerTokenVerifier.verify(jwtToken, matched.getJwtBearerConfig());

        String developerId = createOrGetDeveloper(verified, matched);
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        log.info(
                "JWT Bearer authentication successful, provider: {}, developer: {}",
                matched.getProvider(),
                developerId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    private boolean audienceMatches(JwtBearerConfig config, List<String> audiences) {
        if (CollUtil.isEmpty(config.getAudiences())) {
            return true;
        }
        if (CollUtil.isEmpty(audiences)) {
            return false;
        }
        return config.getAudiences().stream().anyMatch(audiences::contains);
    }

    private List<String> normalizeAudiences(Object audObj) {
        if (audObj == null) {
            return Collections.emptyList();
        }
        if (audObj instanceof String str) {
            return StrUtil.isBlank(str) ? Collections.emptyList() : List.of(str);
        }
        if (audObj instanceof Iterable<?> iterable) {
            List<String> audiences = new ArrayList<>();
            for (Object item : iterable) {
                String value = Convert.toStr(item, null);
                if (StrUtil.isNotBlank(value)) {
                    audiences.add(value);
                }
            }
            return audiences;
        }
        return Collections.emptyList();
    }

    private boolean verifySignature(JWT jwt, PublicKeyConfig keyConfig) {
        // Load public key
        PublicKey publicKey = idpService.loadPublicKey(keyConfig.getFormat(), keyConfig.getValue());

        // Verify JWT
        JWTSigner signer = createJWTSigner(keyConfig.getAlgorithm(), publicKey);
        return jwt.setSigner(signer).verify();
    }

    private JWTSigner createJWTSigner(String algorithm, PublicKey publicKey) {
        JwtAlgorithm alg = EnumUtil.fromString(JwtAlgorithm.class, algorithm.toUpperCase());

        switch (alg) {
            case RS256:
                return JWTSignerUtil.rs256(publicKey);
            case RS384:
                return JWTSignerUtil.rs384(publicKey);
            case RS512:
                return JWTSignerUtil.rs512(publicKey);
            case ES256:
                return JWTSignerUtil.es256(publicKey);
            case ES384:
                return JWTSignerUtil.es384(publicKey);
            case ES512:
                return JWTSignerUtil.es512(publicKey);
            default:
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "Unsupported JWT signature algorithm");
        }
    }

    private void validateJwtClaims(JWT jwt) {
        // Expiration
        Object expObj = jwt.getPayload(JwtConstants.PAYLOAD_EXP);
        Long exp = Convert.toLong(expObj);
        // Issued at
        Object iatObj = jwt.getPayload(JwtConstants.PAYLOAD_IAT);
        Long iat = Convert.toLong(iatObj);

        if (iat == null || exp == null || iat > exp) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Invalid exp or iat in JWT payload");
        }

        long currentTime = System.currentTimeMillis() / 1000;
        if (exp <= currentTime) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT has expired");
        }
    }

    private String createOrGetDeveloper(JWT jwt, OAuth2Config config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        // userId & userName
        String userIdField =
                StrUtil.isBlank(identityMapping.getUserIdField())
                        ? JwtConstants.PAYLOAD_USER_ID
                        : identityMapping.getUserIdField();
        String userNameField =
                StrUtil.isBlank(identityMapping.getUserNameField())
                        ? JwtConstants.PAYLOAD_USER_NAME
                        : identityMapping.getUserNameField();
        String avatarUrlField =
                StrUtil.isBlank(identityMapping.getAvatarUrlField())
                        ? IdpConstants.AVATAR_URL
                        : identityMapping.getAvatarUrlField();
        Object userIdObj = jwt.getPayload(userIdField);
        Object userNameObj = jwt.getPayload(userNameField);
        String avatarUrl = Convert.toStr(jwt.getPayload(avatarUrlField));

        String userId = Convert.toStr(userIdObj);
        String userName = Convert.toStr(userNameObj);
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(userName)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing user ID or user name in JWT payload");
        }

        // Reuse existing developer or create new
        DeveloperResult existing =
                developerService.getExternalDeveloper(config.getProvider(), userId);
        if (existing != null) {
            developerService.updateExternalDeveloperAvatar(config.getProvider(), userId, avatarUrl);
            return existing.getDeveloperId();
        }

        CreateExternalDeveloperParam param =
                CreateExternalDeveloperParam.builder()
                        .provider(config.getProvider())
                        .subject(userId)
                        .displayName(userName)
                        .avatarUrl(avatarUrl)
                        .authType(DeveloperAuthType.OAUTH2)
                        .build();

        return developerService.createExternalDeveloper(param).getDeveloperId();
    }

    private String createOrGetDeveloper(Jwt jwt, OAuth2Config config) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField =
                StrUtil.isBlank(identityMapping.getUserIdField())
                        ? JwtConstants.PAYLOAD_SUB
                        : identityMapping.getUserIdField();
        String userNameField =
                StrUtil.isBlank(identityMapping.getUserNameField())
                        ? JwtConstants.PAYLOAD_USER_NAME
                        : identityMapping.getUserNameField();
        String emailField =
                StrUtil.isBlank(identityMapping.getEmailField())
                        ? JwtConstants.PAYLOAD_EMAIL
                        : identityMapping.getEmailField();

        String userId = jwt.getClaimAsString(userIdField);
        String userName = jwt.getClaimAsString(userNameField);
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing user ID in JWT claims");
        }
        if (StrUtil.isBlank(userName)) {
            userName = userId;
        }

        String email = jwt.getClaimAsString(emailField);

        String finalUserName = userName;
        return Optional.ofNullable(
                        developerService.getExternalDeveloper(config.getProvider(), userId))
                .map(DeveloperResult::getDeveloperId)
                .orElseGet(
                        () -> {
                            CreateExternalDeveloperParam param =
                                    CreateExternalDeveloperParam.builder()
                                            .provider(config.getProvider())
                                            .subject(userId)
                                            .displayName(finalUserName)
                                            .email(email)
                                            .authType(DeveloperAuthType.OAUTH2)
                                            .build();

                            return developerService.createExternalDeveloper(param).getDeveloperId();
                        });
    }

    private String createOrGetDeveloper(Map<String, Object> claims, OAuth2Config config) {
        return createOrGetDeveloper(claims, config, JSONUtil.toJsonStr(claims));
    }

    private String createOrGetDeveloper(
            Map<String, Object> claims, OAuth2Config config, String rawInfoJson) {
        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userIdField =
                StrUtil.isBlank(identityMapping.getUserIdField())
                        ? JwtConstants.PAYLOAD_SUB
                        : identityMapping.getUserIdField();
        String userNameField =
                StrUtil.isBlank(identityMapping.getUserNameField())
                        ? JwtConstants.PAYLOAD_USER_NAME
                        : identityMapping.getUserNameField();
        String emailField =
                StrUtil.isBlank(identityMapping.getEmailField())
                        ? JwtConstants.PAYLOAD_EMAIL
                        : identityMapping.getEmailField();

        String userId = Convert.toStr(claims.get(userIdField), null);
        String userName = Convert.toStr(claims.get(userNameField), null);
        String email = Convert.toStr(claims.get(emailField), null);
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing user ID in OAuth2 identity claims");
        }
        if (StrUtil.isBlank(userName)) {
            userName = userId;
        }

        DeveloperResult existing =
                developerService.getExternalDeveloper(config.getProvider(), userId);
        if (existing != null) {
            developerService.updateExternalDeveloperProfile(
                    config.getProvider(), userId, userName, email, rawInfoJson);
            return existing.getDeveloperId();
        }

        return developerService
                .createExternalDeveloper(
                        CreateExternalDeveloperParam.builder()
                                .provider(config.getProvider())
                                .subject(userId)
                                .displayName(userName)
                                .email(email)
                                .authType(DeveloperAuthType.OAUTH2)
                                .rawInfoJson(rawInfoJson)
                                .build())
                .getDeveloperId();
    }
}
