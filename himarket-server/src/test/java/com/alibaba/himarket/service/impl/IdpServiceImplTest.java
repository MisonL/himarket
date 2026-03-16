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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.OidcConfig;
import com.alibaba.himarket.support.portal.cas.CasLoginConfig;
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasProxyConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceDefinitionConfig;
import com.alibaba.himarket.support.portal.cas.CasServiceResponseType;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdpServiceImplTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validateOidcConfigsShouldPopulateDiscoveredEndpointsAndJwkSetUri() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/cas/oidc";
        String discoveryJson =
                "{"
                        + "\"authorization_endpoint\":\""
                        + issuer
                        + "/authorize\","
                        + "\"token_endpoint\":\""
                        + issuer
                        + "/token\","
                        + "\"userinfo_endpoint\":\""
                        + issuer
                        + "/profile\","
                        + "\"jwks_uri\":\""
                        + issuer
                        + "/jwks\""
                        + "}";
        server.createContext(
                "/cas/oidc/.well-known/openid-configuration", new JsonHandler(discoveryJson));
        server.start();

        AuthCodeConfig authCodeConfig = new AuthCodeConfig();
        authCodeConfig.setClientId("himarket-client");
        authCodeConfig.setClientSecret("secret");
        authCodeConfig.setScopes("openid profile email");
        authCodeConfig.setIssuer(issuer);

        OidcConfig oidcConfig = new OidcConfig();
        oidcConfig.setProvider("cas");
        oidcConfig.setName("CAS");
        oidcConfig.setAuthCodeConfig(authCodeConfig);

        new IdpServiceImpl().validateOidcConfigs(List.of(oidcConfig));

        assertEquals(issuer + "/authorize", authCodeConfig.getAuthorizationEndpoint());
        assertEquals(issuer + "/token", authCodeConfig.getTokenEndpoint());
        assertEquals(issuer + "/profile", authCodeConfig.getUserInfoEndpoint());
        assertEquals(issuer + "/jwks", authCodeConfig.getJwkSetUri());
    }

    @Test
    void validateCasConfigsShouldAcceptRelativeEndpoints() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        casConfig.setLoginEndpoint("/login");
        casConfig.setValidateEndpoint("/p3/serviceValidate");
        casConfig.setLogoutEndpoint("/logout");

        new IdpServiceImpl().validateCasConfigs(List.of(casConfig));
    }

    @Test
    void validateCasConfigsShouldRejectGatewayRenewConflict() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasLoginConfig loginConfig = new CasLoginConfig();
        loginConfig.setGateway(true);
        loginConfig.setRenew(true);
        casConfig.setLogin(loginConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateCasConfigs(List.of(casConfig)));
    }

    @Test
    void validateCasConfigsShouldRejectJsonSamlValidation() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasValidationConfig validationConfig = new CasValidationConfig();
        validationConfig.setProtocolVersion(CasProtocolVersion.SAML1);
        validationConfig.setResponseFormat(CasValidationResponseFormat.JSON);
        casConfig.setValidation(validationConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateCasConfigs(List.of(casConfig)));
    }

    @Test
    void validateCasConfigsShouldRejectHeaderResponseType() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasServiceDefinitionConfig serviceDefinitionConfig = new CasServiceDefinitionConfig();
        serviceDefinitionConfig.setResponseType(CasServiceResponseType.HEADER);
        casConfig.setServiceDefinition(serviceDefinitionConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateCasConfigs(List.of(casConfig)));
    }

    @Test
    void validateCasConfigsShouldRejectRelativeProxyCallbackPathWithoutSlash() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasProxyConfig proxyConfig = new CasProxyConfig();
        proxyConfig.setEnabled(true);
        proxyConfig.setCallbackPath("developers/cas/proxy-callback");
        casConfig.setProxy(proxyConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateCasConfigs(List.of(casConfig)));
    }

    @Test
    void validateLdapConfigsShouldRejectFilterWithoutPlaceholder() {
        LdapConfig ldapConfig = new LdapConfig();
        ldapConfig.setProvider("ldap");
        ldapConfig.setName("LDAP");
        ldapConfig.setServerUrl("ldap://localhost:389");
        ldapConfig.setBaseDn("dc=example,dc=com");
        ldapConfig.setUserSearchFilter("(uid=alice)");

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateLdapConfigs(List.of(ldapConfig)));
    }

    @Test
    void validateLdapConfigsShouldAcceptValidConfig() {
        LdapConfig ldapConfig = new LdapConfig();
        ldapConfig.setProvider("ldap");
        ldapConfig.setName("LDAP");
        ldapConfig.setServerUrl("ldap://localhost:389");
        ldapConfig.setBaseDn("dc=example,dc=com");
        ldapConfig.setUserSearchFilter("(uid={0})");

        new IdpServiceImpl().validateLdapConfigs(List.of(ldapConfig));
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
}
