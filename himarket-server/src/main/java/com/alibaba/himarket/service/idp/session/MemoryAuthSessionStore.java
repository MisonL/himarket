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

import com.alibaba.himarket.core.utils.TokenUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryAuthSessionStore implements AuthSessionStore {

    private final Cache<String, CasLoginContext> loginCodes;

    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    private final Map<String, SessionTokens> sessionTokens = new ConcurrentHashMap<>();

    public MemoryAuthSessionStore(Duration loginCodeTtl) {
        this.loginCodes =
                Caffeine.newBuilder().expireAfterWrite(loginCodeTtl).maximumSize(10000).build();
    }

    @Override
    public void saveCasLoginContext(String code, CasLoginContext context, Duration ttl) {
        loginCodes.put(code, context);
    }

    @Override
    public CasLoginContext consumeCasLoginContext(String code) {
        return loginCodes.asMap().remove(code);
    }

    @Override
    public void bindCasSessionToken(CasSessionScope scope, String sessionIndex, String token) {
        String key = buildSessionKey(scope, sessionIndex);
        SessionTokens bucket = sessionTokens.computeIfAbsent(key, ignored -> new SessionTokens());
        bucket.addToken(TokenUtil.getTokenDigest(token), TokenUtil.getTokenExpireTime(token));
    }

    @Override
    public int revokeCasSession(CasSessionScope scope, String sessionIndex) {
        SessionTokens bucket = sessionTokens.remove(buildSessionKey(scope, sessionIndex));
        if (bucket == null) {
            return 0;
        }
        int revoked = 0;
        for (Map.Entry<String, Long> entry : bucket.snapshot().entrySet()) {
            revokeTokenDigest(entry.getKey(), entry.getValue());
            revoked++;
        }
        return revoked;
    }

    @Override
    public void revokeToken(String token) {
        revokeTokenDigest(TokenUtil.getTokenDigest(token), TokenUtil.getTokenExpireTime(token));
    }

    @Override
    public boolean isTokenRevoked(String token) {
        String digest = TokenUtil.getTokenDigest(token);
        Long expireAt = revokedTokens.get(digest);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            revokedTokens.remove(digest);
            return false;
        }
        return true;
    }

    private void revokeTokenDigest(String tokenDigest, long expireAt) {
        if (expireAt <= System.currentTimeMillis()) {
            return;
        }
        revokedTokens.put(tokenDigest, expireAt);
    }

    private String buildSessionKey(CasSessionScope scope, String sessionIndex) {
        return scope.name() + ":" + sessionIndex;
    }

    private static final class SessionTokens {

        private final Map<String, Long> tokens = new ConcurrentHashMap<>();

        private void addToken(String tokenDigest, long expireAt) {
            tokens.put(tokenDigest, expireAt);
        }

        private Map<String, Long> snapshot() {
            tokens.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
            return Map.copyOf(tokens);
        }
    }
}
