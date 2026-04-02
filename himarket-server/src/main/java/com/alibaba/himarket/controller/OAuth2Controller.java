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

import com.alibaba.himarket.dto.params.idp.OAuth2BrowserLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2DirectLoginParam;
import com.alibaba.himarket.dto.params.idp.OAuth2TrustedHeaderLoginParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.service.OAuth2Service;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/developers/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam String provider,
            @RequestParam(defaultValue = "/api/v1") String apiPrefix,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        IdpAuthorizeResult result = oAuth2Service.buildAuthorizationResult(provider, apiPrefix);
        IdpStateCookie.writeOauth2StateCookie(request, response, result.getState());
        response.sendRedirect(result.getRedirectUrl());
    }

    @PostMapping("/token")
    public AuthResult authenticate(
            @RequestParam("grant_type") String grantType,
            @RequestParam("assertion") String assertion) {
        return oAuth2Service.authenticate(grantType, assertion);
    }

    @PostMapping("/direct")
    public AuthResult authenticateDirect(@Valid @RequestBody OAuth2DirectLoginParam param) {
        return oAuth2Service.authenticateDirect(param);
    }

    @PostMapping("/complete")
    public AuthResult complete(
            @Valid @RequestBody OAuth2BrowserLoginParam param,
            HttpServletRequest request,
            HttpServletResponse response) {
        IdpStateCookie.assertOauth2StateCookieMatches(request, param.getState());
        AuthResult result = oAuth2Service.completeBrowserLogin(param);
        IdpStateCookie.clearOauth2StateCookie(request, response);
        return result;
    }

    @PostMapping("/trusted-header")
    public AuthResult authenticateTrustedHeader(
            @Valid @RequestBody OAuth2TrustedHeaderLoginParam param, HttpServletRequest request) {
        return oAuth2Service.authenticateTrustedHeader(param.getProvider(), request);
    }

    @GetMapping("/providers")
    public List<IdpResult> getProviders() {
        return oAuth2Service.getAvailableProviders();
    }
}
