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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.params.idp.OAuth2BrowserLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2DirectLoginParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.CasJsonTicketValidationParser;
import com.alibaba.himarket.service.idp.CasSamlTicketValidationParser;
import com.alibaba.himarket.service.idp.CasTicketValidationParser;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.JwtBearerTokenVerifier;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.service.idp.TrustedHeaderIdentityResolver;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtDirectAcquireMode;
import com.alibaba.himarket.support.enums.JwtDirectIdentitySource;
import com.alibaba.himarket.support.enums.JwtDirectTokenSource;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.TrustedHeaderConfig;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2ServiceImplJwtBearerTest {

    @Mock private PortalService portalService;

    @Mock private DeveloperService developerService;

    @Mock private IdpService idpService;

    @Mock private ContextHolder contextHolder;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", null);
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 0L);
    }

    @Test
    void authenticateShouldAcceptStandardJwtBearerAndIssueDeveloperToken() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startJwksServer(rsaKey);
        String jwkSetUri = issuer + "/jwks";

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(issuer, jwkSetUri));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();

        String jwtToken = createJwt(rsaKey, issuer, "himarket-api");
        AuthResult result = service.authenticate(GrantType.JWT_BEARER.getType(), jwtToken);

        assertNotNull(result.getAccessToken());
        assertEquals(3600L, result.getExpiresIn());

        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        CreateExternalDeveloperParam param = captor.getValue();
        assertEquals("cas-jwt", param.getProvider());
        assertEquals("alice", param.getSubject());
        assertEquals("Alice", param.getDisplayName());
        assertEquals("alice@example.com", param.getEmail());
    }

    @Test
    void authenticateShouldRejectAudienceMismatch() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startJwksServer(rsaKey);
        String jwkSetUri = issuer + "/jwks";

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(issuer, jwkSetUri));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        OAuth2ServiceImpl service = createService();

        String jwtToken = createJwt(rsaKey, issuer, "other-aud");
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.authenticate(GrantType.JWT_BEARER.getType(), jwtToken));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void authenticateDirectShouldLoadClaimsFromProviderConfig() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startJwksServer(rsaKey);
        String jwkSetUri = issuer + "/jwks";

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(issuer, jwkSetUri));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        OAuth2DirectLoginParam param = new OAuth2DirectLoginParam();
        param.setProvider("cas-jwt");
        param.setJwt(createJwt(rsaKey, issuer, "himarket-api"));

        AuthResult result = service.authenticateDirect(param);

        assertNotNull(result.getAccessToken());
        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void authenticateDirectShouldLoadUserInfoWhenConfigured() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startOAuth2DirectServer(rsaKey);

        OAuth2Config provider = new OAuth2Config();
        provider.setProvider("cas-jwt-direct-userinfo");
        provider.setName("CAS JWT Direct UserInfo");
        provider.setEnabled(true);
        provider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setIssuer(issuer);
        jwtBearerConfig.setJwkSetUri(issuer + "/jwks");
        jwtBearerConfig.setAudiences(List.of("himarket-api"));
        jwtBearerConfig.setIdentitySource(JwtDirectIdentitySource.USERINFO);
        jwtBearerConfig.setUserInfoEndpoint(issuer + "/userinfo");
        provider.setJwtBearerConfig(jwtBearerConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(provider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-direct-userinfo", "alice"))
                .thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        OAuth2DirectLoginParam param = new OAuth2DirectLoginParam();
        param.setProvider("cas-jwt-direct-userinfo");
        param.setJwt(createJwt(rsaKey, issuer, "himarket-api"));

        AuthResult result = service.authenticateDirect(param);

        assertNotNull(result.getAccessToken());
        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void authenticateTrustedHeaderShouldCreateDeveloperAndPersistRawIdentity() {
        OAuth2Config provider = new OAuth2Config();
        provider.setProvider("trusted-header");
        provider.setName("Trusted Header");
        provider.setEnabled(true);
        provider.setGrantType(GrantType.TRUSTED_HEADER);

        TrustedHeaderConfig trustedHeaderConfig = new TrustedHeaderConfig();
        trustedHeaderConfig.setEnabled(true);
        trustedHeaderConfig.setTrustedProxyCidrs(List.of("127.0.0.1/32"));
        trustedHeaderConfig.setUserIdHeader("X-Auth-User");
        trustedHeaderConfig.setUserNameHeader("X-Auth-Name");
        trustedHeaderConfig.setEmailHeader("X-Auth-Email");
        trustedHeaderConfig.setGroupsHeader("X-Auth-Groups");
        trustedHeaderConfig.setRolesHeader("X-Auth-Roles");
        provider.setTrustedHeaderConfig(trustedHeaderConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(provider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("trusted-header", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Auth-User", "alice");
        request.addHeader("X-Auth-Name", "Alice Header");
        request.addHeader("X-Auth-Email", "alice@example.com");
        request.addHeader("X-Auth-Groups", "platform,ops");
        request.addHeader("X-Auth-Roles", "developer,owner");

        OAuth2ServiceImpl service = createService();
        AuthResult result = service.authenticateTrustedHeader("trusted-header", request);

        assertNotNull(result.getAccessToken());
        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice Header", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
        assertNotNull(captor.getValue().getRawInfoJson());
        Assertions.assertTrue(captor.getValue().getRawInfoJson().contains("platform"));
        Assertions.assertTrue(captor.getValue().getRawInfoJson().contains("owner"));
    }

    @Test
    void authenticateTrustedHeaderShouldRejectUntrustedProxy() {
        OAuth2Config provider = new OAuth2Config();
        provider.setProvider("trusted-header");
        provider.setName("Trusted Header");
        provider.setEnabled(true);
        provider.setGrantType(GrantType.TRUSTED_HEADER);

        TrustedHeaderConfig trustedHeaderConfig = new TrustedHeaderConfig();
        trustedHeaderConfig.setEnabled(true);
        trustedHeaderConfig.setTrustedProxyCidrs(List.of("127.0.0.1/32"));
        trustedHeaderConfig.setUserIdHeader("X-Auth-User");
        provider.setTrustedHeaderConfig(trustedHeaderConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(provider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Auth-User", "alice");

        OAuth2ServiceImpl service = createService();

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.authenticateTrustedHeader("trusted-header", request));

        assertEquals("UNAUTHORIZED", exception.getCode());
    }

    @Test
    void getAvailableProvidersShouldExposeBrowserAndDirectCapabilities() {
        PortalResult portalResult = new PortalResult();

        JwtBearerConfig interactiveConfig = new JwtBearerConfig();
        interactiveConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        interactiveConfig.setIssuer("https://cas.example.com/oauth2");
        interactiveConfig.setJwkSetUri("https://cas.example.com/oauth2/jwks");
        interactiveConfig.setAudiences(List.of("himarket-api"));

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-direct");
        interactiveProvider.setName("CAS JWT Direct");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);
        interactiveProvider.setJwtBearerConfig(interactiveConfig);

        OAuth2Config apiOnlyProvider = new OAuth2Config();
        apiOnlyProvider.setProvider("cas-jwt-api");
        apiOnlyProvider.setName("CAS JWT API");
        apiOnlyProvider.setEnabled(true);
        apiOnlyProvider.setGrantType(GrantType.JWT_BEARER);
        apiOnlyProvider.setJwtBearerConfig(new JwtBearerConfig());

        JwtBearerConfig bodySourceConfig = new JwtBearerConfig();
        bodySourceConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        bodySourceConfig.setIssuer("https://cas.example.com/oauth2");
        bodySourceConfig.setJwkSetUri("https://cas.example.com/oauth2/jwks");
        bodySourceConfig.setAudiences(List.of("himarket-api"));
        bodySourceConfig.setTokenSource(JwtDirectTokenSource.BODY);

        OAuth2Config bodySourceProvider = new OAuth2Config();
        bodySourceProvider.setProvider("cas-jwt-body");
        bodySourceProvider.setName("CAS JWT Body");
        bodySourceProvider.setEnabled(true);
        bodySourceProvider.setGrantType(GrantType.JWT_BEARER);
        bodySourceProvider.setJwtBearerConfig(bodySourceConfig);

        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setOauth2Configs(
                List.of(interactiveProvider, apiOnlyProvider, bodySourceProvider));
        portalResult.setPortalSettingConfig(settingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        OAuth2ServiceImpl service = createService();

        List<IdpResult> providers = service.getAvailableProviders();

        assertEquals(2, providers.size());
        IdpResult interactive =
                providers.stream()
                        .filter(provider -> "cas-jwt-direct".equals(provider.getProvider()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("OAUTH2", interactive.getType());
        Assertions.assertTrue(Boolean.TRUE.equals(interactive.getInteractiveBrowserLogin()));
        Assertions.assertTrue(Boolean.TRUE.equals(interactive.getDirectTokenLogin()));

        IdpResult directOnly =
                providers.stream()
                        .filter(provider -> "cas-jwt-body".equals(provider.getProvider()))
                        .findFirst()
                        .orElseThrow();
        Assertions.assertFalse(Boolean.TRUE.equals(directOnly.getInteractiveBrowserLogin()));
        Assertions.assertTrue(Boolean.TRUE.equals(directOnly.getDirectTokenLogin()));
    }

    @Test
    void buildAuthorizationResultShouldUseFrontendCallbackAsServiceTarget() {
        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-direct");
        interactiveProvider.setName("CAS JWT Direct");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setIssuer("https://cas.example.com/oauth2");
        jwtBearerConfig.setJwkSetUri("https://cas.example.com/oauth2/jwks");
        jwtBearerConfig.setAudiences(List.of("himarket-api"));
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        OAuth2ServiceImpl service = createService();

        IdpAuthorizeResult result = service.buildAuthorizationResult("cas-jwt-direct", "/api/v1");

        String expectedServicePrefix =
                "service="
                        + UriUtils.encode(
                                "https://portal.example.com/oauth2/callback?provider=cas-jwt-direct"
                                        + "&state=",
                                StandardCharsets.UTF_8);
        Assertions.assertTrue(result.getRedirectUrl().contains(expectedServicePrefix));
        int serviceIndex = result.getRedirectUrl().indexOf("service=");
        Assertions.assertTrue(serviceIndex >= 0);
        String serviceTarget =
                result.getRedirectUrl().substring(serviceIndex + "service=".length());
        String decodedServiceTarget = UriUtils.decode(serviceTarget, StandardCharsets.UTF_8);
        Assertions.assertTrue(
                decodedServiceTarget.startsWith("https://portal.example.com/oauth2/callback"));
        Assertions.assertTrue(decodedServiceTarget.contains("provider=cas-jwt-direct"));
        Assertions.assertTrue(decodedServiceTarget.contains("state="));
        Assertions.assertTrue(result.getRedirectUrl().contains("state="));
        assertNotNull(result.getState());
    }

    @Test
    void completeBrowserLoginShouldAuthenticateDirectJwt() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startJwksServer(rsaKey);
        String jwkSetUri = issuer + "/jwks";

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-direct");
        interactiveProvider.setName("CAS JWT Direct");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setIssuer(issuer);
        jwtBearerConfig.setJwkSetUri(jwkSetUri);
        jwtBearerConfig.setAudiences(List.of("himarket-api"));
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-direct", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-direct", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-direct",
                                authorizeResult.getState(),
                                createJwt(rsaKey, issuer, "himarket-api"),
                                null));

        assertNotNull(result.getAccessToken());
        assertEquals(3600L, result.getExpiresIn());
    }

    @Test
    void completeBrowserLoginShouldExchangeTicketAndLoadUserInfo() throws Exception {
        RSAKey rsaKey = generateRsaKey("key-1");
        String issuer = startOAuth2DirectServer(rsaKey);

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-ticket");
        interactiveProvider.setName("CAS JWT Ticket");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setIssuer(issuer);
        jwtBearerConfig.setJwkSetUri(issuer + "/jwks");
        jwtBearerConfig.setAudiences(List.of("himarket-api"));
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_EXCHANGE);
        jwtBearerConfig.setTicketExchangeUrl(issuer + "/exchange");
        jwtBearerConfig.setIdentitySource(JwtDirectIdentitySource.USERINFO);
        jwtBearerConfig.setUserInfoEndpoint(issuer + "/userinfo");
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-ticket", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-ticket", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-ticket", authorizeResult.getState(), null, "ST-123"));

        assertNotNull(result.getAccessToken());

        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void completeBrowserLoginShouldValidateCasJsonTicketResponse() throws Exception {
        String issuer = startTicketValidationServer();

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-validate-json");
        interactiveProvider.setName("CAS JWT Validate JSON");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl(issuer + "/validate-json");
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-validate-json", "alice"))
                .thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-validate-json", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-validate-json",
                                authorizeResult.getState(),
                                null,
                                "ST-JSON-123"));

        assertNotNull(result.getAccessToken());

        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void completeBrowserLoginShouldValidateCasXmlTicketResponseWithIdentityMapping()
            throws Exception {
        String issuer = startTicketValidationServer();

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-validate-xml");
        interactiveProvider.setName("CAS JWT Validate XML");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl(issuer + "/validate-xml");
        jwtBearerConfig.setTicketExchangeMethod("GET");
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("uid");
        identityMapping.setUserNameField("displayName");
        identityMapping.setEmailField("mail");
        interactiveProvider.setIdentityMapping(identityMapping);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-validate-xml", "alice"))
                .thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-validate-xml", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-validate-xml",
                                authorizeResult.getState(),
                                null,
                                "ST-XML-123"));

        assertNotNull(result.getAccessToken());

        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void completeBrowserLoginShouldValidateCasSamlTicketResponseWithIdentityMapping()
            throws Exception {
        String issuer = startTicketValidationServer();

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-validate-saml");
        interactiveProvider.setName("CAS JWT Validate SAML");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl(issuer + "/samlValidate");
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("uid");
        identityMapping.setUserNameField("displayName");
        identityMapping.setEmailField("mail");
        interactiveProvider.setIdentityMapping(identityMapping);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-validate-saml", "alice"))
                .thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-validate-saml", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-validate-saml",
                                authorizeResult.getState(),
                                null,
                                "ST-SAML-123"));

        assertNotNull(result.getAccessToken());

        ArgumentCaptor<CreateExternalDeveloperParam> captor =
                ArgumentCaptor.forClass(CreateExternalDeveloperParam.class);
        verify(developerService).createExternalDeveloper(captor.capture());
        assertEquals("alice", captor.getValue().getSubject());
        assertEquals("Alice", captor.getValue().getDisplayName());
        assertEquals("alice@example.com", captor.getValue().getEmail());
    }

    @Test
    void completeBrowserLoginShouldEscapeCasSamlTicketInValidationEnvelope() throws Exception {
        String samlTicket = "ST-SAML-&<>'\"";
        String issuer = startTicketValidationServer(samlTicket);

        OAuth2Config interactiveProvider = new OAuth2Config();
        interactiveProvider.setProvider("cas-jwt-validate-saml-escaped");
        interactiveProvider.setName("CAS JWT Validate SAML Escaped");
        interactiveProvider.setEnabled(true);
        interactiveProvider.setGrantType(GrantType.JWT_BEARER);

        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl(issuer + "/samlValidate");
        interactiveProvider.setJwtBearerConfig(jwtBearerConfig);

        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("uid");
        identityMapping.setUserNameField("displayName");
        identityMapping.setEmailField("mail");
        interactiveProvider.setIdentityMapping(identityMapping);

        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig(List.of(interactiveProvider)));

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas-jwt-validate-saml-escaped", "alice"))
                .thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "oauth2-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        OAuth2ServiceImpl service = createService();
        IdpAuthorizeResult authorizeResult =
                service.buildAuthorizationResult("cas-jwt-validate-saml-escaped", "/api/v1");

        AuthResult result =
                service.completeBrowserLogin(
                        browserLoginParam(
                                "cas-jwt-validate-saml-escaped",
                                authorizeResult.getState(),
                                null,
                                samlTicket));

        assertNotNull(result.getAccessToken());
    }

    private PortalSettingConfig portalSettingConfig(String issuer, String jwkSetUri) {
        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setIssuer(issuer);
        jwtBearerConfig.setJwkSetUri(jwkSetUri);
        jwtBearerConfig.setAudiences(List.of("himarket-api"));

        OAuth2Config config = new OAuth2Config();
        config.setProvider("cas-jwt");
        config.setName("CAS JWT");
        config.setEnabled(true);
        config.setGrantType(GrantType.JWT_BEARER);
        config.setJwtBearerConfig(jwtBearerConfig);

        return portalSettingConfig(List.of(config));
    }

    private PortalSettingConfig portalSettingConfig(List<OAuth2Config> configs) {
        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setOauth2Configs(configs);
        settingConfig.setFrontendRedirectUrl("https://portal.example.com");
        return settingConfig;
    }

    private String startJwksServer(RSAKey rsaKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/issuer";
        String body = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        server.createContext("/issuer/jwks", new JsonHandler(body));
        server.start();
        return issuer;
    }

    private String startOAuth2DirectServer(RSAKey rsaKey) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/issuer";
        String jwt = createJwt(rsaKey, issuer, "himarket-api");
        server.createContext(
                "/issuer/jwks",
                new JsonHandler("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}"));
        server.createContext(
                "/issuer/exchange", new JsonHandler("{\"access_token\":\"" + jwt + "\"}"));
        server.createContext(
                "/issuer/userinfo",
                new JsonHandler(
                        "{\"sub\":\"alice\",\"name\":\"Alice\",\"email\":\"alice@example.com\"}"));
        server.start();
        return issuer;
    }

    private String startTicketValidationServer() throws IOException {
        return startTicketValidationServer("ST-SAML-123");
    }

    private String startTicketValidationServer(String expectedSamlTicket) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/issuer";
        server.createContext(
                "/issuer/validate-json",
                new TicketValidationHandler(
                        "application/json",
                        """
                        {
                          "serviceResponse": {
                            "authenticationSuccess": {
                              "user": "alice",
                              "attributes": {
                                "name": "Alice",
                                "email": "alice@example.com"
                              }
                            }
                          }
                        }
                        """));
        server.createContext(
                "/issuer/validate-xml",
                new TicketValidationHandler(
                        "application/xml",
                        """
                        <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                          <cas:authenticationSuccess>
                            <cas:user>alice</cas:user>
                            <cas:attributes>
                              <cas:uid>alice</cas:uid>
                              <cas:displayName>Alice</cas:displayName>
                              <cas:mail>alice@example.com</cas:mail>
                            </cas:attributes>
                          </cas:authenticationSuccess>
                        </cas:serviceResponse>
                        """));
        server.createContext(
                "/issuer/samlValidate",
                new SamlValidationHandler(
                        expectedSamlTicket,
                        """
                        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                          <SOAP-ENV:Body>
                            <Response xmlns="urn:oasis:names:tc:SAML:1.0:protocol">
                              <Status>
                                <StatusCode Value="samlp:Success"/>
                              </Status>
                              <Assertion xmlns="urn:oasis:names:tc:SAML:1.0:assertion">
                                <AuthenticationStatement>
                                  <Subject>
                                    <NameIdentifier>alice</NameIdentifier>
                                  </Subject>
                                </AuthenticationStatement>
                                <AttributeStatement>
                                  <Attribute AttributeName="uid">
                                    <AttributeValue>alice</AttributeValue>
                                  </Attribute>
                                  <Attribute AttributeName="displayName">
                                    <AttributeValue>Alice</AttributeValue>
                                  </Attribute>
                                  <Attribute AttributeName="mail">
                                    <AttributeValue>alice@example.com</AttributeValue>
                                  </Attribute>
                                </AttributeStatement>
                              </Assertion>
                            </Response>
                          </SOAP-ENV:Body>
                        </SOAP-ENV:Envelope>
                        """));
        server.start();
        return issuer;
    }

    private OAuth2BrowserLoginParam browserLoginParam(
            String provider, String state, String jwt, String ticket) {
        OAuth2BrowserLoginParam param = new OAuth2BrowserLoginParam();
        param.setProvider(provider);
        param.setState(state);
        param.setJwt(jwt);
        param.setTicket(ticket);
        return param;
    }

    private OAuth2ServiceImpl createService() {
        return new OAuth2ServiceImpl(
                portalService,
                developerService,
                idpService,
                contextHolder,
                new JwtBearerTokenVerifier(),
                new PortalFrontendUrlResolver(portalService, contextHolder),
                new IdpStateCodec(),
                new TrustedHeaderIdentityResolver(),
                new CasTicketValidationParser(),
                new CasJsonTicketValidationParser(),
                new CasSamlTicketValidationParser());
    }

    private RSAKey generateRsaKey(String keyId) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(keyId)
                .build();
    }

    private String createJwt(RSAKey rsaKey, String issuer, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claimsSet =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("alice")
                        .audience(audience)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .claim("name", "Alice")
                        .claim("email", "alice@example.com")
                        .build();

        SignedJWT signedJwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .type(JOSEObjectType.JWT)
                                .keyID(rsaKey.getKeyID())
                                .build(),
                        claimsSet);
        signedJwt.sign(new RSASSASigner(rsaKey.toPrivateKey()));
        return signedJwt.serialize();
    }

    private static class JsonHandler implements HttpHandler {

        private final byte[] body;

        private JsonHandler(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class StaticResponseHandler implements HttpHandler {

        private final byte[] body;

        private final String contentType;

        private StaticResponseHandler(String contentType, String body) {
            this.contentType = contentType;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class SamlValidationHandler implements HttpHandler {

        private final byte[] body;

        private final String expectedTicket;

        private SamlValidationHandler(String expectedTicket, String body) {
            this.expectedTicket = expectedTicket;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String requestBody =
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean validRequest =
                    "POST".equalsIgnoreCase(exchange.getRequestMethod())
                            && hasEncodedCallbackWithState(query, "TARGET")
                            && hasQueryParamValue(query, "ticket", expectedTicket)
                            && requestBody.contains(
                                    "<samlp:AssertionArtifact>"
                                            + escapeXml(expectedTicket)
                                            + "</samlp:AssertionArtifact>");
            if (!validRequest) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class TicketValidationHandler implements HttpHandler {

        private final byte[] body;

        private final String contentType;

        private TicketValidationHandler(String contentType, String body) {
            this.contentType = contentType;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            String requestData = exchange.getRequestURI().getRawQuery();
            if ("POST".equalsIgnoreCase(requestMethod)) {
                try (InputStream inputStream = exchange.getRequestBody()) {
                    requestData = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            boolean validRequest =
                    ("GET".equalsIgnoreCase(requestMethod)
                                    || "POST".equalsIgnoreCase(requestMethod))
                            && requestData != null
                            && requestData.contains("ticket=ST-")
                            && hasEncodedCallbackWithState(requestData, "service");
            if (!validRequest) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static boolean hasEncodedCallbackWithState(String query, String key) {
        return query != null
                && query.contains(key + "=")
                && (query.contains("%2Foauth2%2Fcallback%3Fprovider%3D")
                        || query.contains("%252Foauth2%252Fcallback%253Fprovider%253D"))
                && (query.contains("%26state%3D") || query.contains("%2526state%253D"));
    }

    private static boolean hasQueryParamValue(String query, String key, String expectedValue) {
        if (query == null) {
            return false;
        }
        String prefix = key + "=";
        for (String item : query.split("&")) {
            if (!item.startsWith(prefix)) {
                continue;
            }
            String decoded =
                    URLDecoder.decode(item.substring(prefix.length()), StandardCharsets.UTF_8);
            return expectedValue.equals(decoded);
        }
        return false;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
