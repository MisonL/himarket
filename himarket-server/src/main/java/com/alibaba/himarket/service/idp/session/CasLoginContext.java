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

package com.alibaba.himarket.service.idp.session;

public final class CasLoginContext {

    private final CasSessionScope scope;

    private final String provider;

    private final String userId;

    private final String sessionIndex;

    private final String proxyGrantingTicketIou;

    private final Long tokenExpiresIn;

    public CasLoginContext(
            CasSessionScope scope,
            String provider,
            String userId,
            String sessionIndex,
            String proxyGrantingTicketIou,
            Long tokenExpiresIn) {
        this.scope = scope;
        this.provider = provider;
        this.userId = userId;
        this.sessionIndex = sessionIndex;
        this.proxyGrantingTicketIou = proxyGrantingTicketIou;
        this.tokenExpiresIn = tokenExpiresIn;
    }

    public CasSessionScope getScope() {
        return scope;
    }

    public String getProvider() {
        return provider;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionIndex() {
        return sessionIndex;
    }

    public String getProxyGrantingTicketIou() {
        return proxyGrantingTicketIou;
    }

    public Long getTokenExpiresIn() {
        return tokenExpiresIn;
    }
}
