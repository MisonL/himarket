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
import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.idp.CasAuthorizeOptions;
import com.alibaba.himarket.dto.params.idp.CasExchangeParam;
import com.alibaba.himarket.dto.params.idp.CasProxyTicketParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.CasProxyTicketResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import com.alibaba.himarket.service.AdminCasService;
import com.alibaba.himarket.service.idp.CasServiceDefinitionService;
import com.alibaba.himarket.service.idp.IdpStateCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admins/cas")
@RequiredArgsConstructor
public class AdminCasController {

    private final AdminCasService adminCasService;

    private final CasServiceDefinitionService casServiceDefinitionService;

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
                adminCasService.buildAuthorizationResult(
                        provider,
                        apiPrefix,
                        CasAuthorizeOptions.builder()
                                .gateway(gateway)
                                .renew(renew)
                                .warn(warn)
                                .rememberMe(rememberMe)
                                .build(),
                        request);
        IdpStateCookie.writeAdminCasStateCookie(request, response, result.getState());
        log.info("Redirecting to CAS login for admin, provider={}", provider);
        response.sendRedirect(result.getRedirectUrl());
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam String ticket,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        response.sendRedirect(adminCasService.handleCallback(ticket, state, request, response));
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
            adminCasService.handleLogoutRequest(logoutRequest);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        if (StrUtil.isNotBlank(ticket) && StrUtil.isNotBlank(state)) {
            response.sendRedirect(adminCasService.handleCallback(ticket, state, request, response));
            return;
        }
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST, "Missing CAS callback ticket/state or logoutRequest");
    }

    @PostMapping("/exchange")
    public AuthResult exchange(@Valid @RequestBody CasExchangeParam param) {
        return adminCasService.exchangeCode(param.getCode());
    }

    @GetMapping("/proxy-callback")
    public void proxyCallback(
            @RequestParam(required = false) String pgtIou,
            @RequestParam(required = false) String pgtId,
            HttpServletResponse response) {
        if (StrUtil.isNotBlank(pgtIou) && StrUtil.isNotBlank(pgtId)) {
            adminCasService.handleProxyCallback(pgtIou, pgtId);
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/proxy-ticket")
    public CasProxyTicketResult proxyTicket(@Valid @RequestBody CasProxyTicketParam param) {
        return adminCasService.issueProxyTicket(param.getProvider(), param.getTargetService());
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

    @AdminAuth
    @GetMapping("/{provider}/service-definition")
    public java.util.Map<String, Object> exportAdminCasServiceDefinition(
            @PathVariable String provider) {
        return casServiceDefinitionService.exportAdminServiceDefinition(provider);
    }
}
