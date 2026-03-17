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

public interface AdminCasService {

    /**
     * 构造 admin CAS 授权跳转结果。
     *
     * @param provider CAS 提供者名称
     * @param apiPrefix API 路径前缀
     * @param options 授权选项（gateway, renew 等）
     * @param request HTTP 请求对象
     * @return 包含重定向 URL 和 State 的授权结果
     */
    IdpAuthorizeResult buildAuthorizationResult(
            String provider,
            String apiPrefix,
            CasAuthorizeOptions options,
            HttpServletRequest request);

    /**
     * 处理 admin CAS 登录回调并生成前端回跳地址。
     *
     * @param ticket CAS Service Ticket
     * @param state 授权时生成的 State，用于防 CSRF
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 前端重定向 URL
     */
    String handleCallback(
            String ticket, String state, HttpServletRequest request, HttpServletResponse response);

    /**
     * 使用一次性登录码换取 admin JWT。
     *
     * @param code 一次性登录码
     * @return 包含 AccessToken 和过期时间的认证结果
     */
    AuthResult exchangeCode(String code);

    /**
     * 处理 admin CAS 单点登出请求并撤销本地会话。
     *
     * @param logoutRequest CAS 注销请求报文
     * @return 撤销的会话数量
     */
    int handleLogoutRequest(String logoutRequest);

    /**
     * 接收 admin CAS proxy callback 中的 PGT。
     *
     * @param pgtIou PGT IOU
     * @param pgtId PGT ID
     */
    void handleProxyCallback(String pgtIou, String pgtId);

    /**
     * 用当前 admin 登录态申请 CAS proxy ticket。
     *
     * @param provider CAS 提供者名称
     * @param targetService 目标服务 URL
     * @return 包含 Proxy Ticket 的结果
     */
    CasProxyTicketResult issueProxyTicket(String provider, String targetService);

    /**
     * 返回当前 admin 全局配置下可用的交互式 CAS provider 列表。
     *
     * @return IDP 列表
     */
    List<IdpResult> getAvailableProviders();

    /**
     * 构造 admin 退出登录后的 CAS 重定向地址。
     *
     * @param provider CAS 提供者名称
     * @return CAS 注销重定向 URL
     */
    String buildLogoutRedirectUrl(String provider);
}
