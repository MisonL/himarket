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

package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.dto.params.idp.OAuth2BrowserLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2DirectLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2TrustedHeaderLoginParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.service.OAuth2Service;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerTest {

    @Mock private OAuth2Service oAuth2Service;

    @Test
    void authorizeShouldWriteStateCookieAndRedirect() throws Exception {
        OAuth2Controller controller = new OAuth2Controller(oAuth2Service);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/developers/oauth2/authorize");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdpAuthorizeResult authorizeResult =
                IdpAuthorizeResult.builder()
                        .state("state-123")
                        .redirectUrl("https://cas.example.com/login?state=state-123")
                        .build();
        when(oAuth2Service.buildAuthorizationResult("cas-jwt-direct", "/api/v1"))
                .thenReturn(authorizeResult);

        controller.authorize("cas-jwt-direct", "/api/v1", request, response);

        assertEquals("https://cas.example.com/login?state=state-123", response.getRedirectedUrl());
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains(IdpConstants.OAUTH2_STATE_COOKIE_NAME + "=state-123"));
        assertTrue(setCookie.contains("HttpOnly"));
        verify(oAuth2Service).buildAuthorizationResult("cas-jwt-direct", "/api/v1");
    }

    @Test
    void completeShouldValidateStateCookieAndClearItAfterSuccess() {
        OAuth2Controller controller = new OAuth2Controller(oAuth2Service);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/developers/oauth2/complete");
        request.setCookies(new Cookie(IdpConstants.OAUTH2_STATE_COOKIE_NAME, "state-123"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2BrowserLoginParam param = new OAuth2BrowserLoginParam();
        param.setProvider("cas-jwt-direct");
        param.setState("state-123");
        param.setJwt("jwt-token");
        AuthResult authResult = AuthResult.of("access-token", 3600L);
        when(oAuth2Service.completeBrowserLogin(param)).thenReturn(authResult);

        AuthResult result = controller.complete(param, request, response);

        assertEquals("access-token", result.getAccessToken());
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains(IdpConstants.OAUTH2_STATE_COOKIE_NAME + "="));
        assertTrue(setCookie.contains("Max-Age=0"));
        verify(oAuth2Service).completeBrowserLogin(param);
    }

    @Test
    void completeShouldRejectWhenStateCookieDoesNotMatch() {
        OAuth2Controller controller = new OAuth2Controller(oAuth2Service);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/developers/oauth2/complete");
        request.setCookies(new Cookie(IdpConstants.OAUTH2_STATE_COOKIE_NAME, "other-state"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2BrowserLoginParam param = new OAuth2BrowserLoginParam();
        param.setProvider("cas-jwt-direct");
        param.setState("state-123");
        param.setJwt("jwt-token");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> controller.complete(param, request, response));

        assertEquals("INVALID_REQUEST", exception.getCode());
        verifyNoInteractions(oAuth2Service);
    }

    @Test
    void authenticateDirectShouldDelegateToService() {
        OAuth2Controller controller = new OAuth2Controller(oAuth2Service);
        OAuth2DirectLoginParam param = new OAuth2DirectLoginParam();
        param.setProvider("cas-jwt");
        param.setJwt("jwt-token");
        AuthResult authResult = AuthResult.of("access-token", 3600L);
        when(oAuth2Service.authenticateDirect(param)).thenReturn(authResult);

        AuthResult result = controller.authenticateDirect(param);

        assertEquals("access-token", result.getAccessToken());
        verify(oAuth2Service).authenticateDirect(param);
    }

    @Test
    void authenticateTrustedHeaderShouldDelegateToService() {
        OAuth2Controller controller = new OAuth2Controller(oAuth2Service);
        OAuth2TrustedHeaderLoginParam param = new OAuth2TrustedHeaderLoginParam();
        param.setProvider("trusted-header");
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/developers/oauth2/trusted-header");
        AuthResult authResult = AuthResult.of("access-token", 3600L);
        when(oAuth2Service.authenticateTrustedHeader("trusted-header", request))
                .thenReturn(authResult);

        AuthResult result = controller.authenticateTrustedHeader(param, request);

        assertEquals("access-token", result.getAccessToken());
        verify(oAuth2Service).authenticateTrustedHeader("trusted-header", request);
    }
}
