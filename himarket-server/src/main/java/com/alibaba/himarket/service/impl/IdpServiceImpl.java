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
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.gateway.factory.HTTPClientFactory;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtDirectAcquireMode;
import com.alibaba.himarket.support.enums.JwtDirectIdentitySource;
import com.alibaba.himarket.support.enums.JwtDirectTokenSource;
import com.alibaba.himarket.support.enums.PublicKeyFormat;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.OidcConfig;
import com.alibaba.himarket.support.portal.PublicKeyConfig;
import com.alibaba.himarket.support.portal.TrustedHeaderConfig;
import com.alibaba.himarket.support.portal.cas.CasLoginConfig;
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyPolicyMode;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdpServiceImpl implements IdpService {

    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]*$");

    private final RestTemplate restTemplate = HTTPClientFactory.createRestTemplate();

    @Override
    public void validateOidcConfigs(List<OidcConfig> oidcConfigs) {
        if (CollUtil.isEmpty(oidcConfigs)) {
            return;
        }

        Set<String> providers =
                oidcConfigs.stream()
                        .map(OidcConfig::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oidcConfigs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in OIDC config");
        }

        oidcConfigs.forEach(
                config -> {
                    AuthCodeConfig authConfig =
                            Optional.ofNullable(config.getAuthCodeConfig())
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ErrorCode.INVALID_PARAMETER,
                                                            StrUtil.format(
                                                                    "OIDC config {} missing auth"
                                                                            + " code config",
                                                                    config.getProvider())));
                    if (StrUtil.isBlank(authConfig.getClientId())
                            || StrUtil.isBlank(authConfig.getClientSecret())
                            || StrUtil.isBlank(authConfig.getScopes())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "OIDC config {} missing required params: Client ID, Client"
                                                + " Secret or Scopes",
                                        config.getProvider()));
                    }

                    if (StrUtil.isNotBlank(authConfig.getIssuer())) {
                        discoverAndSetEndpoints(config.getProvider(), authConfig);
                    } else if (StrUtil.isBlank(authConfig.getAuthorizationEndpoint())
                            || StrUtil.isBlank(authConfig.getTokenEndpoint())
                            || StrUtil.isBlank(authConfig.getUserInfoEndpoint())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "OIDC config {} missing required endpoint config",
                                        config.getProvider()));
                    }
                });
    }

    @Override
    public void validateCasConfigs(List<CasConfig> casConfigs) {
        if (CollUtil.isEmpty(casConfigs)) {
            return;
        }

        Set<String> providers =
                casConfigs.stream()
                        .map(CasConfig::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != casConfigs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in CAS config");
        }

        casConfigs.forEach(
                config -> {
                    if (StrUtil.isBlank(config.getName())
                            || StrUtil.isBlank(config.getServerUrl())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "CAS config {} missing required params: name or server"
                                                + " URL",
                                        config.getProvider()));
                    }

                    validateUrl(config.getServerUrl(), "CAS server URL");
                    validateEndpoint(config.getLoginEndpoint(), "CAS login endpoint");
                    validateEndpoint(config.getValidateEndpoint(), "CAS validate endpoint");
                    validateEndpoint(config.getLogoutEndpoint(), "CAS logout endpoint");
                    validateCasLoginConfig(config.getProvider(), config.resolveLoginConfig());
                    validateCasProxyConfig(config.getProvider(), config.resolveProxyConfig());
                    validateCasServiceDefinition(
                            config.getProvider(), config.resolveServiceDefinition());
                    validateCasValidationConfig(
                            config.getProvider(), config.resolveValidationConfig());
                });
    }

    private void validateCasLoginConfig(String provider, CasLoginConfig loginConfig) {
        if (Boolean.TRUE.equals(loginConfig.getGateway())
                && Boolean.TRUE.equals(loginConfig.getRenew())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "CAS config {} cannot enable gateway and renew at the same time",
                            provider));
        }
    }

    private void validateCasValidationConfig(
            String provider, CasValidationConfig validationConfig) {
        if (validationConfig.getProtocolVersion() == CasProtocolVersion.SAML1
                && validationConfig.getResponseFormat() == CasValidationResponseFormat.JSON) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "CAS config {} cannot use JSON response with SAML1 validation",
                            provider));
        }
    }

    private void validateCasServiceDefinition(
            String provider, CasServiceDefinitionConfig serviceDefinitionConfig) {}

    private void validateCasProxyConfig(String provider, CasProxyConfig proxyConfig) {
        if (!Boolean.TRUE.equals(proxyConfig.getEnabled())) {
            return;
        }
        validateEndpoint(proxyConfig.getProxyEndpoint(), "CAS proxy endpoint");
        if (proxyConfig.getPolicyMode() == CasProxyPolicyMode.REST) {
            validateUrl(proxyConfig.getPolicyEndpoint(), "CAS proxy policy endpoint");
        }
        String callbackPath = proxyConfig.getCallbackPath();
        if (StrUtil.isNotBlank(callbackPath)
                && !StrUtil.startWithAnyIgnoreCase(callbackPath, "/", "http://", "https://")) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "CAS config {} proxy callback path must be absolute path or URL",
                            provider));
        }
    }

    @Override
    public void validateLdapConfigs(List<LdapConfig> ldapConfigs) {
        if (CollUtil.isEmpty(ldapConfigs)) {
            return;
        }

        Set<String> providers =
                ldapConfigs.stream()
                        .map(LdapConfig::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != ldapConfigs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in LDAP config");
        }

        ldapConfigs.forEach(
                config -> {
                    if (StrUtil.isBlank(config.getName())
                            || StrUtil.isBlank(config.getServerUrl())
                            || StrUtil.isBlank(config.getBaseDn())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "LDAP config {} missing required params: name, server URL"
                                                + " or base DN",
                                        config.getProvider()));
                    }

                    validateLdapUrl(config.getServerUrl(), "LDAP server URL");
                    if (StrUtil.isNotBlank(config.getBindDn())
                            && StrUtil.isBlank(config.getBindPassword())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "LDAP config {} missing required bind password",
                                        config.getProvider()));
                    }
                    if (StrUtil.isBlank(config.getBindDn())
                            && StrUtil.isNotBlank(config.getBindPassword())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "LDAP config {} missing required bind DN",
                                        config.getProvider()));
                    }

                    if (StrUtil.isBlank(config.getUserSearchFilter())
                            || !config.getUserSearchFilter().contains("{0}")) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "LDAP config {} has invalid user search filter",
                                        config.getProvider()));
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void discoverAndSetEndpoints(String provider, AuthCodeConfig config) {
        String discoveryUrl =
                config.getIssuer().replaceAll("/$", "") + "/.well-known/openid-configuration";
        try {
            Map<String, Object> discovery =
                    restTemplate.exchange(discoveryUrl, HttpMethod.GET, null, Map.class).getBody();

            String authEndpoint =
                    getRequiredEndpoint(discovery, IdpConstants.AUTHORIZATION_ENDPOINT);
            String tokenEndpoint = getRequiredEndpoint(discovery, IdpConstants.TOKEN_ENDPOINT);
            String userInfoEndpoint =
                    getRequiredEndpoint(discovery, IdpConstants.USERINFO_ENDPOINT);
            String jwkSetUri = getRequiredEndpoint(discovery, IdpConstants.JWKS_URI);

            config.setAuthorizationEndpoint(authEndpoint);
            config.setTokenEndpoint(tokenEndpoint);
            config.setUserInfoEndpoint(userInfoEndpoint);
            config.setJwkSetUri(jwkSetUri);
        } catch (Exception e) {
            log.error("Failed to discover OIDC endpoints from discovery URL: {}", discoveryUrl, e);
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OIDC config {} issuer is invalid or unreachable", provider));
        }
    }

    private String getRequiredEndpoint(Map<String, Object> discovery, String name) {
        return Optional.ofNullable(discovery.get(name))
                .map(Object::toString)
                .filter(StrUtil::isNotBlank)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_PARAMETER,
                                        "Missing endpoint in OIDC discovery config: " + name));
    }

    @Override
    public void validateOAuth2Configs(List<OAuth2Config> oauth2Configs) {
        if (CollUtil.isEmpty(oauth2Configs)) {
            return;
        }

        Set<String> providers =
                oauth2Configs.stream()
                        .map(OAuth2Config::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oauth2Configs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in OAuth2 config");
        }

        oauth2Configs.forEach(
                config -> {
                    if (GrantType.JWT_BEARER.equals(config.getGrantType())) {
                        validateJwtBearerConfig(config);
                        return;
                    }
                    if (GrantType.TRUSTED_HEADER.equals(config.getGrantType())) {
                        validateTrustedHeaderConfig(config);
                    }
                });
    }

    private void validateJwtBearerConfig(OAuth2Config config) {
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        if (jwtBearerConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} uses JWT bearer but missing JWT bearer config",
                            config.getProvider()));
        }

        boolean jwtVerificationRequired = requiresJwtVerification(jwtBearerConfig);
        boolean hasJwks = StrUtil.isNotBlank(jwtBearerConfig.getJwkSetUri());
        List<PublicKeyConfig> publicKeys = jwtBearerConfig.getPublicKeys();
        boolean hasPublicKeys = CollUtil.isNotEmpty(publicKeys);
        if (jwtVerificationRequired && !hasJwks && !hasPublicKeys) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} missing JWT verification config",
                            config.getProvider()));
        }

        if (jwtVerificationRequired && hasJwks) {
            if (StrUtil.isBlank(jwtBearerConfig.getIssuer())) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        StrUtil.format(
                                "OAuth2 config {} missing JWT issuer", config.getProvider()));
            }
            if (CollUtil.isEmpty(jwtBearerConfig.getAudiences())
                    || jwtBearerConfig.getAudiences().stream().anyMatch(StrUtil::isBlank)) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        StrUtil.format(
                                "OAuth2 config {} missing JWT audiences", config.getProvider()));
            }
            validateUrl(jwtBearerConfig.getIssuer(), "JWT issuer");
            validateUrl(jwtBearerConfig.getJwkSetUri(), "JWT JWK set URI");
        }

        if (jwtVerificationRequired && hasPublicKeys) {
            if (publicKeys.stream()
                            .map(
                                    key -> {
                                        loadPublicKey(key.getFormat(), key.getValue());
                                        return key.getKid();
                                    })
                            .collect(Collectors.toSet())
                            .size()
                    != publicKeys.size()) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        StrUtil.format(
                                "OAuth2 config {} has duplicate public key IDs",
                                config.getProvider()));
            }
        }

        validateJwtDirectConfig(config, jwtBearerConfig);
    }

    private void validateJwtDirectConfig(OAuth2Config config, JwtBearerConfig jwtBearerConfig) {
        if (!isJwtDirectFlowConfigured(jwtBearerConfig)) {
            return;
        }

        validateUrl(
                requireNonBlank(
                        config.getProvider(),
                        jwtBearerConfig.getAuthorizationEndpoint(),
                        "JWT direct authorization endpoint"),
                "JWT direct authorization endpoint");
        validateFieldName(
                config.getProvider(),
                jwtBearerConfig.resolveAuthorizationServiceField(),
                "JWT direct authorization service field");

        JwtDirectAcquireMode acquireMode = jwtBearerConfig.resolveAcquireMode();
        JwtDirectTokenSource tokenSource = jwtBearerConfig.resolveTokenSource();
        if (acquireMode == JwtDirectAcquireMode.TICKET_EXCHANGE
                || acquireMode == JwtDirectAcquireMode.TICKET_VALIDATE) {
            validateUrl(
                    requireNonBlank(
                            config.getProvider(),
                            jwtBearerConfig.getTicketExchangeUrl(),
                            "JWT direct ticket exchange URL"),
                    "JWT direct ticket exchange URL");
            validateHttpMethod(
                    config.getProvider(),
                    jwtBearerConfig.resolveTicketExchangeMethod(),
                    "JWT direct ticket exchange method");
            validateFieldName(
                    config.getProvider(),
                    jwtBearerConfig.resolveTicketExchangeTicketField(),
                    "JWT direct ticket field");
            validateFieldName(
                    config.getProvider(),
                    jwtBearerConfig.resolveTicketExchangeTokenField(),
                    "JWT direct token field");
            validateFieldName(
                    config.getProvider(),
                    jwtBearerConfig.resolveTicketExchangeServiceField(),
                    "JWT direct service field");
        }

        if (tokenSource == JwtDirectTokenSource.BODY) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} cannot use BODY token source for browser login",
                            config.getProvider()));
        }

        JwtDirectIdentitySource identitySource = jwtBearerConfig.resolveIdentitySource();
        if (acquireMode == JwtDirectAcquireMode.TICKET_VALIDATE
                && identitySource == JwtDirectIdentitySource.USERINFO) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} cannot use USERINFO with ticket validate mode",
                            config.getProvider()));
        }
        if (identitySource == JwtDirectIdentitySource.USERINFO) {
            validateUrl(
                    requireNonBlank(
                            config.getProvider(),
                            jwtBearerConfig.getUserInfoEndpoint(),
                            "JWT direct user info endpoint"),
                    "JWT direct user info endpoint");
        } else if (StrUtil.isNotBlank(jwtBearerConfig.getUserInfoEndpoint())) {
            validateUrl(jwtBearerConfig.getUserInfoEndpoint(), "JWT direct user info endpoint");
        }
    }

    private boolean isJwtDirectFlowConfigured(JwtBearerConfig jwtBearerConfig) {
        return StrUtil.isNotBlank(jwtBearerConfig.getAuthorizationEndpoint())
                || StrUtil.isNotBlank(jwtBearerConfig.getAuthorizationServiceField())
                || jwtBearerConfig.getAcquireMode() != null
                || StrUtil.isNotBlank(jwtBearerConfig.getTicketExchangeUrl())
                || StrUtil.isNotBlank(jwtBearerConfig.getTicketExchangeMethod())
                || StrUtil.isNotBlank(jwtBearerConfig.getTicketExchangeTicketField())
                || StrUtil.isNotBlank(jwtBearerConfig.getTicketExchangeTokenField())
                || StrUtil.isNotBlank(jwtBearerConfig.getTicketExchangeServiceField())
                || StrUtil.isNotBlank(jwtBearerConfig.getUserInfoEndpoint())
                || jwtBearerConfig.getIdentitySource() != null;
    }

    private boolean requiresJwtVerification(JwtBearerConfig jwtBearerConfig) {
        if (!isJwtDirectFlowConfigured(jwtBearerConfig)) {
            return true;
        }
        return jwtBearerConfig.resolveAcquireMode() != JwtDirectAcquireMode.TICKET_VALIDATE;
    }

    private void validateTrustedHeaderConfig(OAuth2Config config) {
        TrustedHeaderConfig trustedHeaderConfig = config.getTrustedHeaderConfig();
        if (trustedHeaderConfig == null || !trustedHeaderConfig.resolveEnabled()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} missing trusted header config",
                            config.getProvider()));
        }

        boolean hasTrustedProxyCidrs =
                CollUtil.isNotEmpty(trustedHeaderConfig.getTrustedProxyCidrs());
        boolean hasTrustedProxyHosts =
                CollUtil.isNotEmpty(trustedHeaderConfig.getTrustedProxyHosts());
        if (!hasTrustedProxyCidrs && !hasTrustedProxyHosts) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} missing trusted proxy allowlist",
                            config.getProvider()));
        }

        if (hasTrustedProxyCidrs) {
            trustedHeaderConfig
                    .getTrustedProxyCidrs()
                    .forEach(cidr -> validateTrustedProxyCidr(config.getProvider(), cidr));
        }
        if (hasTrustedProxyHosts
                && trustedHeaderConfig.getTrustedProxyHosts().stream().anyMatch(StrUtil::isBlank)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} has blank trusted proxy host", config.getProvider()));
        }

        validateFieldName(
                config.getProvider(),
                trustedHeaderConfig.resolveUserIdHeader(),
                "trusted header user ID header");
        validateFieldName(
                config.getProvider(),
                trustedHeaderConfig.resolveUserNameHeader(),
                "trusted header user name header");
        validateFieldName(
                config.getProvider(),
                trustedHeaderConfig.resolveEmailHeader(),
                "trusted header email header");
        validateFieldName(
                config.getProvider(),
                trustedHeaderConfig.resolveGroupsHeader(),
                "trusted header groups header");
        validateFieldName(
                config.getProvider(),
                trustedHeaderConfig.resolveRolesHeader(),
                "trusted header roles header");
        requireNonBlank(
                config.getProvider(),
                trustedHeaderConfig.resolveValueSeparator(),
                "trusted header value separator");
    }

    private void validateTrustedProxyCidr(String provider, String cidr) {
        if (StrUtil.isBlank(cidr)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2 config {} has blank trusted proxy CIDR", provider));
        }
        String[] cidrParts = cidr.split("/", 2);
        if (cidrParts.length != 2) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2 config {} has invalid trusted proxy CIDR", provider));
        }
        try {
            int prefixLength = Integer.parseInt(cidrParts[1]);
            int maxPrefixLength = resolveIpLiteralBitLength(cidrParts[0]);
            if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        StrUtil.format(
                                "OAuth2 config {} has invalid trusted proxy CIDR", provider));
            }
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2 config {} has invalid trusted proxy CIDR", provider));
        }
    }

    private int resolveIpLiteralBitLength(String addressLiteral) {
        if (Validator.isIpv4(addressLiteral)) {
            return 32;
        }
        if (Validator.isIpv6(addressLiteral)) {
            return 128;
        }
        throw new NumberFormatException("Invalid IP literal");
    }

    private String requireNonBlank(String provider, String value, String label) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2 config {} missing {}", provider, label));
        }
        return value;
    }

    private void validateFieldName(String provider, String value, String label) {
        if (StrUtil.isBlank(value) || !FIELD_NAME_PATTERN.matcher(value).matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2 config {} has invalid {}", provider, label));
        }
    }

    private void validateHttpMethod(String provider, String value, String label) {
        if (!HttpMethod.POST.matches(value) && !HttpMethod.GET.matches(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} has invalid {}: only GET/POST are supported",
                            provider,
                            label));
        }
    }

    @Override
    public PublicKey loadPublicKey(PublicKeyFormat format, String publicKey) {
        switch (format) {
            case PEM:
                return loadPublicKeyFromPem(publicKey);
            case JWK:
                return loadPublicKeyFromJwk(publicKey);
            default:
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "Unsupported public key format");
        }
    }

    private PublicKey loadPublicKeyFromPem(String pemContent) {
        String publicKeyPEM =
                pemContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                        .replace("-----END RSA PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");

        if (StrUtil.isBlank(publicKeyPEM)) {
            throw new IllegalArgumentException("PEM content is empty");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to parse PEM public key", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to parse PEM public key: " + e.getMessage());
        }
    }

    private PublicKey loadPublicKeyFromJwk(String jwkContent) {
        JSONObject jwk = JSONUtil.parseObj(jwkContent);
        String kty = getRequiredField(jwk, "kty");
        if (!"RSA".equals(kty)) {
            throw new IllegalArgumentException("Only RSA type JWK is supported");
        }
        return loadRSAPublicKeyFromJwk(jwk);
    }

    private PublicKey loadRSAPublicKeyFromJwk(JSONObject jwk) {
        String nStr = getRequiredField(jwk, "n");
        String eStr = getRequiredField(jwk, "e");

        try {
            byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
            byte[] eBytes = Base64.getUrlDecoder().decode(eStr);
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to parse JWK RSA parameters", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to parse JWK RSA parameters: " + e.getMessage());
        }
    }

    private void validateUrl(String url, String label) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Unsupported scheme");
            }
            if (StrUtil.isBlank(uri.getHost())) {
                throw new IllegalArgumentException("Missing host");
            }
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, label + " must be an absolute http/https URL");
        }
    }

    private void validateLdapUrl(String url, String label) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"ldap".equalsIgnoreCase(scheme) && !"ldaps".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Unsupported scheme");
            }
            if (StrUtil.isBlank(uri.getHost())) {
                throw new IllegalArgumentException("Missing host");
            }
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, label + " must be an absolute ldap/ldaps URL");
        }
    }

    private void validateEndpoint(String endpoint, String label) {
        if (StrUtil.isBlank(endpoint) || endpoint.startsWith("/")) {
            return;
        }
        validateUrl(endpoint, label);
    }

    private String getRequiredField(JSONObject jwk, String fieldName) {
        String value = jwk.getStr(fieldName);
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing field in JWK: " + fieldName);
        }
        return value;
    }
}
