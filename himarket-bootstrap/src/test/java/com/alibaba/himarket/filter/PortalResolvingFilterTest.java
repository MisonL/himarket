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

package com.alibaba.himarket.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.service.PortalService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class PortalResolvingFilterTest {

    @Mock private PortalService portalService;

    @Mock private ContextHolder contextHolder;

    @Mock private FilterChain filterChain;

    @Test
    void strictAuthPathShouldNotFallbackToDefaultPortal() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/developers/cas/providers");
        request.addHeader("Host", "unknown.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("unknown.example.com")).thenReturn(null);

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(portalService).resolvePortal("unknown.example.com");
        verify(portalService, never()).getDefaultPortal();
        verify(contextHolder, never()).savePortal(anyString());
        verify(contextHolder).clearPortal();
    }

    @Test
    void strictAuthPathWithApiPrefixShouldNotFallbackToDefaultPortal() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/developers/cas/providers");
        request.addHeader("Host", "unknown.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("unknown.example.com")).thenReturn(null);

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(portalService).resolvePortal("unknown.example.com");
        verify(portalService, never()).getDefaultPortal();
        verify(contextHolder, never()).savePortal(anyString());
        verify(contextHolder).clearPortal();
    }

    @Test
    void strictCasExchangePathShouldNotFallbackToDefaultPortal() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/developers/cas/exchange");
        request.addHeader("Host", "unknown.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("unknown.example.com")).thenReturn(null);

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(portalService).resolvePortal("unknown.example.com");
        verify(portalService, never()).getDefaultPortal();
        verify(contextHolder, never()).savePortal(anyString());
        verify(contextHolder).clearPortal();
    }

    @Test
    void strictCasProxyCallbackPathShouldNotFallbackToDefaultPortal() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/developers/cas/proxy-callback");
        request.addHeader("Host", "unknown.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("unknown.example.com")).thenReturn(null);

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(portalService).resolvePortal("unknown.example.com");
        verify(portalService, never()).getDefaultPortal();
        verify(contextHolder, never()).savePortal(anyString());
        verify(contextHolder).clearPortal();
    }

    @Test
    void shouldUseForwardedHostWhenPresent() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/developers/oidc/providers");
        request.addHeader("Host", "internal-service.local");
        request.addHeader("X-Forwarded-Host", "portal.example.com:443");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("portal.example.com")).thenReturn("portal-1");

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(portalService).resolvePortal("portal.example.com");
        verify(contextHolder).savePortal("portal-1");
        verify(contextHolder).clearPortal();
    }

    @Test
    void nonAuthPathShouldFallbackToDefaultPortal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader("Host", "unknown.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(portalService.resolvePortal("unknown.example.com")).thenReturn(null);
        when(portalService.getDefaultPortal()).thenReturn("default-portal");

        PortalResolvingFilter filter = new PortalResolvingFilter(portalService, contextHolder);
        filter.doFilter(request, response, filterChain);

        verify(contextHolder).savePortal("default-portal");
        verify(contextHolder).clearPortal();
    }
}
