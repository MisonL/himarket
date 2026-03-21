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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductPublication;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.enums.ConsumerCredentialType;
import com.alibaba.himarket.support.enums.ProductType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ContextHolder contextHolder;

    @Mock private PortalService portalService;

    @Mock private GatewayService gatewayService;

    @Mock private ProductRepository productRepository;

    @Mock private ProductRefRepository productRefRepository;

    @Mock private ProductPublicationRepository publicationRepository;

    @Mock private SubscriptionRepository subscriptionRepository;

    @Mock private ConsumerRepository consumerRepository;

    @Mock private NacosService nacosService;

    @Mock private ProductCategoryService productCategoryService;

    @Mock private ToolManager toolManager;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService =
                new ProductServiceImpl(
                        contextHolder,
                        portalService,
                        gatewayService,
                        productRepository,
                        productRefRepository,
                        publicationRepository,
                        subscriptionRepository,
                        consumerRepository,
                        nacosService,
                        productCategoryService,
                        toolManager);
    }

    @Test
    void getProductsShouldExposeModelCredentialRequirementEvenWhenConsumerAuthDisabled() {
        Product product = new Product();
        product.setProductId("product-1");
        product.setType(ProductType.MODEL_API);
        product.setEnableConsumerAuth(false);

        ModelConfigResult modelConfig = new ModelConfigResult();
        modelConfig.setRequiredCredentialType(ConsumerCredentialType.API_KEY);

        ProductRef productRef = new ProductRef();
        productRef.setProductId("product-1");
        productRef.setModelConfig(JSONUtil.toJsonStr(modelConfig));

        when(productRepository.findByProductIdIn(List.of("product-1")))
                .thenReturn(List.of(product));
        when(productRefRepository.findByProductIdIn(List.of("product-1")))
                .thenReturn(List.of(productRef));
        when(productCategoryService.listCategoriesForProducts(List.of("product-1")))
                .thenReturn(Map.of());

        ProductResult result = productService.getProducts(List.of("product-1")).get("product-1");

        assertEquals(ConsumerCredentialType.API_KEY, result.getRequiredCredentialType());
    }

    @Test
    void getProductsShouldExposeMcpCredentialRequirementEvenWhenConsumerAuthDisabled() {
        Product product = new Product();
        product.setProductId("product-2");
        product.setType(ProductType.MCP_SERVER);
        product.setEnableConsumerAuth(false);

        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setRequiredCredentialType(ConsumerCredentialType.API_KEY);

        ProductRef productRef = new ProductRef();
        productRef.setProductId("product-2");
        productRef.setMcpConfig(JSONUtil.toJsonStr(mcpConfig));

        when(productRepository.findByProductIdIn(List.of("product-2")))
                .thenReturn(List.of(product));
        when(productRefRepository.findByProductIdIn(List.of("product-2")))
                .thenReturn(List.of(productRef));
        when(productCategoryService.listCategoriesForProducts(List.of("product-2")))
                .thenReturn(Map.of());

        ProductResult result = productService.getProducts(List.of("product-2")).get("product-2");

        assertEquals(ConsumerCredentialType.API_KEY, result.getRequiredCredentialType());
    }

    @Test
    void listMcpToolsShouldReturnCachedPreviewForAnonymousAccess() {
        Product product = new Product();
        product.setProductId("product-3");
        product.setType(ProductType.MCP_SERVER);

        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setTools(
                """
                {
                  "server": {
                    "name": "hm-mcp-key"
                  },
                  "tools": [
                    {
                      "name": "echo",
                      "description": "Echo the provided message back to the caller.",
                      "args": [
                        {
                          "name": "message",
                          "description": "Message to echo back.",
                          "type": "string",
                          "required": true
                        }
                      ]
                    }
                  ]
                }
                """);

        ProductRef productRef = new ProductRef();
        productRef.setProductId("product-3");
        productRef.setMcpConfig(JSONUtil.toJsonStr(mcpConfig));

        ProductPublication publication = new ProductPublication();
        publication.setProductId("product-3");
        publication.setPortalId("portal-1");

        when(contextHolder.isAdministrator()).thenReturn(false);
        when(contextHolder.isDeveloper()).thenReturn(false);
        when(contextHolder.getPortal()).thenReturn("portal-1");
        when(publicationRepository.findByPortalIdAndProductId("portal-1", "product-3"))
                .thenReturn(java.util.Optional.of(publication));
        when(productRepository.findByProductId("product-3"))
                .thenReturn(java.util.Optional.of(product));
        when(productRefRepository.findByProductIdIn(List.of("product-3")))
                .thenReturn(List.of(productRef));
        when(productCategoryService.listCategoriesForProducts(List.of("product-3")))
                .thenReturn(Map.of());

        McpToolListResult result = productService.listMcpTools("product-3");

        assertNotNull(result.getTools());
        assertEquals(1, result.getTools().size());
        assertEquals("echo", result.getTools().get(0).name());
        assertEquals(List.of("message"), result.getTools().get(0).inputSchema().required());
        assertEquals(
                "string",
                ((Map<?, ?>) result.getTools().get(0).inputSchema().properties().get("message"))
                        .get("type"));
        verifyNoInteractions(toolManager);
    }
}
