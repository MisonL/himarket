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
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.idp.JwtBearerTokenVerifier;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        System.clearProperty("jwt.secret");
        System.clearProperty("jwt.expiration");
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

        System.setProperty("jwt.secret", "oauth2-test-secret");
        System.setProperty("jwt.expiration", "3600000");

        OAuth2ServiceImpl service =
                new OAuth2ServiceImpl(
                        portalService,
                        developerService,
                        idpService,
                        contextHolder,
                        new JwtBearerTokenVerifier());

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

        OAuth2ServiceImpl service =
                new OAuth2ServiceImpl(
                        portalService,
                        developerService,
                        idpService,
                        contextHolder,
                        new JwtBearerTokenVerifier());

        String jwtToken = createJwt(rsaKey, issuer, "other-aud");
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.authenticate(GrantType.JWT_BEARER.getType(), jwtToken));
        assertEquals("NOT_FOUND", ex.getCode());
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

        PortalSettingConfig settingConfig = new PortalSettingConfig();
        settingConfig.setOauth2Configs(List.of(config));
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
}
