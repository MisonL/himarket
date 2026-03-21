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

package com.alibaba.himarket.service.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.HigressClient;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.HigressAuthConfig;
import com.alibaba.himarket.support.consumer.HmacConfig;
import com.alibaba.himarket.support.consumer.JwtConfig;
import com.alibaba.himarket.support.enums.ConsumerCredentialType;
import com.alibaba.himarket.support.enums.CredentialMode;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.HigressRefConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;

class HigressOperatorTest {

    private final HigressOperator operator = new HigressOperator(mock(ToolManager.class));

    @Test
    void buildHigressConsumerShouldSerializeApiKeyCredential() {
        ApiKeyConfig.ApiKeyCredential apiKeyCredential = new ApiKeyConfig.ApiKeyCredential();
        apiKeyCredential.setApiKey("apikey-1");
        apiKeyCredential.setMode(CredentialMode.SYSTEM);

        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setSource("Default");
        apiKeyConfig.setKey("Authorization");
        apiKeyConfig.setCredentials(List.of(apiKeyCredential));

        ConsumerCredential credential = new ConsumerCredential();
        credential.setApiKeyConfig(apiKeyConfig);

        HigressOperator.HigressConsumerConfig consumerConfig =
                operator.buildHigressConsumer("consumer-1", credential);

        assertEquals("consumer-1", consumerConfig.getName());
        Map<String, Object> payload = consumerConfig.getCredentials().get(0);
        assertEquals("key-auth", payload.get("type"));
        assertEquals("BEARER", payload.get("source"));
        assertEquals("Authorization", payload.get("key"));
        assertEquals(List.of("apikey-1"), payload.get("values"));
    }

    @Test
    void buildHigressConsumerShouldSerializeHmacCredential() {
        HmacConfig.HmacCredential hmacCredential = new HmacConfig.HmacCredential();
        hmacCredential.setAk("ak-1");
        hmacCredential.setSk("sk-1");
        hmacCredential.setMode(CredentialMode.CUSTOM);

        HmacConfig hmacConfig = new HmacConfig();
        hmacConfig.setCredentials(List.of(hmacCredential));

        ConsumerCredential credential = new ConsumerCredential();
        credential.setHmacConfig(hmacConfig);

        HigressOperator.HigressConsumerConfig consumerConfig =
                operator.buildHigressConsumer("consumer-1", credential);

        Map<String, Object> payload = consumerConfig.getCredentials().get(0);
        assertEquals("hmac-auth", payload.get("type"));
        assertEquals("ak-1", payload.get("access_key"));
        assertEquals("sk-1", payload.get("secret_key"));
    }

    @Test
    void buildHigressConsumerShouldSerializeJwtCredential() {
        JwtConfig.ClaimsToHeader claimToHeader = new JwtConfig.ClaimsToHeader();
        claimToHeader.setClaim("sub");
        claimToHeader.setHeader("x-user-id");
        claimToHeader.setOverride(true);

        JwtConfig.FromHeader fromHeader = new JwtConfig.FromHeader();
        fromHeader.setName("Authorization");
        fromHeader.setValuePrefix("Bearer ");

        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setIssuer("https://issuer.example.com");
        jwtConfig.setJwks("{\"keys\":[]}");
        jwtConfig.setClaimsToHeaders(List.of(claimToHeader));
        jwtConfig.setFromHeaders(List.of(fromHeader));
        jwtConfig.setFromParams(List.of("access_token"));
        jwtConfig.setFromCookies(List.of("token"));
        jwtConfig.setClockSkewSeconds(120L);
        jwtConfig.setKeepToken(Boolean.TRUE);

        ConsumerCredential credential = new ConsumerCredential();
        credential.setJwtConfig(jwtConfig);

        HigressOperator.HigressConsumerConfig consumerConfig =
                operator.buildHigressConsumer("consumer-1", credential);

        Map<String, Object> payload = consumerConfig.getCredentials().get(0);
        assertEquals("jwt-auth", payload.get("type"));
        assertEquals("https://issuer.example.com", payload.get("issuer"));
        assertEquals("{\"keys\":[]}", payload.get("jwks"));
        assertEquals(120L, payload.get("clock_skew_seconds"));
        assertEquals(Boolean.TRUE, payload.get("keep_token"));
        assertInstanceOf(List.class, payload.get("claims_to_headers"));
        assertInstanceOf(List.class, payload.get("from_headers"));
        assertNotNull(payload.get("from_params"));
        assertNotNull(payload.get("from_cookies"));
    }

    @Test
    void fetchModelConfigShouldExposeSingleAllowedCredentialType() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        HigressRefConfig refConfig = new HigressRefConfig();
        refConfig.setModelRouteName("model-route");

        HigressOperator.HigressAIRoute aiRoute = new HigressOperator.HigressAIRoute();
        aiRoute.setName("model-route");
        aiRoute.setDomains(List.of("ai.example.com"));
        aiRoute.setPathPredicate(new com.alibaba.higress.sdk.model.route.RoutePredicate());
        aiRoute.setAuthConfig(
                new HigressOperator.RouteAuthConfig(
                        true, List.of("jwt-auth"), List.of("consumer-1")));

        HigressOperator.HigressResponse<HigressOperator.HigressAIRoute> aiRouteResponse =
                new HigressOperator.HigressResponse<>(aiRoute);
        HigressOperator.HigressResponse<HigressOperator.HigressDomainConfig> domainResponse =
                new HigressOperator.HigressResponse<>(
                        new HigressOperator.HigressDomainConfig("ai.example.com", "on"));

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/ai/routes/model-route"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressAIRoute>>>
                                        any()))
                .thenReturn(aiRouteResponse);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/domains/ai.example.com"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressDomainConfig>>>
                                        any()))
                .thenReturn(domainResponse);

        TestableHigressOperator operator = new TestableHigressOperator(client);

        ModelConfigResult result =
                JSONUtil.toBean(
                        operator.fetchModelConfig(gateway, refConfig), ModelConfigResult.class);

        assertEquals(ConsumerCredentialType.JWT, result.getRequiredCredentialType());
    }

    @Test
    void fetchModelConfigShouldIgnoreMixedAllowedCredentialTypes() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        HigressRefConfig refConfig = new HigressRefConfig();
        refConfig.setModelRouteName("model-route");

        HigressOperator.HigressAIRoute aiRoute = new HigressOperator.HigressAIRoute();
        aiRoute.setName("model-route");
        aiRoute.setDomains(List.of("ai.example.com"));
        aiRoute.setPathPredicate(new com.alibaba.higress.sdk.model.route.RoutePredicate());
        aiRoute.setAuthConfig(
                new HigressOperator.RouteAuthConfig(
                        true, List.of("key-auth", "jwt-auth"), List.of("consumer-1")));

        HigressOperator.HigressResponse<HigressOperator.HigressAIRoute> aiRouteResponse =
                new HigressOperator.HigressResponse<>(aiRoute);
        HigressOperator.HigressResponse<HigressOperator.HigressDomainConfig> domainResponse =
                new HigressOperator.HigressResponse<>(
                        new HigressOperator.HigressDomainConfig("ai.example.com", "on"));

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/ai/routes/model-route"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressAIRoute>>>
                                        any()))
                .thenReturn(aiRouteResponse);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/domains/ai.example.com"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressDomainConfig>>>
                                        any()))
                .thenReturn(domainResponse);

        TestableHigressOperator operator = new TestableHigressOperator(client);

        ModelConfigResult result =
                JSONUtil.toBean(
                        operator.fetchModelConfig(gateway, refConfig), ModelConfigResult.class);

        assertNull(result.getRequiredCredentialType());
    }

    @Test
    void createConsumerShouldTreatTimeoutAsSuccessWhenConsumerStateMatches() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        ApiKeyConfig.ApiKeyCredential apiKeyCredential = new ApiKeyConfig.ApiKeyCredential();
        apiKeyCredential.setApiKey("apikey-1");

        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setSource("BEARER");
        apiKeyConfig.setCredentials(List.of(apiKeyCredential));

        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");

        ConsumerCredential credential = new ConsumerCredential();
        credential.setApiKeyConfig(apiKeyConfig);

        HigressOperator.HigressConsumerConfig expected =
                operator.buildHigressConsumer("consumer-1", credential);

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/consumers"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(expected),
                        org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/consumers/consumer-1"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressConsumer>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressConsumer(
                                        "consumer-1", expected.getCredentials())));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        String result =
                operator.createConsumer(consumer, credential, gatewayConfigWithGateway(gateway));

        assertEquals("consumer-1", result);
    }

    @Test
    void createConsumerShouldRethrowTimeoutWhenConsumerStateDoesNotMatch() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        ApiKeyConfig.ApiKeyCredential apiKeyCredential = new ApiKeyConfig.ApiKeyCredential();
        apiKeyCredential.setApiKey("apikey-1");

        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setSource("BEARER");
        apiKeyConfig.setCredentials(List.of(apiKeyCredential));

        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");

        ConsumerCredential credential = new ConsumerCredential();
        credential.setApiKeyConfig(apiKeyConfig);

        HigressOperator.HigressConsumerConfig expected =
                operator.buildHigressConsumer("consumer-1", credential);

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/consumers"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(expected),
                        org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/consumers/consumer-1"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressConsumer>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressConsumer(
                                        "consumer-1", List.of(Map.of("type", "hmac-auth")))));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        assertThrows(
                RuntimeException.class,
                () ->
                        operator.createConsumer(
                                consumer, credential, gatewayConfigWithGateway(gateway)));
    }

    @Test
    void authorizeConsumerShouldTreatRouteUpdateTimeoutAsSuccessWhenStatePersisted() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        HigressRefConfig refConfig = new HigressRefConfig();
        refConfig.setModelRouteName("model-route");

        HigressOperator.HigressAIRoute beforeUpdate = new HigressOperator.HigressAIRoute();
        beforeUpdate.setName("model-route");
        beforeUpdate.setAuthConfig(
                new HigressOperator.RouteAuthConfig(true, List.of("key-auth"), new ArrayList<>()));

        HigressOperator.HigressAIRoute afterUpdate = new HigressOperator.HigressAIRoute();
        afterUpdate.setName("model-route");
        afterUpdate.setAuthConfig(
                new HigressOperator.RouteAuthConfig(
                        true, List.of("key-auth"), new ArrayList<>(List.of("consumer-1"))));

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/ai/routes/model-route"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressAIRoute>>>
                                        any()))
                .thenReturn(new HigressOperator.HigressResponse<>(beforeUpdate))
                .thenReturn(new HigressOperator.HigressResponse<>(afterUpdate));
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/ai/routes/model-route"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.PUT),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(HigressOperator.HigressAIRoute.class),
                        org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(timeoutException());

        TestableHigressOperator operator = new TestableHigressOperator(client);

        ConsumerAuthConfig authConfig =
                operator.authorizeConsumer(gateway, "consumer-1", refConfig);

        assertEquals("MODEL_API", authConfig.getHigressAuthConfig().getResourceType());
        assertEquals("model-route", authConfig.getHigressAuthConfig().getResourceName());
    }

    @Test
    void authorizeConsumerShouldTreatMcpAuthorizationTimeoutAsSuccessWhenStatePersisted() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        HigressRefConfig refConfig = new HigressRefConfig();
        refConfig.setMcpServerName("hm-mcp-key");

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/consumers/"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.PUT),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(
                                HigressOperator.HigressAuthConsumerConfig.class),
                        org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/hm-mcp-key"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressMCPConfig>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressMCPConfig(
                                        "hm-mcp-key",
                                        "DIRECT_ROUTE",
                                        List.of(),
                                        null,
                                        new HigressOperator.DirectRouteConfig(
                                                "/hm-mcp-key", "streamable"),
                                        new HigressOperator.HigressConsumerAuthInfo(
                                                "key-auth",
                                                true,
                                                new ArrayList<>(List.of("consumer-1"))))));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        ConsumerAuthConfig authConfig =
                operator.authorizeConsumer(gateway, "consumer-1", refConfig);

        assertEquals("MCP_SERVER", authConfig.getHigressAuthConfig().getResourceType());
        assertEquals("hm-mcp-key", authConfig.getHigressAuthConfig().getResourceName());
    }

    @Test
    void authorizeConsumerShouldRethrowMcpAuthorizationTimeoutWhenStateNotPersisted() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        HigressRefConfig refConfig = new HigressRefConfig();
        refConfig.setMcpServerName("hm-mcp-key");

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/consumers/"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.PUT),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(
                                HigressOperator.HigressAuthConsumerConfig.class),
                        org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/hm-mcp-key"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressMCPConfig>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressMCPConfig(
                                        "hm-mcp-key",
                                        "DIRECT_ROUTE",
                                        List.of(),
                                        null,
                                        new HigressOperator.DirectRouteConfig(
                                                "/hm-mcp-key", "streamable"),
                                        new HigressOperator.HigressConsumerAuthInfo(
                                                "key-auth", true, new ArrayList<>()))));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        assertThrows(
                RuntimeException.class,
                () -> operator.authorizeConsumer(gateway, "consumer-1", refConfig));
    }

    @Test
    void revokeConsumerAuthorizationShouldTreatMcpTimeoutAsSuccessWhenStateCleared() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        ConsumerAuthConfig authConfig =
                ConsumerAuthConfig.builder()
                        .higressAuthConfig(
                                HigressAuthConfig.builder()
                                        .resourceType("MCP_SERVER")
                                        .resourceName("hm-mcp-key")
                                        .build())
                        .build();

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/consumers/"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.DELETE),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(
                                HigressOperator.HigressAuthConsumerConfig.class),
                        org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/hm-mcp-key"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressMCPConfig>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressMCPConfig(
                                        "hm-mcp-key",
                                        "DIRECT_ROUTE",
                                        List.of(),
                                        null,
                                        new HigressOperator.DirectRouteConfig(
                                                "/hm-mcp-key", "streamable"),
                                        new HigressOperator.HigressConsumerAuthInfo(
                                                "key-auth",
                                                true,
                                                new ArrayList<>(List.of("consumer-2"))))));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        operator.revokeConsumerAuthorization(gateway, "consumer-1", authConfig);

        verify(client)
                .execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/consumers/"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.DELETE),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(
                                HigressOperator.HigressAuthConsumerConfig.class),
                        org.mockito.ArgumentMatchers.eq(Void.class));
    }

    @Test
    void revokeConsumerAuthorizationShouldRethrowMcpTimeoutWhenStateStillAuthorized() {
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);

        ConsumerAuthConfig authConfig =
                ConsumerAuthConfig.builder()
                        .higressAuthConfig(
                                HigressAuthConfig.builder()
                                        .resourceType("MCP_SERVER")
                                        .resourceName("hm-mcp-key")
                                        .build())
                        .build();

        HigressClient client = mock(HigressClient.class);
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/consumers/"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.DELETE),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(
                                HigressOperator.HigressAuthConsumerConfig.class),
                        org.mockito.ArgumentMatchers.eq(Void.class)))
                .thenThrow(timeoutException());
        when(client.execute(
                        org.mockito.ArgumentMatchers.eq("/v1/mcpServer/hm-mcp-key"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.GET),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers
                                .<ParameterizedTypeReference<
                                                HigressOperator.HigressResponse<
                                                        HigressOperator.HigressMCPConfig>>>
                                        any()))
                .thenReturn(
                        new HigressOperator.HigressResponse<>(
                                new HigressOperator.HigressMCPConfig(
                                        "hm-mcp-key",
                                        "DIRECT_ROUTE",
                                        List.of(),
                                        null,
                                        new HigressOperator.DirectRouteConfig(
                                                "/hm-mcp-key", "streamable"),
                                        new HigressOperator.HigressConsumerAuthInfo(
                                                "key-auth",
                                                true,
                                                new ArrayList<>(List.of("consumer-1"))))));

        TestableHigressOperator operator = new TestableHigressOperator(client);

        assertThrows(
                RuntimeException.class,
                () -> operator.revokeConsumerAuthorization(gateway, "consumer-1", authConfig));
    }

    private static RuntimeException timeoutException() {
        return new RuntimeException(
                "Failed to execute Higress request",
                new ResourceAccessException("Request timed out"));
    }

    private static com.alibaba.himarket.support.gateway.GatewayConfig gatewayConfigWithGateway(
            Gateway gateway) {
        return com.alibaba.himarket.support.gateway.GatewayConfig.builder()
                .gatewayType(GatewayType.HIGRESS)
                .higressConfig(gateway.getHigressConfig())
                .gateway(gateway)
                .build();
    }

    private static class TestableHigressOperator extends HigressOperator {

        private final HigressClient client;

        private TestableHigressOperator(HigressClient client) {
            super(mock(ToolManager.class));
            this.client = client;
        }

        @Override
        protected HigressClient getClient(Gateway gateway) {
            return client;
        }
    }
}
