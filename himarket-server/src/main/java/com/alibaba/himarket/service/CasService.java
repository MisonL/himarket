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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.idp.CasAuthorizeOptions;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.idp.CasProxyTicketResult;
import com.alibaba.himarket.dto.result.idp.IdpAuthorizeResult;
import com.alibaba.himarket.dto.result.idp.IdpResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface CasService {

    IdpAuthorizeResult buildAuthorizationResult(
            String provider,
            String apiPrefix,
            CasAuthorizeOptions options,
            HttpServletRequest request);

    String handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response);

    AuthResult exchangeCode(String code);

    int handleLogoutRequest(String logoutRequest);

    void handleProxyCallback(String pgtIou, String pgtId);

    CasProxyTicketResult issueProxyTicket(String provider, String targetService);

    List<IdpResult> getAvailableProviders();

    String buildLogoutRedirectUrl(String provider);
}
