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
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtDirectAcquireMode;
import com.alibaba.himarket.support.enums.JwtDirectIdentitySource;
import com.alibaba.himarket.support.enums.JwtDirectTokenSource;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.OidcConfig;
import com.alibaba.himarket.support.portal.TrustedHeaderConfig;
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
    void validateCasConfigsShouldAcceptHeaderResponseType() {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setServerUrl("https://cas.example.com/cas");
        CasServiceDefinitionConfig serviceDefinitionConfig = new CasServiceDefinitionConfig();
        serviceDefinitionConfig.setResponseType(CasServiceResponseType.HEADER);
        casConfig.setServiceDefinition(serviceDefinitionConfig);

        new IdpServiceImpl().validateCasConfigs(List.of(casConfig));
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

    @Test
    void validateOAuth2ConfigsShouldAcceptStandardJwtBearerConfigWithoutDirectFlow() {
        OAuth2Config config = createStandardJwtBearerConfig();

        new IdpServiceImpl().validateOAuth2Configs(List.of(config));
    }

    @Test
    void validateOAuth2ConfigsShouldAcceptJwtDirectClaimsConfig() {
        OAuth2Config config = createStandardJwtBearerConfig();
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.DIRECT);
        jwtBearerConfig.setIdentitySource(JwtDirectIdentitySource.CLAIMS);

        new IdpServiceImpl().validateOAuth2Configs(List.of(config));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectDirectFlowWithoutAuthorizationEndpoint() {
        OAuth2Config config = createStandardJwtBearerConfig();
        config.getJwtBearerConfig().setAcquireMode(JwtDirectAcquireMode.DIRECT);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectMissingTicketExchangeUrl() {
        OAuth2Config config = createStandardJwtBearerConfig();
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_EXCHANGE);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectInvalidTicketExchangeUrl() {
        OAuth2Config config = createStandardJwtBearerConfig();
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl("cas.example.com/exchange");

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectTrustedProxyHostnameCidr() {
        OAuth2Config config = new OAuth2Config();
        config.setProvider("trusted-header");
        config.setName("Trusted Header");
        config.setGrantType(GrantType.TRUSTED_HEADER);

        TrustedHeaderConfig trustedHeaderConfig = new TrustedHeaderConfig();
        trustedHeaderConfig.setEnabled(true);
        trustedHeaderConfig.setTrustedProxyCidrs(List.of("example.com/24"));
        config.setTrustedHeaderConfig(trustedHeaderConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectUserInfoModeWithoutEndpoint() {
        OAuth2Config config = createStandardJwtBearerConfig();
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setIdentitySource(JwtDirectIdentitySource.USERINFO);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldAllowTicketValidateWithoutJwtVerificationConfig() {
        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl("https://cas.example.com/oauth2/validate");

        OAuth2Config config = new OAuth2Config();
        config.setProvider("cas-jwt");
        config.setName("CAS JWT");
        config.setGrantType(GrantType.JWT_BEARER);
        config.setJwtBearerConfig(jwtBearerConfig);

        new IdpServiceImpl().validateOAuth2Configs(List.of(config));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectUserInfoWithTicketValidate() {
        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setAcquireMode(JwtDirectAcquireMode.TICKET_VALIDATE);
        jwtBearerConfig.setTicketExchangeUrl("https://cas.example.com/oauth2/validate");
        jwtBearerConfig.setIdentitySource(JwtDirectIdentitySource.USERINFO);
        jwtBearerConfig.setUserInfoEndpoint("https://cas.example.com/oauth2/userinfo");

        OAuth2Config config = new OAuth2Config();
        config.setProvider("cas-jwt");
        config.setName("CAS JWT");
        config.setGrantType(GrantType.JWT_BEARER);
        config.setJwtBearerConfig(jwtBearerConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectBodyTokenSourceForBrowserLogin() {
        OAuth2Config config = createStandardJwtBearerConfig();
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        jwtBearerConfig.setAuthorizationEndpoint("https://cas.example.com/oauth2/authorize");
        jwtBearerConfig.setTokenSource(JwtDirectTokenSource.BODY);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    @Test
    void validateOAuth2ConfigsShouldAcceptTrustedHeaderConfig() {
        OAuth2Config config = new OAuth2Config();
        config.setProvider("trusted-header");
        config.setName("Trusted Header");
        config.setGrantType(GrantType.TRUSTED_HEADER);

        TrustedHeaderConfig trustedHeaderConfig = new TrustedHeaderConfig();
        trustedHeaderConfig.setEnabled(true);
        trustedHeaderConfig.setTrustedProxyCidrs(List.of("127.0.0.1/32"));
        trustedHeaderConfig.setUserIdHeader("X-Auth-User");
        trustedHeaderConfig.setUserNameHeader("X-Auth-Name");
        trustedHeaderConfig.setEmailHeader("X-Auth-Email");
        trustedHeaderConfig.setGroupsHeader("X-Auth-Groups");
        trustedHeaderConfig.setRolesHeader("X-Auth-Roles");
        config.setTrustedHeaderConfig(trustedHeaderConfig);

        new IdpServiceImpl().validateOAuth2Configs(List.of(config));
    }

    @Test
    void validateOAuth2ConfigsShouldRejectTrustedHeaderWithoutProxyAllowlist() {
        OAuth2Config config = new OAuth2Config();
        config.setProvider("trusted-header");
        config.setName("Trusted Header");
        config.setGrantType(GrantType.TRUSTED_HEADER);

        TrustedHeaderConfig trustedHeaderConfig = new TrustedHeaderConfig();
        trustedHeaderConfig.setEnabled(true);
        trustedHeaderConfig.setUserIdHeader("X-Auth-User");
        config.setTrustedHeaderConfig(trustedHeaderConfig);

        assertThrows(
                BusinessException.class,
                () -> new IdpServiceImpl().validateOAuth2Configs(List.of(config)));
    }

    private OAuth2Config createStandardJwtBearerConfig() {
        JwtBearerConfig jwtBearerConfig = new JwtBearerConfig();
        jwtBearerConfig.setIssuer("https://cas.example.com/oauth2");
        jwtBearerConfig.setJwkSetUri("https://cas.example.com/oauth2/jwks");
        jwtBearerConfig.setAudiences(List.of("himarket-api"));

        OAuth2Config config = new OAuth2Config();
        config.setProvider("cas-jwt");
        config.setName("CAS JWT");
        config.setGrantType(GrantType.JWT_BEARER);
        config.setJwtBearerConfig(jwtBearerConfig);
        return config;
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
