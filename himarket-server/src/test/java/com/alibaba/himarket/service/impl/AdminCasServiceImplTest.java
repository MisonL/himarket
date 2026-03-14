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
import static org.mockito.Mockito.when;

import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.config.AuthSessionConfig;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.entity.Administrator;
import com.alibaba.himarket.repository.AdministratorRepository;
import com.alibaba.himarket.service.idp.CasLogoutRequestParser;
import com.alibaba.himarket.service.idp.CasTicketValidationParser;
import com.alibaba.himarket.service.idp.IdpStateCodec;
import com.alibaba.himarket.service.idp.session.MemoryAuthSessionStore;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.IdentityMapping;
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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminCasServiceImplTest {

    @Mock private AdministratorRepository administratorRepository;

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
    void exchangeCodeShouldIssueAdminTokenAndSupportLogoutRequest() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String serverUrl = "http://localhost:" + server.getAddress().getPort() + "/cas";
        String[] expectedServiceHolder = new String[1];
        server.createContext(
                "/cas/p3/serviceValidate", new ValidateHandler(expectedServiceHolder, "admin"));
        server.start();

        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl(serverUrl);
        casConfig.setLoginEndpoint(serverUrl + "/login");
        casConfig.setValidateEndpoint(serverUrl + "/p3/serviceValidate");
        casConfig.setIdentityMapping(identityMapping());

        AdminAuthConfig adminAuthConfig = new AdminAuthConfig();
        adminAuthConfig.setFrontendRedirectUrl("https://admin.example.com/");
        adminAuthConfig.setCasConfigs(List.of(casConfig));

        Administrator administrator =
                Administrator.builder()
                        .adminId("admin-1")
                        .username("admin")
                        .passwordHash("x")
                        .build();
        when(administratorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(administrator));

        ReflectionTestUtils.setField(TokenUtil.class, "JWT_SECRET", "admin-cas-secret");
        ReflectionTestUtils.setField(TokenUtil.class, "JWT_EXPIRE_MILLIS", 3600_000L);

        MemoryAuthSessionStore authSessionStore =
                new MemoryAuthSessionStore(new AuthSessionConfig().getCas().getLoginCodeTtl());
        AdminCasServiceImpl service =
                new AdminCasServiceImpl(
                        adminAuthConfig,
                        new AuthSessionConfig(),
                        administratorRepository,
                        authSessionStore,
                        new com.alibaba.himarket.service.idp.CasTicketValidator(
                                new CasTicketValidationParser()),
                        new CasLogoutRequestParser(),
                        new com.alibaba.himarket.service.idp.AdminFrontendUrlResolver(
                                adminAuthConfig),
                        new IdpStateCodec());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("ignored.local");
        request.setServerPort(server.getAddress().getPort());
        MockHttpServletResponse response = new MockHttpServletResponse();

        var authorizeResult = service.buildAuthorizationResult("cas", "/api/v1", request);
        String state = authorizeResult.getState();
        String serviceUrl = buildServiceUrl(authorizeResult);
        assertEquals(
                "https://admin.example.com/api/v1/admins/cas/callback",
                URI.create(serviceUrl).getScheme()
                        + "://"
                        + URI.create(serviceUrl).getHost()
                        + URI.create(serviceUrl).getPath());
        expectedServiceHolder[0] = serviceUrl;
        request.setCookies(new Cookie(IdpConstants.ADMIN_CAS_STATE_COOKIE_NAME, state));

        String redirectUrl = service.handleCallback("ST-ADMIN-1", state, request, response);
        String code = splitQueryValue(URI.create(redirectUrl).getQuery(), IdpConstants.CODE);
        var authResult = service.exchangeCode(code);

        assertNotNull(authResult.getAccessToken());
        assertEquals(false, authSessionStore.isTokenRevoked(authResult.getAccessToken()));
        assertEquals(1, service.handleLogoutRequest(logoutRequest("ST-ADMIN-1")));
        assertEquals(true, authSessionStore.isTokenRevoked(authResult.getAccessToken()));
    }

    private IdentityMapping identityMapping() {
        IdentityMapping identityMapping = new IdentityMapping();
        identityMapping.setUserIdField("user");
        return identityMapping;
    }

    private String buildServiceUrl(com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult result) {
        return splitQueryValue(
                URI.create(result.getRedirectUrl()).getQuery(), IdpConstants.SERVICE);
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

        private final byte[] successBody;

        private ValidateHandler(String[] expectedServiceHolder, String user) {
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
                                    + "</cas:attributes>"
                                    + "</cas:authenticationSuccess>"
                                    + "</cas:serviceResponse>")
                            .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String service = extract(query, IdpConstants.SERVICE);
            String ticket = extract(query, IdpConstants.TICKET);
            if (!expectedServiceHolder[0].equals(service) || !"ST-ADMIN-1".equals(ticket)) {
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
