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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.idp.CasAuthorizeOptions;
import com.alibaba.himarket.dto.params.idp.CasExchangeParam;
import com.alibaba.himarket.dto.params.idp.CasProxyTicketParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.CasProxyTicketResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.service.CasService;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/developers/cas")
@RequiredArgsConstructor
public class CasController {

    private final CasService casService;

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam String provider,
            @RequestParam(defaultValue = "/api/v1") String apiPrefix,
            @RequestParam(required = false) Boolean gateway,
            @RequestParam(required = false) Boolean renew,
            @RequestParam(required = false) Boolean warn,
            @RequestParam(required = false) Boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        IdpAuthorizeResult result =
                casService.buildAuthorizationResult(
                        provider,
                        apiPrefix,
                        CasAuthorizeOptions.builder()
                                .gateway(gateway)
                                .renew(renew)
                                .warn(warn)
                                .rememberMe(rememberMe)
                                .build(),
                        request);
        IdpStateCookie.writeCasStateCookie(request, response, result.getState());
        log.info("Redirecting to CAS login, provider={}", provider);
        response.sendRedirect(result.getRedirectUrl());
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam String ticket,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        response.sendRedirect(casService.handleCallback(ticket, state, request, response));
    }

    @PostMapping("/callback")
    public void callbackPost(
            @RequestParam(required = false) String logoutRequest,
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        if (StrUtil.isNotBlank(logoutRequest)) {
            casService.handleLogoutRequest(logoutRequest);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        if (StrUtil.isNotBlank(ticket) && StrUtil.isNotBlank(state)) {
            response.sendRedirect(casService.handleCallback(ticket, state, request, response));
            return;
        }
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST, "Missing CAS callback ticket/state or logoutRequest");
    }

    @PostMapping("/exchange")
    public AuthResult exchange(@Valid @RequestBody CasExchangeParam param) {
        return casService.exchangeCode(param.getCode());
    }

    @GetMapping("/proxy-callback")
    public void proxyCallback(
            @RequestParam(required = false) String pgtIou,
            @RequestParam(required = false) String pgtId,
            HttpServletResponse response) {
        if (StrUtil.isNotBlank(pgtIou) && StrUtil.isNotBlank(pgtId)) {
            casService.handleProxyCallback(pgtIou, pgtId);
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/proxy-ticket")
    public CasProxyTicketResult proxyTicket(@Valid @RequestBody CasProxyTicketParam param) {
        return casService.issueProxyTicket(param.getProvider(), param.getTargetService());
    }

    @GetMapping("/providers")
    public List<IdpResult> getProviders() {
        return casService.getAvailableProviders();
    }

    @GetMapping("/logout")
    public void logout(@RequestParam String provider, HttpServletResponse response)
            throws IOException {
        String url = casService.buildLogoutRedirectUrl(provider);
        response.sendRedirect(url);
    }
}
