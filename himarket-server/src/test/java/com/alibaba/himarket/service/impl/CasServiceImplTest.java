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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.CasTicketValidationParser;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.PortalFrontendUrlResolver;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.util.ReflectionTestUtils;

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
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", null);
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 0L);
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

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "cas-test-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        CasServiceImpl casService =
                new CasServiceImpl(
                        portalService,
                        developerService,
                        contextHolder,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser()),
                        new PortalFrontendUrlResolver(portalService, contextHolder),
                        new IdpStateCodec());
        MockHttpServletRequest request = buildRequest(server.getAddress().getPort());
        MockHttpServletResponse response = new MockHttpServletResponse();

        String authUrl = casService.buildAuthorizationUrl("cas", "/api/v1", request);
        URI authUri = URI.create(authUrl);
        assertEquals(
                serverUrl + "/login",
                authUri.getScheme() + "://" + authUri.getAuthority() + authUri.getPath());
        String serviceUrl = splitQueryValue(authUri.getQuery(), "service");
        assertEquals(
                "https://portal.example.com/cas/callback",
                URI.create(serviceUrl).getScheme()
                        + "://"
                        + URI.create(serviceUrl).getHost()
                        + URI.create(serviceUrl).getPath());
        String state = splitQueryValue(URI.create(serviceUrl).getQuery(), "state");
        expectedServiceHolder[0] = serviceUrl;

        AuthResult authResult = casService.handleCallback("ST-1", state, request, response);

        assertNotNull(authResult);
        assertNotNull(authResult.getAccessToken());
        assertEquals("Bearer", authResult.getTokenType());
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

    private static class ValidateHandler implements HttpHandler {

        private final String[] expectedServiceHolder;

        private final byte[] successBody;

        private ValidateHandler(String[] expectedServiceHolder, String user, String mail) {
            this.expectedServiceHolder = expectedServiceHolder;
            this.successBody =
                    ("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                                    + "<cas:authenticationSuccess>"
                                    + "<cas:user>"
                                    + user
                                    + "</cas:user>"
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
            String query = exchange.getRequestURI().getQuery();
            String service = extract(query, "service");
            String ticket = extract(query, "ticket");
            if (!expectedServiceHolder[0].equals(service) || !"ST-1".equals(ticket)) {
                throw new IOException("Unexpected CAS validate request");
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
    }
}
