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
import static org.mockito.Mockito.when;

import com.alibaba.himarket.config.AuthSessionConfig;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.idp.CasAuthorizeOptions;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.CasJsonTicketValidationParser;
import com.alibaba.himarket.service.idp.CasLogoutRequestParser;
import com.alibaba.himarket.service.idp.CasProxyTicketClient;
import com.alibaba.himarket.service.idp.CasProxyTicketParser;
import com.alibaba.himarket.service.idp.CasSamlTicketValidationParser;
import com.alibaba.himarket.service.idp.CasTicketValidationParser;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.service.idp.session.MemoryAuthSessionStore;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.cas.CasLoginConfig;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceLogoutType;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class CasServiceImplTest {

    @Mock private PortalService portalService;

    @Mock private DeveloperService developerService;

    @Mock private ContextHolder contextHolder;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("jwt.secret");
        System.clearProperty("jwt.expiration");
    }

    @Test
    void handleCallbackShouldValidateTicketAndCreateDeveloper() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        server.createContext(
                "/cas/p3/serviceValidate",
                new ValidateHandler(expectedServiceHolder, "alice", "alice@example.com"));
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl(serverUrl);
        casConfig.setLoginEndpoint(serverUrl + "/login");
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        casConfig.setIdentityMapping(defaultIdentityMapping());

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        System.setProperty("jwt.secret", "cas-test-secret");
        System.setProperty("jwt.expiration", "3600000");

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());
        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdpAuthorizeResult authorizeResult =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request);
        URI authUri = URI.create(authorizeResult.getRedirectUrl());
        assertEquals(
                serverUrl + "/login",
                authUri.getScheme() + "://" + authUri.getAuthority() + authUri.getPath());
        String serviceUrl = splitQueryValue(authUri.getRawQuery(), "service");
        assertEquals(
                "https://portal.example.com/api/v1/developers/cas/callback",
                URI.create(serviceUrl).getScheme()
                        + "://"
                        + URI.create(serviceUrl).getHost()
                        + URI.create(serviceUrl).getPath());
        assertEquals(
                "cas", splitQueryValue(URI.create(serviceUrl).getQuery(), IdpConstants.PROVIDER));
        String state = authorizeResult.getState();
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, state));
        expectedServiceHolder[0] = serviceUrl;

        String redirectUrl = casService.handleCallback("ST-1", state, request, response);
        String code = splitQueryValue(URI.create(redirectUrl).getQuery(), IdpConstants.CODE);
        AuthResult authResult = casService.exchangeCode(code);

        assertNotNull(authResult);
        assertNotNull(authResult.getAccessToken());
        assertEquals("Bearer", authResult.getTokenType());
    }

    @Test
    void handleCallbackShouldExtractAttributesUsingCustomMapping() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        // 模拟 CAS 返回非标准的属性名：corporate_uid 和 corporate_mail
        server.createContext(
                "/cas/p3/serviceValidate",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        byte[] body =
                                ("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                                     + "<cas:authenticationSuccess><cas:user>alice</cas:user>"
                                     + "<cas:attributes>"
                                     + "<cas:corporate_uid>alice-corp-id</cas:corporate_uid>"
                                     + "<cas:corporate_mail>alice@corp.com</cas:corporate_mail>"
                                     + "</cas:attributes></cas:authenticationSuccess>"
                                     + "</cas:serviceResponse>")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/xml");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                    }
                });
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setServerUrl(serverUrl);
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        // 配置自定义映射
        IdentityMapping mapping = new IdentityMapping();
        mapping.setUserIdField("corporate_uid");
        mapping.setEmailField("corporate_mail");
        casConfig.setIdentityMapping(mapping);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        // 预期通过映射拿到的 ID 是 alice-corp-id
        when(developerService.getExternalDeveloper("cas", "alice-corp-id")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any()))
                .thenAnswer(
                        invocation -> {
                            com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam
                                    param = invocation.getArgument(0);
                            assertEquals("alice-corp-id", param.getSubject());
                            assertEquals("alice@corp.com", param.getEmail());
                            return developerResult;
                        });

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        String state =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request).getState();
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, state));
        expectedServiceHolder[0] = "ignored"; // 内部 Handler 已重写，此处仅占位

        casService.handleCallback("ST-1", state, request, new MockHttpServletResponse());
    }

    @Test
    void handleCallbackShouldRejectWhenStateCookieMissing() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("http://localhost:12345/cas");

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        MockHttpServletRequest request = buildRequest(18080);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String state =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request).getState();

        assertThrows(
                com.alibaba.himarket.core.exception.BusinessException.class,
                () -> casService.handleCallback("ST-1", state, request, response));
    }

    @Test
    void handleLogoutRequestShouldRevokeIssuedToken() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        server.createContext(
                "/cas/p3/serviceValidate",
                new ValidateHandler(expectedServiceHolder, "alice", "alice@example.com"));
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl(serverUrl);
        casConfig.setLoginEndpoint(serverUrl + "/login");
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        casConfig.setIdentityMapping(defaultIdentityMapping());

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        System.setProperty("jwt.secret", "cas-test-secret");
        System.setProperty("jwt.expiration", "3600000");

        MemoryAuthSessionStore authSessionStore =
                new MemoryAuthSessionStore(new AuthSessionConfig().getCas().getLoginCodeTtl());
        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        authSessionStore,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());
        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, "placeholder"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdpAuthorizeResult authorizeResult =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request);
        String state = authorizeResult.getState();
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, state));
        String serviceUrl =
                splitQueryValue(
                        URI.create(authorizeResult.getRedirectUrl()).getRawQuery(), "service");
        expectedServiceHolder[0] = serviceUrl;

        String redirectUrl = casService.handleCallback("ST-1", state, request, response);
        String code = splitQueryValue(URI.create(redirectUrl).getQuery(), IdpConstants.CODE);
        AuthResult authResult = casService.exchangeCode(code);

        assertEquals(false, authSessionStore.isTokenRevoked(authResult.getAccessToken()));
        assertEquals(1, casService.handleLogoutRequest(logoutRequest("ST-1")));
        assertEquals(true, authSessionStore.isTokenRevoked(authResult.getAccessToken()));
    }

    @Test
    void buildLogoutRedirectUrlShouldHonorLogoutTypeNone() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        casConfig.setSloEnabled(true);
        CasServiceDefinitionConfig serviceDefinition = new CasServiceDefinitionConfig();
        serviceDefinition.setLogoutType(CasServiceLogoutType.NONE);
        casConfig.setServiceDefinition(serviceDefinition);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        assertEquals("https://portal.example.com/login", casService.buildLogoutRedirectUrl("cas"));
        assertEquals(false, casService.getAvailableProviders().get(0).getSloEnabled());
        assertEquals(true, casService.getAvailableProviders().get(0).getInteractiveBrowserLogin());
    }

    @Test
    void buildAuthorizationResultShouldMergeRequestLoginFlags() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasLoginConfig loginConfig = new CasLoginConfig();
        loginConfig.setWarn(true);
        casConfig.setLogin(loginConfig);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        String redirectUrl =
                casService
                        .buildAuthorizationResult(
                                "cas",
                                "/api/v1",
                                CasAuthorizeOptions.builder()
                                        .gateway(true)
                                        .rememberMe(true)
                                        .build(),
                                buildRequest(443))
                        .getRedirectUrl();

        URI uri = URI.create(redirectUrl);
        assertEquals("true", splitQueryValue(uri.getQuery(), IdpConstants.GATEWAY));
        assertEquals("true", splitQueryValue(uri.getQuery(), IdpConstants.WARN));
        assertEquals("true", splitQueryValue(uri.getQuery(), IdpConstants.REMEMBER_ME));
        assertEquals(null, splitQueryValue(uri.getQuery(), IdpConstants.RENEW));
    }

    @Test
    void buildAuthorizationResultShouldRequestHeaderMethodForHeaderResponseType() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas-header");
        casConfig.setName("CAS Header");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasServiceDefinitionConfig serviceDefinition = new CasServiceDefinitionConfig();
        serviceDefinition.setResponseType(CasServiceResponseType.HEADER);
        casConfig.setServiceDefinition(serviceDefinition);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        new MemoryAuthSessionStore(
                                new AuthSessionConfig().getCas().getLoginCodeTtl()),
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        URI uri =
                URI.create(
                        casService
                                .buildAuthorizationResult(
                                        "cas-header", "/api/v1", null, buildRequest(443))
                                .getRedirectUrl());

        assertEquals("HEADER", splitQueryValue(uri.getQuery(), IdpConstants.METHOD));
        assertEquals(1, casService.getAvailableProviders().size());
        assertEquals(false, casService.getAvailableProviders().get(0).getInteractiveBrowserLogin());
    }

    @Test
    void issueProxyTicketShouldUseBoundDeveloperProxyGrantingTicket() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        String[] expectedPgtUrlHolder = new String[1];
        server.createContext(
                "/cas/p3/serviceValidate",
                new ValidateHandler(
                        expectedServiceHolder,
                        expectedPgtUrlHolder,
                        "alice",
                        "alice@example.com",
                        "PGTIOU-1"));
        server.createContext("/cas/proxy", new ProxyHandler("PGT-DEV-1", "PT-DEV-1"));
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl(serverUrl);
        casConfig.setLoginEndpoint(serverUrl + "/login");
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        casConfig.setIdentityMapping(defaultIdentityMapping());
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        casConfig.setProxy(proxyConfig);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(contextHolder.getUser()).thenReturn("dev-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        System.setProperty("jwt.secret", "cas-test-secret");
        System.setProperty("jwt.expiration", "3600000");

        MemoryAuthSessionStore authSessionStore =
                new MemoryAuthSessionStore(new AuthSessionConfig().getCas().getLoginCodeTtl());
        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        authSessionStore,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());
        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdpAuthorizeResult authorizeResult =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request);
        String serviceUrl =
                splitQueryValue(
                        URI.create(authorizeResult.getRedirectUrl()).getRawQuery(), "service");
        String state = authorizeResult.getState();
        expectedServiceHolder[0] = serviceUrl;
        expectedPgtUrlHolder[0] = "https://portal.example.com/api/v1/developers/cas/proxy-callback";
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, state));

        String redirectUrl = casService.handleCallback("ST-1", state, request, response);
        casService.handleProxyCallback("PGTIOU-1", "PGT-DEV-1");
        String code = splitQueryValue(URI.create(redirectUrl).getQuery(), IdpConstants.CODE);
        casService.exchangeCode(code);

        assertEquals(
                "PT-DEV-1",
                casService
                        .issueProxyTicket("cas", "https://target.example.com/service")
                        .getProxyTicket());
    }

    @Test
    void exchangeCodeShouldWaitForProxyGrantingTicketCallback() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        String[] expectedPgtUrlHolder = new String[1];
        server.createContext(
                "/cas/p3/serviceValidate",
                new ValidateHandler(
                        expectedServiceHolder,
                        expectedPgtUrlHolder,
                        "alice",
                        "alice@example.com",
                        "PGTIOU-DELAYED"));
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl(serverUrl);
        casConfig.setLoginEndpoint(serverUrl + "/login");
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        casConfig.setIdentityMapping(defaultIdentityMapping());
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        casConfig.setProxy(proxyConfig);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);
        when(developerService.getExternalDeveloper("cas", "alice")).thenReturn(null);

        DeveloperResult developerResult = new DeveloperResult();
        developerResult.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(developerResult);

        System.setProperty("jwt.secret", "cas-test-secret");
        System.setProperty("jwt.expiration", "3600000");

        MemoryAuthSessionStore authSessionStore =
                new MemoryAuthSessionStore(new AuthSessionConfig().getCas().getLoginCodeTtl());
        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        authSessionStore,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());
        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdpAuthorizeResult authorizeResult =
                casService.buildAuthorizationResult("cas", "/api/v1", null, request);
        String serviceUrl =
                splitQueryValue(
                        URI.create(authorizeResult.getRedirectUrl()).getRawQuery(), "service");
        String state = authorizeResult.getState();
        expectedServiceHolder[0] = serviceUrl;
        expectedPgtUrlHolder[0] = "https://portal.example.com/api/v1/developers/cas/proxy-callback";
        request.setCookies(new Cookie(IdpConstants.CAS_STATE_COOKIE_NAME, state));

        String redirectUrl = casService.handleCallback("ST-1", state, request, response);
        String code = splitQueryValue(URI.create(redirectUrl).getQuery(), IdpConstants.CODE);

        Thread callbackThread =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(200L);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            casService.handleProxyCallback("PGTIOU-DELAYED", "PGT-DELAYED");
                        });
        callbackThread.start();

        AuthResult authResult = casService.exchangeCode(code);
        callbackThread.join();

        assertNotNull(authResult);
        assertNotNull(authResult.getAccessToken());
    }

    @Test
    void issueProxyTicketShouldRejectDisallowedDeveloperTargetService() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        proxyConfig.setTargetServicePattern("^https://allowed\\.example\\.com/.*$");
        casConfig.setProxy(proxyConfig);

        PortalSettingConfig portalSettingConfig = new PortalSettingConfig();
        portalSettingConfig.setCasConfigs(List.of(casConfig));
        portalSettingConfig.setFrontendRedirectUrl("https://portal.example.com/");
        PortalResult portalResult = new PortalResult();
        portalResult.setPortalSettingConfig(portalSettingConfig);

        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(contextHolder.getUser()).thenReturn("dev-1");
        when(portalService.getPortal("portal-1")).thenReturn(portalResult);

        MemoryAuthSessionStore authSessionStore =
                new MemoryAuthSessionStore(new AuthSessionConfig().getCas().getLoginCodeTtl());
        authSessionStore.bindCasProxyGrantingTicket(
                com.alibaba.himarket.service.idp.session.CasSessionScope.DEVELOPER,
                "cas",
                "dev-1",
                "ST-1",
                "PGT-DEV-1");
        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new AuthSessionConfig(),
                        authSessionStore,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser(),
                                new CasJsonTicketValidationParser(),
                                new CasSamlTicketValidationParser()),
                        new CasProxyTicketClient(new CasProxyTicketParser()),
                        new CasLogoutRequestParser(),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());

        assertThrows(
                com.alibaba.himarket.core.exception.BusinessException.class,
                () -> casService.issueProxyTicket("cas", "https://forbidden.example.com/service"));
    }

    private MockHttpServletRequest buildRequest(int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("ignored.local");
        request.setServerPort(port);
        return request;
    }

    private IdentityMapping defaultIdentityMapping() {
        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("user");
        identityMapping.setUserNameField("user");
        identityMapping.setEmailField("mail");
        return identityMapping;
    }

    private String splitQueryValue(String query, String key) {
        String prefix = key + "=";
        for (String item : query.split("&")) {
            if (item.startsWith(prefix)) {
                return java.net.URLDecoder.decode(
                        item.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String logoutRequest(String sessionIndex) {
        return "<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                + "<samlp:SessionIndex>"
                + sessionIndex
                + "</samlp:SessionIndex>"
                + "</samlp:LogoutRequest>";
    }

    private static class ValidateHandler implements HttpHandler {

        private final String[] expectedServiceHolder;

        private final String[] expectedPgtUrlHolder;

        private final byte[] successBody;

        private ValidateHandler(String[] expectedServiceHolder, String user, String mail) {
            this(expectedServiceHolder, new String[1], user, mail, null);
        }

        private ValidateHandler(
                String[] expectedServiceHolder,
                String[] expectedPgtUrlHolder,
                String user,
                String mail,
                String proxyGrantingTicket) {
            this.expectedServiceHolder = expectedServiceHolder;
            this.expectedPgtUrlHolder = expectedPgtUrlHolder;
            this.successBody =
                    ("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                                    + "<cas:authenticationSuccess>"
                                    + "<cas:user>"
                                    + user
                                    + "</cas:user>"
                                    + (proxyGrantingTicket == null
                                            ? ""
                                            : "<cas:proxyGrantingTicket>"
                                                    + proxyGrantingTicket
                                                    + "</cas:proxyGrantingTicket>")
                                    + "<cas:attributes>"
                                    + "<cas:user>"
                                    + user
                                    + "</cas:user>"
                                    + "<cas:mail>"
                                    + mail
                                    + "</cas:mail>"
                                    + "</cas:attributes>"
                                    + "</cas:authenticationSuccess>"
                                    + "</cas:serviceResponse>")
                            .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String service = extract(query, "service");
            String ticket = extract(query, "ticket");
            String pgtUrl = extract(query, IdpConstants.PGT_URL);
            if (!expectedServiceHolder[0].equals(service) || !"ST-1".equals(ticket)) {
                writeFailure(
                        exchange,
                        "Unexpected CAS validate request service="
                                + service
                                + ", ticket="
                                + ticket
                                + ", expectedService="
                                + expectedServiceHolder[0]);
                return;
            }
            if (expectedPgtUrlHolder[0] != null && !expectedPgtUrlHolder[0].equals(pgtUrl)) {
                writeFailure(
                        exchange,
                        "Unexpected CAS proxy callback URL actual="
                                + pgtUrl
                                + ", expected="
                                + expectedPgtUrlHolder[0]);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, successBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(successBody);
            }
        }

        private String extract(String query, String key) {
            String prefix = key + "=";
            for (String item : query.split("&")) {
                if (item.startsWith(prefix)) {
                    return java.net.URLDecoder.decode(
                            item.substring(prefix.length()), StandardCharsets.UTF_8);
                }
            }
            return null;
        }

        private void writeFailure(HttpExchange exchange, String message) throws IOException {
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class ProxyHandler implements HttpHandler {

        private final String expectedPgt;

        private final String proxyTicket;

        private ProxyHandler(String expectedPgt, String proxyTicket) {
            this.expectedPgt = expectedPgt;
            this.proxyTicket = proxyTicket;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            if (!expectedPgt.equals(extract(query, IdpConstants.PGT))) {
                throw new IOException("Unexpected PGT");
            }
            byte[] body =
                    ("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                                    + "<cas:proxySuccess><cas:proxyTicket>"
                                    + proxyTicket
                                    + "</cas:proxyTicket></cas:proxySuccess>"
                                    + "</cas:serviceResponse>")
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        private String extract(String query, String key) {
            String prefix = key + "=";
            for (String item : query.split("&")) {
                if (item.startsWith(prefix)) {
                    return java.net.URLDecoder.decode(
                            item.substring(prefix.length()), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
