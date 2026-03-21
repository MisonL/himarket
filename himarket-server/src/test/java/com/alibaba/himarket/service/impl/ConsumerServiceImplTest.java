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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.consumer.CreateSubscriptionParam;
import com.alibaba.himarket.dto.params.consumer.UpdateCredentialParam;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.repository.ConsumerCredentialRepository;
import com.alibaba.himarket.repository.ConsumerRefRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.HmacConfig;
import com.alibaba.himarket.support.enums.ConsumerCredentialType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SourceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumerServiceImplTest {

    @Mock private PortalService portalService;

    @Mock private ConsumerRepository consumerRepository;

    @Mock private GatewayService gatewayService;

    @Mock private ContextHolder contextHolder;

    @Mock private ConsumerCredentialRepository credentialRepository;

    @Mock private SubscriptionRepository subscriptionRepository;

    @Mock private ProductService productService;

    @Mock private ConsumerRefRepository consumerRefRepository;

    private ConsumerServiceImpl consumerService;

    @BeforeEach
    void setUp() {
        consumerService =
                new ConsumerServiceImpl(
                        portalService,
                        consumerRepository,
                        gatewayService,
                        contextHolder,
                        credentialRepository,
                        subscriptionRepository,
                        productService,
                        consumerRefRepository);
    }

    @Test
    void updateCredentialShouldRejectMultiplePayloads() {
        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId("consumer-1");
        credential.setApiKeyConfig(new ApiKeyConfig());
        when(credentialRepository.findByConsumerId("consumer-1"))
                .thenReturn(Optional.of(credential));

        UpdateCredentialParam param = new UpdateCredentialParam();
        param.setApiKeyConfig(new ApiKeyConfig());
        param.setHmacConfig(new HmacConfig());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.updateCredential("consumer-1", param));

        assertEquals("INVALID_REQUEST", exception.getCode());
        verifyNoInteractions(subscriptionRepository, consumerRefRepository, gatewayService);
    }

    @Test
    void setPrimaryConsumerShouldRejectNonApiKeyCredential() {
        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");

        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId("consumer-1");
        credential.setHmacConfig(new HmacConfig());

        when(contextHolder.getUser()).thenReturn("dev-1");
        when(consumerRepository.findByDeveloperIdAndConsumerId("dev-1", "consumer-1"))
                .thenReturn(Optional.of(consumer));
        when(credentialRepository.findByConsumerId("consumer-1"))
                .thenReturn(Optional.of(credential));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.setPrimaryConsumer("consumer-1"));

        assertEquals("INVALID_REQUEST", exception.getCode());
    }

    @Test
    void subscribeProductShouldRejectIncompatibleCredentialType() {
        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");
        consumer.setPortalId("portal-1");

        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId("consumer-1");
        credential.setHmacConfig(new HmacConfig());

        ProductResult product = new ProductResult();
        product.setProductId("product-1");
        product.setName("Test Product");
        product.setRequiredCredentialType(ConsumerCredentialType.API_KEY);

        ProductRefResult productRef = new ProductRefResult();
        productRef.setGatewayId("gateway-1");
        productRef.setSourceType(SourceType.GATEWAY);

        when(contextHolder.isDeveloper()).thenReturn(true);
        when(contextHolder.getUser()).thenReturn("dev-1");
        when(consumerRepository.findByDeveloperIdAndConsumerId("dev-1", "consumer-1"))
                .thenReturn(Optional.of(consumer));
        when(subscriptionRepository.findByConsumerIdAndProductId("consumer-1", "product-1"))
                .thenReturn(Optional.empty());
        when(productService.getProduct("product-1")).thenReturn(product);
        when(productService.getProductRef("product-1")).thenReturn(productRef);
        when(credentialRepository.findByConsumerId("consumer-1"))
                .thenReturn(Optional.of(credential));

        CreateSubscriptionParam param = new CreateSubscriptionParam();
        param.setProductId("product-1");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.subscribeProduct("consumer-1", param));

        assertEquals("INVALID_REQUEST", exception.getCode());
    }

    @Test
    void subscribeProductShouldRejectNestedCredentialRequirementWhenRootFieldMissing() {
        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");
        consumer.setPortalId("portal-1");

        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId("consumer-1");
        credential.setHmacConfig(new HmacConfig());

        ModelConfigResult modelConfig = new ModelConfigResult();
        modelConfig.setRequiredCredentialType(ConsumerCredentialType.API_KEY);

        ProductResult product = new ProductResult();
        product.setProductId("product-1");
        product.setName("Model Product");
        product.setType(ProductType.MODEL_API);
        product.setModelConfig(modelConfig);

        ProductRefResult productRef = new ProductRefResult();
        productRef.setGatewayId("gateway-1");
        productRef.setSourceType(SourceType.GATEWAY);

        when(contextHolder.isDeveloper()).thenReturn(true);
        when(contextHolder.getUser()).thenReturn("dev-1");
        when(consumerRepository.findByDeveloperIdAndConsumerId("dev-1", "consumer-1"))
                .thenReturn(Optional.of(consumer));
        when(subscriptionRepository.findByConsumerIdAndProductId("consumer-1", "product-1"))
                .thenReturn(Optional.empty());
        when(productService.getProduct("product-1")).thenReturn(product);
        when(productService.getProductRef("product-1")).thenReturn(productRef);
        when(credentialRepository.findByConsumerId("consumer-1"))
                .thenReturn(Optional.of(credential));

        CreateSubscriptionParam param = new CreateSubscriptionParam();
        param.setProductId("product-1");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.subscribeProduct("consumer-1", param));

        assertEquals("INVALID_REQUEST", exception.getCode());
    }

    @Test
    void deleteCredentialShouldBeRejected() {
        when(contextHolder.isDeveloper()).thenReturn(true);
        when(contextHolder.getUser()).thenReturn("dev-1");
        when(consumerRepository.findByDeveloperIdAndConsumerId("dev-1", "consumer-1"))
                .thenReturn(Optional.of(new Consumer()));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.deleteCredential("consumer-1"));

        assertEquals("INVALID_REQUEST", exception.getCode());
        assertTrue(exception.getMessage().contains("cannot be deleted"));
    }

    @Test
    void getPrimaryConsumerShouldRepairLegacyNonApiKeyPrimary() {
        Consumer legacyPrimary = new Consumer();
        legacyPrimary.setConsumerId("consumer-hmac");
        legacyPrimary.setDeveloperId("dev-1");
        legacyPrimary.setIsPrimary(true);

        Consumer apiKeyConsumer = new Consumer();
        apiKeyConsumer.setConsumerId("consumer-apikey");
        apiKeyConsumer.setDeveloperId("dev-1");

        ConsumerCredential hmacCredential = new ConsumerCredential();
        hmacCredential.setConsumerId("consumer-hmac");
        hmacCredential.setHmacConfig(new HmacConfig());

        ConsumerCredential apiKeyCredential = new ConsumerCredential();
        apiKeyCredential.setConsumerId("consumer-apikey");
        apiKeyCredential.setApiKeyConfig(new ApiKeyConfig());

        when(consumerRepository.findByDeveloperIdAndIsPrimary("dev-1", true))
                .thenReturn(Optional.of(legacyPrimary));
        when(credentialRepository.findByConsumerId("consumer-hmac"))
                .thenReturn(Optional.of(hmacCredential));
        when(consumerRepository.findAllByDeveloperId("dev-1"))
                .thenReturn(java.util.List.of(legacyPrimary, apiKeyConsumer));
        when(credentialRepository.findByConsumerIdIn(
                        java.util.List.of("consumer-hmac", "consumer-apikey")))
                .thenReturn(java.util.List.of(hmacCredential, apiKeyCredential));
        when(credentialRepository.findByConsumerId("consumer-apikey"))
                .thenReturn(Optional.of(apiKeyCredential));

        ConsumerResult result = consumerService.getPrimaryConsumer("dev-1");

        assertEquals("consumer-apikey", result.getConsumerId());
        assertEquals(ConsumerCredentialType.API_KEY, result.getCredentialType());
        verify(consumerRepository).clearPrimary("dev-1");
        verify(consumerRepository).save(apiKeyConsumer);
    }

    @Test
    void updateCredentialShouldRejectIncompatibleSubscribedProductWhenRootFieldMissing() {
        Consumer consumer = new Consumer();
        consumer.setConsumerId("consumer-1");
        consumer.setIsPrimary(false);

        ConsumerCredential credential = new ConsumerCredential();
        credential.setConsumerId("consumer-1");
        credential.setApiKeyConfig(new ApiKeyConfig());

        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setRequiredCredentialType(ConsumerCredentialType.API_KEY);

        ProductResult product = new ProductResult();
        product.setProductId("product-1");
        product.setName("MCP Product");
        product.setType(ProductType.MCP_SERVER);
        product.setMcpConfig(mcpConfig);

        ProductSubscription subscription = new ProductSubscription();
        subscription.setConsumerId("consumer-1");
        subscription.setProductId("product-1");

        UpdateCredentialParam param = new UpdateCredentialParam();
        param.setHmacConfig(new HmacConfig());

        when(credentialRepository.findByConsumerId("consumer-1"))
                .thenReturn(Optional.of(credential));
        when(consumerRepository.findByConsumerId("consumer-1")).thenReturn(Optional.of(consumer));
        when(subscriptionRepository.findAllByConsumerId("consumer-1"))
                .thenReturn(List.of(subscription));
        when(productService.getProducts(List.of("product-1")))
                .thenReturn(java.util.Map.of("product-1", product));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> consumerService.updateCredential("consumer-1", param));

        assertEquals("INVALID_REQUEST", exception.getCode());
        verifyNoInteractions(consumerRefRepository, gatewayService);
    }
}
