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

    /** 构造 developer CAS 授权跳转结果。 */
    IdpAuthorizeResult buildAuthorizationResult(
            String provider,
            String apiPrefix,
            CasAuthorizeOptions options,
            HttpServletRequest request);

    /** 处理 developer CAS 登录回调并生成前端回跳地址。 */
    String handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response);

    /** 使用一次性登录码换取 developer JWT。 */
    AuthResult exchangeCode(String code);

    /** 处理 developer CAS 单点登出请求并撤销本地会话。 */
    int handleLogoutRequest(String logoutRequest);

    /** 接收 developer CAS proxy callback 中的 PGT。 */
    void handleProxyCallback(String pgtIou, String pgtId);

    /** 用当前 developer 登录态申请 CAS proxy ticket。 */
    CasProxyTicketResult issueProxyTicket(String provider, String targetService);

    /** 返回当前 portal 可用的交互式 CAS provider 列表。 */
    List<IdpResult> getAvailableProviders();

    /** 构造 developer 退出登录后的 CAS 重定向地址。 */
    String buildLogoutRedirectUrl(String provider);
}
