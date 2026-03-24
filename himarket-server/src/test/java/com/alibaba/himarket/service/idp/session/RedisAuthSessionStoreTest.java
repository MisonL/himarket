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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisAuthSessionStoreTest {

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private SetOperations<String, String> setOperations;

    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void revokeCasSessionShouldFailFastOnCorruptedTokenExpiration() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("hm:auth:cas:session:DEVELOPER:ST-1"))
                .thenReturn(Set.of("user:dev-1", "token:digest:not-a-number"));

        RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

        assertThrows(
                IllegalStateException.class,
                () -> store.revokeCasSession(CasSessionScope.DEVELOPER, "ST-1"));
        verify(redisTemplate, never()).delete("hm:auth:cas:session:DEVELOPER:ST-1");
    }

    @Test
    void getCasProxyGrantingTicketShouldResolveCurrentSession() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("hm:auth:cas:token-session:token-digest")).thenReturn("ST-2");
        when(valueOperations.get("hm:auth:cas:pgt:DEVELOPER:cas:dev-1:ST-2"))
                .thenReturn("PGT-DEV-2");

        RedisAuthSessionStore store = new RedisAuthSessionStore(redisTemplate);

        assertEquals(
                "PGT-DEV-2",
                store.getCasProxyGrantingTicket(
                        CasSessionScope.DEVELOPER, "cas", "dev-1", "token-digest"));
    }
}
