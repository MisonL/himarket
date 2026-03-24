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

import static org.junit.jupiter.api.Assertions.assertNull;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.support.enums.UserType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;

class MemoryAuthSessionStoreTest {

    @Test
    void getCasProxyGrantingTicketShouldDropExpiredTokenSessionBinding() {
        MemoryAuthSessionStore store = new MemoryAuthSessionStore(Duration.ofSeconds(60));
        String expiredToken = expiredDeveloperToken("dev-1");

        store.bindCasSessionToken(CasSessionScope.DEVELOPER, "dev-1", "ST-1", expiredToken);
        store.bindCasProxyGrantingTicket(
                CasSessionScope.DEVELOPER, "cas", "dev-1", "ST-1", "PGT-DEV-1");

        assertNull(
                store.getCasProxyGrantingTicket(
                        CasSessionScope.DEVELOPER,
                        "cas",
                        "dev-1",
                        com.alibaba.himarket.core.utils.TokenUtil.getTokenDigest(expiredToken)));
    }

    private String expiredDeveloperToken(String userId) {
        return JWT.create()
                .addPayloads(
                        java.util.Map.of(
                                CommonConstants.USER_TYPE,
                                UserType.DEVELOPER.name(),
                                CommonConstants.USER_ID,
                                userId))
                .setIssuedAt(new Date(System.currentTimeMillis() - 2000))
                .setExpiresAt(new Date(System.currentTimeMillis() - 1000))
                .setSigner(JWTSignerUtil.hs256("unit-test-secret".getBytes(StandardCharsets.UTF_8)))
                .sign();
    }
}
