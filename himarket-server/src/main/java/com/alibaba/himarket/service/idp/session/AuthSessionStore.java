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

import java.time.Duration;

public interface AuthSessionStore {

    void saveCasLoginContext(String code, CasLoginContext context, Duration ttl);

    CasLoginContext consumeCasLoginContext(String code);

    void bindCasSessionToken(
            CasSessionScope scope, String userId, String sessionIndex, String token);

    int revokeCasSession(CasSessionScope scope, String sessionIndex);

    int revokeUserSessions(CasSessionScope scope, String userId);

    int revokeOverflowUserSessions(CasSessionScope scope, String userId, int maxSessions);

    void saveCasProxyGrantingTicket(String pgtIou, String pgtId, Duration ttl);

    String consumeCasProxyGrantingTicket(String pgtIou);

    void bindCasProxyGrantingTicket(
            CasSessionScope scope,
            String provider,
            String userId,
            String sessionIndex,
            String pgtId);

    String getCasProxyGrantingTicket(
            CasSessionScope scope, String provider, String userId, String tokenDigest);

    void revokeToken(String token);

    boolean isTokenRevoked(String token);
}
