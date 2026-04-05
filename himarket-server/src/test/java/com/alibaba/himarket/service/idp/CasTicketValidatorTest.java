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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CasTicketValidatorTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validateShouldUseSamlPostFlow() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String[] methodHolder = new String[1];
        String[] queryHolder = new String[1];
        String[] bodyHolder = new String[1];
        server.createContext(
                "/cas/samlValidate", new SamlHandler(methodHolder, queryHolder, bodyHolder));
        server.start();

        CasConfig config = new CasConfig();
        config.setProvider("cas");
        config.setServerUrl("http://localhost:" + server.getAddress().getPort() + "/cas");
        CasValidationConfig validationConfig = new CasValidationConfig();
        validationConfig.setProtocolVersion(CasProtocolVersion.SAML1);
        config.setValidation(validationConfig);

        CasTicketValidator validator =
                new CasTicketValidator(
                        new CasTicketValidationParser(),
                        new CasJsonTicketValidationParser(),
                        new CasSamlTicketValidationParser());

        Map<String, Object> attributes =
                validator.validate(config, "ST-SAML-1", "https://portal.example.com/callback");

        assertEquals("POST", methodHolder[0]);
        assertEquals(
                "https://portal.example.com/callback", extractQueryValue(queryHolder[0], "TARGET"));
        assertEquals("ST-SAML-1", extractQueryValue(queryHolder[0], "SAMLArt"));
        assertTrue(bodyHolder[0].contains("ST-SAML-1"));
        assertTrue(bodyHolder[0].contains("RequestID=\"_"));
        assertTrue(bodyHolder[0].contains("IssueInstant=\""));
        assertEquals("alice", attributes.get("user"));
        assertEquals("alice@example.com", attributes.get("mail"));
    }

    @Test
    void validateShouldUseGetFlowForCas3Json() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String[] methodHolder = new String[1];
        String[] queryHolder = new String[1];
        server.createContext("/cas/p3/serviceValidate", new JsonHandler(methodHolder, queryHolder));
        server.start();

        CasConfig config = new CasConfig();
        config.setProvider("cas");
        config.setServerUrl("http://localhost:" + server.getAddress().getPort() + "/cas");
        CasValidationConfig validationConfig = new CasValidationConfig();
        validationConfig.setProtocolVersion(CasProtocolVersion.CAS3);
        validationConfig.setResponseFormat(
                com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat.JSON);
        config.setValidation(validationConfig);

        CasTicketValidator validator =
                new CasTicketValidator(
                        new CasTicketValidationParser(),
                        new CasJsonTicketValidationParser(),
                        new CasSamlTicketValidationParser());

        Map<String, Object> attributes =
                validator.validate(
                        config,
                        "ST-JSON-1",
                        "https://portal.example.com/callback",
                        "https://portal.example.com/proxy-callback");

        assertEquals("GET", methodHolder[0]);
        assertEquals(
                "https://portal.example.com/callback",
                extractQueryValue(queryHolder[0], "service"));
        assertEquals("ST-JSON-1", extractQueryValue(queryHolder[0], "ticket"));
        assertEquals(
                "https://portal.example.com/proxy-callback",
                extractQueryValue(queryHolder[0], "pgtUrl"));
        assertEquals("JSON", extractQueryValue(queryHolder[0], "format"));
        assertEquals("alice", attributes.get("user"));
    }

    private static final class SamlHandler implements HttpHandler {

        private final String[] methodHolder;

        private final String[] queryHolder;

        private final String[] bodyHolder;

        private SamlHandler(String[] methodHolder, String[] queryHolder, String[] bodyHolder) {
            this.methodHolder = methodHolder;
            this.queryHolder = queryHolder;
            this.bodyHolder = bodyHolder;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            methodHolder[0] = exchange.getRequestMethod();
            queryHolder[0] = exchange.getRequestURI().getRawQuery();
            bodyHolder[0] =
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes =
                    CasSamlTicketValidationParserTest.successResponse()
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    private static final class JsonHandler implements HttpHandler {

        private final String[] methodHolder;

        private final String[] queryHolder;

        private JsonHandler(String[] methodHolder, String[] queryHolder) {
            this.methodHolder = methodHolder;
            this.queryHolder = queryHolder;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            methodHolder[0] = exchange.getRequestMethod();
            queryHolder[0] = exchange.getRequestURI().getRawQuery();
            byte[] bytes =
                    """
                    {"serviceResponse":{"authenticationSuccess":{"user":"alice","attributes":{"mail":"alice@example.com"}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    private String extractQueryValue(String query, String key) {
        String prefix = key + "=";
        for (String item : query.split("&")) {
            if (item.startsWith(prefix)) {
                return URLDecoder.decode(item.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
