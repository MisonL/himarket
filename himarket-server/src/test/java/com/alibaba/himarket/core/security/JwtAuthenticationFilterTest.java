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

package com.alibaba.himarket.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private AuthSessionStore authSessionStore;

    @BeforeEach
    void setUp() {
        System.setProperty("jwt.secret", "unit-test-secret");
        System.setProperty("jwt.expiration", "60000");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("jwt.secret");
        System.clearProperty("jwt.expiration");
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateValidToken() throws Exception {
        String token = TokenUtil.generateDeveloperToken("developer-1");
        when(authSessionStore.isTokenRevoked(token)).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authSessionStore);
        MockHttpServletRequest request = buildBearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(authSessionStore).isTokenRevoked(token);
        verify(chain).doFilter(request, response);
        assertEquals(
                "developer-1",
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void shouldIgnoreMalformedToken() throws Exception {
        String token = "bad-token";
        when(authSessionStore.isTokenRevoked(token)).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authSessionStore);
        MockHttpServletRequest request = buildBearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(authSessionStore).isTokenRevoked(token);
        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSurfaceSessionStoreFailure() {
        String token = TokenUtil.generateDeveloperToken("developer-1");
        when(authSessionStore.isTokenRevoked(token))
                .thenThrow(new IllegalStateException("session store unavailable"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authSessionStore);
        MockHttpServletRequest request = buildBearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> filter.doFilterInternal(request, response, chain));

        assertEquals("session store unavailable", exception.getMessage());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest buildBearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                CommonConstants.AUTHORIZATION_HEADER, CommonConstants.BEARER_PREFIX + token);
        return request;
    }
}
