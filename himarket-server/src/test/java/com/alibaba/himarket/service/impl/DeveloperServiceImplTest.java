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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.entity.Developer;
import com.alibaba.himarket.entity.DeveloperExternalIdentity;
import com.alibaba.himarket.repository.DeveloperExternalIdentityRepository;
import com.alibaba.himarket.repository.DeveloperRepository;
import com.alibaba.himarket.repository.PortalRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceImplTest {

    @Mock private DeveloperRepository developerRepository;

    @Mock private DeveloperExternalIdentityRepository externalRepository;

    @Mock private PortalRepository portalRepository;

    @Mock private ContextHolder contextHolder;

    @Mock private ConsumerService consumerService;

    @Mock private AuthSessionStore authSessionStore;

    private DeveloperServiceImpl developerService;

    @BeforeEach
    void setUp() {
        developerService =
                new DeveloperServiceImpl(
                        developerRepository,
                        externalRepository,
                        portalRepository,
                        contextHolder,
                        consumerService,
                        authSessionStore);
    }

    @Test
    void updateExternalDeveloperProfileShouldKeepExistingRawInfoWhenIncomingValueIsNull() {
        Developer developer = new Developer();
        developer.setEmail("alice@example.com");

        DeveloperExternalIdentity externalIdentity = new DeveloperExternalIdentity();
        externalIdentity.setDeveloper(developer);
        externalIdentity.setDisplayName("Alice");
        externalIdentity.setRawInfoJson("{\"sub\":\"alice\"}");

        when(externalRepository.findByProviderAndSubject("cas", "alice"))
                .thenReturn(Optional.of(externalIdentity));

        developerService.updateExternalDeveloperProfile(
                "cas", "alice", "Alice", "alice@example.com", null);

        assertEquals("{\"sub\":\"alice\"}", externalIdentity.getRawInfoJson());
        verify(developerRepository, never()).save(any(Developer.class));
        verify(externalRepository, never()).save(any(DeveloperExternalIdentity.class));
    }
}
