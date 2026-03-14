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

import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.service.AdminCasService;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admins/cas")
@RequiredArgsConstructor
public class AdminCasController {

    private final AdminCasService adminCasService;

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam String provider,
            @RequestParam(defaultValue = "/api/v1") String apiPrefix,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        IdpAuthorizeResult result =
                adminCasService.buildAuthorizationResult(provider, apiPrefix, request);
        IdpStateCookie.writeAdminCasStateCookie(request, response, result.getState());
        log.info("Redirecting to CAS login for admin, provider={}", provider);
        response.sendRedirect(result.getRedirectUrl());
    }

    @GetMapping("/callback")
    public AuthResult callback(
            @RequestParam String ticket,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        IdpStateCookie.assertAdminCasStateCookieMatches(request, state);
        AuthResult result = adminCasService.handleCallback(ticket, state, request, response);
        IdpStateCookie.clearAdminCasStateCookie(request, response);
        return result;
    }

    @GetMapping("/providers")
    public List<IdpResult> getProviders() {
        return adminCasService.getAvailableProviders();
    }

    @GetMapping("/logout")
    public void logout(@RequestParam String provider, HttpServletResponse response)
            throws IOException {
        String url = adminCasService.buildLogoutRedirectUrl(provider);
        response.sendRedirect(url);
    }
}
