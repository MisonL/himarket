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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapBuilder;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.higress.sdk.model.route.KeyedRoutePredicate;
import com.alibaba.higress.sdk.model.route.RoutePredicate;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.HigressMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.OpenAPIMCPConfig;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.HigressModelResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.HigressClient;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.HigressAuthConfig;
import com.alibaba.himarket.support.consumer.HmacConfig;
import com.alibaba.himarket.support.consumer.JwtConfig;
import com.alibaba.himarket.support.enums.ConsumerCredentialType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.gateway.HigressConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@Slf4j
@RequiredArgsConstructor
public class HigressOperator extends GatewayOperator<HigressClient> {

    private final ToolManager toolManager;

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Higress gateway does not support HTTP APIs");
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Higress gateway does not support REST APIs");
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        HigressClient client = getClient(gateway);

        Map<String, String> queryParams =
                MapBuilder.<String, String>create()
                        .put("pageNum", String.valueOf(page))
                        .put("pageSize", String.valueOf(size))
                        .build();

        HigressPageResponse<HigressMCPConfig> response =
                client.execute(
                        "/v1/mcpServer",
                        HttpMethod.GET,
                        queryParams,
                        null,
                        new ParameterizedTypeReference<HigressPageResponse<HigressMCPConfig>>() {});

        List<HigressMCPServerResult> mcpServers =
                response.getData().stream()
                        .map(s -> new HigressMCPServerResult().convertFrom(s))
                        .collect(Collectors.toList());

        return PageResult.of(mcpServers, page, size, response.getTotal());
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size) {
        HigressClient client = getClient(gateway);

        Map<String, String> queryParams =
                MapBuilder.<String, String>create()
                        .put("pageNum", String.valueOf(page))
                        .put("pageSize", String.valueOf(size))
                        .build();

        try {
            HigressPageResponse<HigressAIRoute> response =
                    client.execute(
                            "/v1/ai/routes",
                            HttpMethod.GET,
                            queryParams,
                            null,
                            new ParameterizedTypeReference<
                                    HigressPageResponse<HigressAIRoute>>() {});

            List<HigressModelResult> modelAPIs =
                    response.getData().stream()
                            .map(
                                    config ->
                                            HigressModelResult.builder()
                                                    .modelRouteName(config.getName())
                                                    .build())
                            .collect(Collectors.toList());

            return PageResult.of(modelAPIs, page, size, response.getTotal());
        } catch (Exception e) {
            log.warn("Failed to fetch model APIs from Higress, returning empty result", e);
            return PageResult.of(Collections.emptyList(), page, size, 0);
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        throw new UnsupportedOperationException(
                "Higress gateway does not support fetching API config");
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        HigressClient client = getClient(gateway);
        HigressRefConfig config = (HigressRefConfig) conf;

        HigressResponse<HigressMCPConfig> response =
                client.execute(
                        "/v1/mcpServer/" + config.getMcpServerName(),
                        HttpMethod.GET,
                        null,
                        null,
                        new ParameterizedTypeReference<HigressResponse<HigressMCPConfig>>() {});

        MCPConfigResult m = new MCPConfigResult();
        HigressMCPConfig higressMCPConfig = response.getData();
        m.setMcpServerName(higressMCPConfig.getName());

        // mcpServer config
        MCPConfigResult.MCPServerConfig c = new MCPConfigResult.MCPServerConfig();

        boolean isDirect = "direct_route".equalsIgnoreCase(higressMCPConfig.getType());
        DirectRouteConfig directRouteConfig = higressMCPConfig.getDirectRouteConfig();
        String transportType =
                (isDirect && directRouteConfig != null)
                        ? directRouteConfig.getTransportType()
                        : null;

        // Standardized path format for Higress MCP servers: /mcp-servers/{name}
        String path = "/mcp-servers/" + higressMCPConfig.getName();
        if ("SSE".equalsIgnoreCase(transportType)) {
            path += "/sse";
        }
        c.setPath(path);

        List<String> domains = higressMCPConfig.getDomains();
        if (CollUtil.isEmpty(domains)) {
            // If no domain is specified, use the first gateway IP as the domain
            List<DomainResult> domainResults = fetchDefaultDomains(gateway);
            c.setDomains(domainResults);
        } else {
            c.setDomains(
                    domains.stream()
                            .map(
                                    domain -> {
                                        HigressDomainConfig domainConfig =
                                                fetchDomain(gateway, domain);
                                        String protocol =
                                                (domainConfig == null
                                                                || "off"
                                                                        .equalsIgnoreCase(
                                                                                domainConfig
                                                                                        .getEnableHttps()))
                                                        ? "http"
                                                        : "https";
                                        return DomainResult.builder()
                                                .domain(domain)
                                                .protocol(protocol)
                                                .build();
                                    })
                            .collect(Collectors.toList()));
        }

        m.setMcpServerConfig(c);

        // tools
        m.setTools(higressMCPConfig.getRawConfigurations());
        m.setRequiredCredentialType(
                resolveRequiredCredentialType(higressMCPConfig.getConsumerAuthInfo()));

        // meta
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.HIGRESS.name());
        meta.setCreateFromType(higressMCPConfig.getType());
        meta.setProtocol(
                (StrUtil.isBlank(transportType) || "SSE".equalsIgnoreCase(transportType))
                        ? "SSE"
                        : "HTTP");
        m.setMeta(meta);

        return JSONUtil.toJsonStr(m);
    }

    private List<DomainResult> fetchDefaultDomains(Gateway gateway) {
        List<URI> gatewayUris = fetchGatewayUris(gateway);
        DomainResult domainResult =
                Optional.ofNullable(gatewayUris)
                        .filter(CollUtil::isNotEmpty)
                        .map(uris -> uris.get(0))
                        .map(
                                uri ->
                                        DomainResult.builder()
                                                .domain(uri.getHost())
                                                .protocol(uri.getScheme())
                                                .port(uri.getPort() == -1 ? null : uri.getPort())
                                                .build())
                        .orElse(
                                DomainResult.builder()
                                        .domain("<higress-gateway-ip>")
                                        .protocol("http")
                                        .build());
        return Collections.singletonList(domainResult);
    }

    private HigressDomainConfig fetchDomain(Gateway gateway, String domain) {
        HigressClient client = getClient(gateway);
        HigressResponse<HigressDomainConfig> response =
                client.execute(
                        "/v1/domains/" + domain,
                        HttpMethod.GET,
                        null,
                        null,
                        new ParameterizedTypeReference<HigressResponse<HigressDomainConfig>>() {});
        return response.getData();
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        return "";
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        HigressRefConfig higressRefConfig = (HigressRefConfig) conf;
        HigressAIRoute aiRoute = fetchAIRoute(gateway, higressRefConfig.getModelRouteName());

        List<DomainResult> domains;
        if (CollUtil.isEmpty(aiRoute.getDomains())) {
            // Use gateway IP as domain
            domains = fetchDefaultDomains(gateway);
        } else {
            domains =
                    aiRoute.getDomains().stream()
                            .map(
                                    domain ->
                                            DomainResult.builder()
                                                    .domain(domain)
                                                    .protocol(
                                                            Optional.ofNullable(
                                                                            fetchDomain(
                                                                                    gateway,
                                                                                    domain))
                                                                    .map(
                                                                            HigressDomainConfig
                                                                                    ::getEnableHttps)
                                                                    .map(String::toLowerCase)
                                                                    .filter("off"::equals)
                                                                    .map(s -> "http")
                                                                    .orElse("https"))
                                                    .build())
                            .toList();
        }

        // AI route
        List<HttpRouteResult> routeResults =
                Collections.singletonList(new HttpRouteResult().convertFrom(aiRoute, domains));

        ModelConfigResult.ModelAPIConfig config =
                ModelConfigResult.ModelAPIConfig.builder()
                        // Default value
                        .aiProtocols(List.of("OpenAI/V1"))
                        .modelCategory("Text")
                        .routes(routeResults)
                        .build();

        ModelConfigResult result = new ModelConfigResult();
        result.setModelAPIConfig(config);
        result.setRequiredCredentialType(resolveAllowedCredentialType(aiRoute.getAuthConfig()));

        return JSONUtil.toJsonStr(result);
    }

    @Override
    public String fetchMcpToolsForConfig(Gateway gateway, Object conf) {
        HigressClient client = getClient(gateway);
        MCPConfigResult config = (MCPConfigResult) conf;

        // Fetch MCP server configuration
        HigressMCPConfig higressMCPConfig =
                client.execute(
                                "/v1/mcpServer/" + config.getMcpServerName(),
                                HttpMethod.GET,
                                null,
                                null,
                                new ParameterizedTypeReference<
                                        HigressResponse<HigressMCPConfig>>() {})
                        .getData();

        // Only 'direct_route' is supported
        if (!"direct_route".equalsIgnoreCase(higressMCPConfig.getType())) {
            return null;
        }

        // Build authentication context
        CredentialContext credentialContext = CredentialContext.builder().build();
        if (config.getRequiredCredentialType() != null
                && !config.getRequiredCredentialType().isApiKeyCompatible()) {
            log.info(
                    "Skip MCP tool fetch for server {} because required credential type is {}",
                    config.getMcpServerName(),
                    config.getRequiredCredentialType());
            return null;
        }

        Optional.ofNullable(higressMCPConfig.getConsumerAuthInfo())
                .filter(authInfo -> BooleanUtil.isTrue(authInfo.getEnable()))
                .map(HigressConsumerAuthInfo::getAllowedConsumers)
                .filter(CollUtil::isNotEmpty)
                .map(CollUtil::getFirst)
                .ifPresent(
                        consumer -> {
                            HigressConsumer higressConsumer =
                                    client.execute(
                                                    "/v1/consumers/" + consumer,
                                                    HttpMethod.GET,
                                                    null,
                                                    null,
                                                    new ParameterizedTypeReference<
                                                            HigressResponse<HigressConsumer>>() {})
                                            .getData();

                            Optional.ofNullable(higressConsumer.getCredentials())
                                    .filter(CollUtil::isNotEmpty)
                                    .map(CollUtil::getFirst)
                                    .ifPresent(
                                            credential ->
                                                    fillCredentialContext(
                                                            credentialContext, credential));
                        });

        MCPTransportConfig transportConfig = config.toTransportConfig();
        transportConfig.setHeaders(credentialContext.copyHeaders());
        transportConfig.setQueryParams(credentialContext.copyQueryParams());

        McpClientWrapper mcpClientWrapper = toolManager.getOrCreateClient(transportConfig);
        if (mcpClientWrapper == null) {
            return null;
        }

        // Get and transform tool list
        List<McpSchema.Tool> tools = mcpClientWrapper.listTools().block();
        OpenAPIMCPConfig openAPIMCPConfig =
                OpenAPIMCPConfig.convertFromToolList(config.getMcpServerName(), tools);

        return JSONUtil.toJsonStr(openAPIMCPConfig);
    }

    private void fillCredentialContext(CredentialContext context, Map<String, Object> credential) {
        if (credential == null) {
            return;
        }

        String type = Objects.toString(credential.get("type"), null);
        if (!"key-auth".equalsIgnoreCase(type)) {
            log.debug("Skip unsupported MCP preview credential type: {}", type);
            return;
        }

        List<String> values = toStringList(credential.get("values"));
        String apiKey = CollUtil.getFirst(values);
        if (StrUtil.isBlank(apiKey)) {
            return;
        }

        String source = Objects.toString(credential.get("source"), null);
        String key = Objects.toString(credential.get("key"), null);
        if (StrUtil.isBlank(source)) {
            source = "BEARER";
        }
        if (StrUtil.isBlank(key)) {
            key = "Authorization";
        }

        switch (source.toUpperCase()) {
            case "BEARER" -> context.getHeaders().put("Authorization", "Bearer " + apiKey);
            case "QUERY" -> context.getQueryParams().put(key, apiKey);
            default -> context.getHeaders().put(key, apiKey);
        }
    }

    private List<String> toStringList(Object rawValues) {
        if (!(rawValues instanceof List<?> values)) {
            return Collections.emptyList();
        }
        return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        throw new UnsupportedOperationException(
                "Higress gateway does not support fetching Gateways");
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        Gateway gateway = resolveGateway(config);
        HigressClient client = getClient(gateway);
        HigressConsumerConfig payload = buildHigressConsumer(consumer.getConsumerId(), credential);

        runWithTimeoutReconcile(
                "create consumer " + consumer.getConsumerId(),
                () -> client.execute("/v1/consumers", HttpMethod.POST, null, payload, String.class),
                () -> doesConsumerMatch(client, consumer.getConsumerId(), payload));

        return consumer.getConsumerId();
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        Gateway gateway = resolveGateway(config);
        HigressClient client = getClient(gateway);
        HigressConsumerConfig payload = buildHigressConsumer(consumerId, credential);

        runWithTimeoutReconcile(
                "update consumer " + consumerId,
                () ->
                        client.execute(
                                "/v1/consumers/" + consumerId,
                                HttpMethod.PUT,
                                null,
                                payload,
                                String.class),
                () -> doesConsumerMatch(client, consumerId, payload));
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        Gateway gateway = resolveGateway(config);
        HigressClient client = getClient(gateway);

        runWithTimeoutReconcile(
                "delete consumer " + consumerId,
                () ->
                        client.execute(
                                "/v1/consumers/" + consumerId,
                                HttpMethod.DELETE,
                                null,
                                null,
                                String.class),
                () -> !isConsumerPresent(client, consumerId));
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        HigressClient client = getClient(resolveGateway(config));
        try {
            client.execute("/v1/consumers/" + consumerId, HttpMethod.GET, null, null, String.class);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig) {
        HigressRefConfig config = (HigressRefConfig) refConfig;

        String mcpServerName = config.getMcpServerName();
        String modelRouteName = config.getModelRouteName();

        // MCP or AIRoute
        return StrUtil.isNotBlank(mcpServerName)
                ? authorizeMCPServer(gateway, consumerId, mcpServerName)
                : authorizeAIRoute(gateway, consumerId, modelRouteName);
    }

    private ConsumerAuthConfig authorizeMCPServer(
            Gateway gateway, String consumerId, String mcpServerName) {
        HigressClient client = getClient(gateway);
        HigressAuthConsumerConfig payload = buildAuthHigressConsumer(mcpServerName, consumerId);

        runWithTimeoutReconcile(
                "authorize mcp server " + mcpServerName + " for consumer " + consumerId,
                () ->
                        client.execute(
                                "/v1/mcpServer/consumers/",
                                HttpMethod.PUT,
                                null,
                                payload,
                                Void.class),
                () -> isMCPServerAuthorized(gateway, mcpServerName, consumerId));

        HigressAuthConfig higressAuthConfig =
                HigressAuthConfig.builder()
                        .resourceType("MCP_SERVER")
                        .resourceName(mcpServerName)
                        .build();

        return ConsumerAuthConfig.builder().higressAuthConfig(higressAuthConfig).build();
    }

    private ConsumerAuthConfig authorizeAIRoute(
            Gateway gateway, String consumerId, String modelRouteName) {
        HigressAIRoute aiRoute = fetchAIRoute(gateway, modelRouteName);

        if (aiRoute.getAuthConfig() == null) {
            aiRoute.setAuthConfig(new RouteAuthConfig());
        }

        RouteAuthConfig authConfig = aiRoute.getAuthConfig();
        List<String> allowedConsumers = authConfig.getAllowedConsumers();
        // Add consumer only if not exists
        if (!CollUtil.contains(allowedConsumers, consumerId)) {
            allowedConsumers.add(consumerId);
            updateAIRoute(
                    gateway,
                    aiRoute,
                    () -> isAIRouteAuthorized(gateway, modelRouteName, consumerId));
        }

        HigressAuthConfig higressAuthConfig =
                HigressAuthConfig.builder()
                        .resourceType("MODEL_API")
                        .resourceName(modelRouteName)
                        .build();

        return ConsumerAuthConfig.builder().higressAuthConfig(higressAuthConfig).build();
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        HigressClient client = getClient(gateway);

        HigressAuthConfig higressAuthConfig = authConfig.getHigressAuthConfig();
        if (higressAuthConfig == null) {
            return;
        }

        if ("MCP_SERVER".equalsIgnoreCase(higressAuthConfig.getResourceType())) {
            String resourceName = higressAuthConfig.getResourceName();
            HigressAuthConsumerConfig payload = buildAuthHigressConsumer(resourceName, consumerId);
            runWithTimeoutReconcile(
                    "revoke mcp server " + resourceName + " for consumer " + consumerId,
                    () ->
                            client.execute(
                                    "/v1/mcpServer/consumers/",
                                    HttpMethod.DELETE,
                                    null,
                                    payload,
                                    Void.class),
                    () -> !isMCPServerAuthorized(gateway, resourceName, consumerId));
        } else {
            HigressAIRoute aiRoute = fetchAIRoute(gateway, higressAuthConfig.getResourceName());
            RouteAuthConfig aiRouteAuthConfig = aiRoute.getAuthConfig();

            if (aiRouteAuthConfig == null
                    || CollUtil.isEmpty(aiRouteAuthConfig.getAllowedConsumers())) {
                return;
            }

            aiRouteAuthConfig.getAllowedConsumers().remove(consumerId);
            updateAIRoute(
                    gateway,
                    aiRoute,
                    () ->
                            !isAIRouteAuthorized(
                                    gateway, higressAuthConfig.getResourceName(), consumerId));
        }
    }

    private HigressMCPConfig fetchMCPServer(Gateway gateway, String mcpServerName) {
        HigressClient client = getClient(gateway);
        HigressResponse<HigressMCPConfig> response =
                client.execute(
                        "/v1/mcpServer/" + mcpServerName,
                        HttpMethod.GET,
                        null,
                        null,
                        new ParameterizedTypeReference<>() {});
        return response.getData();
    }

    private HigressAIRoute fetchAIRoute(Gateway gateway, String modelRouteName) {
        HigressClient client = getClient(gateway);

        HigressResponse<HigressAIRoute> response =
                client.execute(
                        "/v1/ai/routes/" + modelRouteName,
                        HttpMethod.GET,
                        null,
                        null,
                        new ParameterizedTypeReference<>() {});

        return response.getData();
    }

    private void updateAIRoute(
            Gateway gateway, HigressAIRoute aiRoute, BooleanSupplier reconciler) {
        HigressClient client = getClient(gateway);

        runWithTimeoutReconcile(
                "update ai route " + aiRoute.getName(),
                () ->
                        client.execute(
                                "/v1/ai/routes/" + aiRoute.getName(),
                                HttpMethod.PUT,
                                null,
                                aiRoute,
                                Void.class),
                reconciler);
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Higress gateway does not support fetching API");
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.HIGRESS;
    }

    private Gateway resolveGateway(GatewayConfig config) {
        if (config.getGateway() != null) {
            return config.getGateway();
        }
        Gateway gateway = new Gateway();
        gateway.setGatewayType(GatewayType.HIGRESS);
        gateway.setHigressConfig(config.getHigressConfig());
        return gateway;
    }

    private void runWithTimeoutReconcile(
            String action, Runnable operation, BooleanSupplier reconciler) {
        try {
            operation.run();
        } catch (RuntimeException e) {
            if (!isTimeoutException(e) || !reconciler.getAsBoolean()) {
                throw e;
            }
            log.warn("Higress {} timed out, but reconciliation confirmed success", action);
        }
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean doesConsumerMatch(
            HigressClient client, String consumerId, HigressConsumerConfig expected) {
        try {
            HigressResponse<HigressConsumer> response =
                    client.execute(
                            "/v1/consumers/" + consumerId,
                            HttpMethod.GET,
                            null,
                            null,
                            new ParameterizedTypeReference<HigressResponse<HigressConsumer>>() {});
            HigressConsumer actual = response.getData();
            return actual != null
                    && StrUtil.equals(actual.getName(), expected.getName())
                    && Objects.equals(
                            normalizeStructure(actual.getCredentials()),
                            normalizeStructure(expected.getCredentials()));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private boolean isConsumerPresent(HigressClient client, String consumerId) {
        try {
            client.execute("/v1/consumers/" + consumerId, HttpMethod.GET, null, null, String.class);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private boolean isAIRouteAuthorized(Gateway gateway, String modelRouteName, String consumerId) {
        HigressAIRoute route = fetchAIRoute(gateway, modelRouteName);
        return Optional.ofNullable(route.getAuthConfig())
                .map(RouteAuthConfig::getAllowedConsumers)
                .map(consumers -> CollUtil.contains(consumers, consumerId))
                .orElse(false);
    }

    private boolean isMCPServerAuthorized(
            Gateway gateway, String mcpServerName, String consumerId) {
        HigressMCPConfig mcpServer = fetchMCPServer(gateway, mcpServerName);
        return Optional.ofNullable(mcpServer.getConsumerAuthInfo())
                .map(HigressConsumerAuthInfo::getAllowedConsumers)
                .map(consumers -> CollUtil.contains(consumers, consumerId))
                .orElse(false);
    }

    private Object normalizeStructure(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new TreeMap<>();
            mapValue.forEach(
                    (key, item) -> normalized.put(String.valueOf(key), normalizeStructure(item)));
            return normalized;
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(this::normalizeStructure)
                    .sorted(Comparator.comparing(JSONUtil::toJsonStr))
                    .toList();
        }
        return value;
    }

    @Override
    public List<URI> fetchGatewayUris(Gateway gateway) {
        String address =
                Optional.ofNullable(gateway.getHigressConfig())
                        .map(HigressConfig::getGatewayAddress)
                        .filter(StrUtil::isNotBlank)
                        .orElse(null);

        if (StrUtil.isBlank(address)) {
            return Collections.emptyList();
        }

        try {
            URI uri = new URI(address);

            // If no scheme (protocol) specified, add default http://
            if (uri.getScheme() == null) {
                uri = new URI("http://" + address);
            }

            return Collections.singletonList(uri);
        } catch (URISyntaxException e) {
            log.warn("Invalid gateway address: {}, error: {}", address, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressConsumerConfig {
        private String name;
        private List<Map<String, Object>> credentials;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressCredentialConfig {
        private String type;
        private String source;
        private String key;
        private List<String> values;
    }

    public HigressConsumerConfig buildHigressConsumer(
            String consumerId, ConsumerCredential credential) {
        return switch (resolveCredentialType(credential)) {
            case API_KEY ->
                    HigressConsumerConfig.builder()
                            .name(consumerId)
                            .credentials(buildApiKeyCredentials(credential.getApiKeyConfig()))
                            .build();
            case HMAC ->
                    HigressConsumerConfig.builder()
                            .name(consumerId)
                            .credentials(buildHmacCredentials(credential.getHmacConfig()))
                            .build();
            case JWT ->
                    HigressConsumerConfig.builder()
                            .name(consumerId)
                            .credentials(
                                    Collections.singletonList(
                                            buildJwtCredential(credential.getJwtConfig())))
                            .build();
        };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressMCPConfig {
        private String name;
        private String type;
        private List<String> domains;
        private String rawConfigurations;
        private DirectRouteConfig directRouteConfig;
        private HigressConsumerAuthInfo consumerAuthInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectRouteConfig {
        private String path;
        private String transportType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressConsumerAuthInfo {
        private String type;
        private Boolean enable;
        private List<String> allowedConsumers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressConsumer {
        private String name;
        private List<Map<String, Object>> credentials;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressPageResponse<T> {
        private List<T> data;
        private int total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressResponse<T> {
        private T data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressDomainConfig {
        private String name;
        private String enableHttps;
    }

    // AI route definition start

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressAIRoute {
        private String name;
        private String version;
        private List<String> domains;
        private RoutePredicate pathPredicate;
        private List<KeyedRoutePredicate> headerPredicates;
        private List<KeyedRoutePredicate> urlParamPredicates;
        private List<AiUpstream> upstreams;
        private List<AiModelPredicate> modelPredicates;
        private RouteAuthConfig authConfig;
        private AiRouteFallbackConfig fallbackConfig;
    }

    public static class AiModelPredicate extends RoutePredicate {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiUpstream {
        private String provider;
        private Integer weight;
        private Map<String, String> modelMapping;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteAuthConfig {
        private Boolean enabled;
        private List<String> allowedCredentialTypes;
        private List<String> allowedConsumers = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiRouteFallbackConfig {
        private Boolean enabled;
        private List<AiUpstream> upstreams;
        private String fallbackStrategy;
        private List<String> responseCodes;
    }

    // AI route definition end

    public HigressAuthConsumerConfig buildAuthHigressConsumer(
            String gatewayName, String consumerId) {
        return HigressAuthConsumerConfig.builder()
                .mcpServerName(gatewayName)
                .consumers(Collections.singletonList(consumerId))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HigressAuthConsumerConfig {
        private String mcpServerName;
        private List<String> consumers;
    }

    private String mapSource(String source) {
        if (StringUtils.isBlank(source)) return null;
        if ("Default".equalsIgnoreCase(source)) return "BEARER";
        if ("HEADER".equalsIgnoreCase(source)) return "HEADER";
        if ("QueryString".equalsIgnoreCase(source)) return "QUERY";
        return source;
    }

    private ConsumerCredentialType resolveRequiredCredentialType(HigressConsumerAuthInfo authInfo) {
        if (authInfo == null || !BooleanUtil.isTrue(authInfo.getEnable())) {
            return null;
        }
        return ConsumerCredentialType.fromHigressAuthType(authInfo.getType());
    }

    private ConsumerCredentialType resolveAllowedCredentialType(RouteAuthConfig authConfig) {
        if (authConfig == null || !BooleanUtil.isTrue(authConfig.getEnabled())) {
            return null;
        }

        List<String> allowedCredentialTypes = authConfig.getAllowedCredentialTypes();
        if (CollUtil.isEmpty(allowedCredentialTypes)) {
            return null;
        }

        EnumSet<ConsumerCredentialType> resolvedTypes =
                allowedCredentialTypes.stream()
                        .filter(StrUtil::isNotBlank)
                        .map(ConsumerCredentialType::fromHigressAuthType)
                        .collect(
                                Collectors.toCollection(
                                        () -> EnumSet.noneOf(ConsumerCredentialType.class)));

        if (resolvedTypes.size() != 1) {
            return null;
        }

        return resolvedTypes.iterator().next();
    }

    private ConsumerCredentialType resolveCredentialType(ConsumerCredential credential) {
        EnumSet<ConsumerCredentialType> configuredTypes =
                EnumSet.noneOf(ConsumerCredentialType.class);
        if (credential.getApiKeyConfig() != null) {
            configuredTypes.add(ConsumerCredentialType.API_KEY);
        }
        if (credential.getHmacConfig() != null) {
            configuredTypes.add(ConsumerCredentialType.HMAC);
        }
        if (credential.getJwtConfig() != null) {
            configuredTypes.add(ConsumerCredentialType.JWT);
        }
        if (configuredTypes.isEmpty()) {
            throw new IllegalArgumentException("Consumer credential config is empty");
        }
        if (configuredTypes.size() > 1) {
            throw new IllegalArgumentException(
                    "Consumer credential has multiple auth configs, which is not supported");
        }
        return configuredTypes.iterator().next();
    }

    private List<Map<String, Object>> buildApiKeyCredentials(ApiKeyConfig apiKeyConfig) {
        String source = mapSource(apiKeyConfig.getSource());
        List<String> apiKeys =
                apiKeyConfig.getCredentials().stream()
                        .map(ApiKeyConfig.ApiKeyCredential::getApiKey)
                        .collect(Collectors.toList());

        return Collections.singletonList(
                MapBuilder.<String, Object>create()
                        .put("type", "key-auth")
                        .put("source", source)
                        .put("key", apiKeyConfig.getKey())
                        .put("values", apiKeys)
                        .build());
    }

    private List<Map<String, Object>> buildHmacCredentials(HmacConfig hmacConfig) {
        return hmacConfig.getCredentials().stream()
                .map(
                        credential ->
                                MapBuilder.<String, Object>create()
                                        .put("type", "hmac-auth")
                                        .put("access_key", credential.getAk())
                                        .put("secret_key", credential.getSk())
                                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildJwtCredential(JwtConfig jwtConfig) {
        MapBuilder<String, Object> builder =
                MapBuilder.<String, Object>create()
                        .put("type", "jwt-auth")
                        .put("issuer", jwtConfig.getIssuer())
                        .put("jwks", jwtConfig.getJwks())
                        .put("clock_skew_seconds", jwtConfig.getClockSkewSeconds())
                        .put("keep_token", jwtConfig.getKeepToken());

        if (CollUtil.isNotEmpty(jwtConfig.getClaimsToHeaders())) {
            builder.put(
                    "claims_to_headers",
                    jwtConfig.getClaimsToHeaders().stream()
                            .map(
                                    item ->
                                            MapBuilder.<String, Object>create()
                                                    .put("claim", item.getClaim())
                                                    .put("header", item.getHeader())
                                                    .put("override", item.getOverride())
                                                    .build())
                            .collect(Collectors.toList()));
        }
        if (CollUtil.isNotEmpty(jwtConfig.getFromHeaders())) {
            builder.put(
                    "from_headers",
                    jwtConfig.getFromHeaders().stream()
                            .map(
                                    item ->
                                            MapBuilder.<String, Object>create()
                                                    .put("name", item.getName())
                                                    .put("value_prefix", item.getValuePrefix())
                                                    .build())
                            .collect(Collectors.toList()));
        }
        if (CollUtil.isNotEmpty(jwtConfig.getFromParams())) {
            builder.put("from_params", jwtConfig.getFromParams());
        }
        if (CollUtil.isNotEmpty(jwtConfig.getFromCookies())) {
            builder.put("from_cookies", jwtConfig.getFromCookies());
        }
        return builder.build();
    }
}
