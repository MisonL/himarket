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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.portal.UpdatePortalParam;
import com.alibaba.himarket.entity.Portal;
import com.alibaba.himarket.repository.PortalDomainRepository;
import com.alibaba.himarket.repository.PortalRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.PortalUiConfig;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalServiceImplTest {

    @Mock private PortalRepository portalRepository;

    @Mock private PortalDomainRepository portalDomainRepository;

    @Mock private SubscriptionRepository subscriptionRepository;

    @Mock private ContextHolder contextHolder;

    @Mock private IdpService idpService;

    @Mock private ProductPublicationRepository publicationRepository;

    @Mock private ProductRepository productRepository;

    private PortalServiceImpl portalService;

    @BeforeEach
    void setUp() {
        portalService =
                new PortalServiceImpl(
                        portalRepository,
                        portalDomainRepository,
                        subscriptionRepository,
                        contextHolder,
                        idpService,
                        publicationRepository,
                        productRepository);
    }

    @Test
    void updatePortalShouldRejectMissingFrontendRedirectUrlWhenCasEnabled() {
        Portal portal = buildPortal();
        UpdatePortalParam param = new UpdatePortalParam();
        param.setPortalSettingConfig(settingWithEnabledCas(null));

        when(portalRepository.findByPortalId("portal-1")).thenReturn(Optional.of(portal));

        assertThrows(BusinessException.class, () -> portalService.updatePortal("portal-1", param));
    }

    @Test
    void updatePortalShouldTrimTrailingSlashFromFrontendRedirectUrl() {
        Portal portal = buildPortal();
        UpdatePortalParam param = new UpdatePortalParam();
        param.setPortalSettingConfig(settingWithEnabledCas("https://portal.example.com/"));

        when(portalRepository.findByPortalId("portal-1")).thenReturn(Optional.of(portal));
        when(portalRepository.saveAndFlush(any(Portal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalDomainRepository.findAllByPortalId("portal-1"))
                .thenReturn(Collections.emptyList());

        portalService.updatePortal("portal-1", param);

        assertEquals(
                "https://portal.example.com",
                portal.getPortalSettingConfig().getFrontendRedirectUrl());
    }

    private Portal buildPortal() {
        Portal portal = new Portal();
        portal.setPortalId("portal-1");
        portal.setName("portal");
        portal.setPortalUiConfig(new PortalUiConfig());
        portal.setPortalSettingConfig(new PortalSettingConfig());
        return portal;
    }

    private PortalSettingConfig settingWithEnabledCas(String frontendRedirectUrl) {
        CasConfig casConfig = new CasConfig();
        casConfig.setProvider("cas");
        casConfig.setName("CAS");
        casConfig.setEnabled(true);
        casConfig.setServerUrl("https://cas.example.com/cas");

        PortalSettingConfig setting = new PortalSettingConfig();
        setting.setBuiltinAuthEnabled(true);
        setting.setCasConfigs(List.of(casConfig));
        setting.setFrontendRedirectUrl(frontendRedirectUrl);
        return setting;
    }
}
