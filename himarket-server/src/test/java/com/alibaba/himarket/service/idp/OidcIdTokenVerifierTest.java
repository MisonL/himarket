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

package com.alibaba.himarket.service.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OidcIdTokenVerifierTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifyAndExtractClaimsShouldAcceptValidIdToken() throws Exception {
        RSAKey rsaKey = generateRsaKey("cas-key");
        String issuer = startJwksServer(rsaKey);

        AuthCodeConfig authCodeConfig = new AuthCodeConfig();
        authCodeConfig.setClientId("himarket-client");
        authCodeConfig.setIssuer(issuer);
        authCodeConfig.setJwkSetUri(issuer + "/jwks");

        String idToken = createIdToken(rsaKey, issuer, "himarket-client", "nonce-123");

        Map<String, Object> claims =
                new OidcIdTokenVerifier()
                        .verifyAndExtractClaims(idToken, authCodeConfig, "nonce-123");

        assertEquals("alice", claims.get("sub"));
        assertEquals("Alice", claims.get("name"));
        assertEquals("alice@example.com", claims.get("email"));
    }

    @Test
    void verifyAndExtractClaimsShouldRejectNonceMismatch() throws Exception {
        RSAKey rsaKey = generateRsaKey("cas-key");
        String issuer = startJwksServer(rsaKey);

        AuthCodeConfig authCodeConfig = new AuthCodeConfig();
        authCodeConfig.setClientId("himarket-client");
        authCodeConfig.setIssuer(issuer);
        authCodeConfig.setJwkSetUri(issuer + "/jwks");

        String idToken = createIdToken(rsaKey, issuer, "himarket-client", "nonce-123");

        assertThrows(
                BusinessException.class,
                () ->
                        new OidcIdTokenVerifier()
                                .verifyAndExtractClaims(idToken, authCodeConfig, "nonce-456"));
    }

    private String startJwksServer(RSAKey rsaKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String issuer = "http://localhost:" + server.getAddress().getPort() + "/cas/oidc";
        String body = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        server.createContext("/cas/oidc/jwks", new JsonHandler(body));
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

    private String createIdToken(RSAKey rsaKey, String issuer, String audience, String nonce)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claimsSet =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("alice")
                        .audience(audience)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .claim("nonce", nonce)
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
