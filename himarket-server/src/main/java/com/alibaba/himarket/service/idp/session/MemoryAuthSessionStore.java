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

    private final Cache<String, String> proxyGrantingTickets;

    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    private final Map<String, java.util.Set<String>> userSessions = new ConcurrentHashMap<>();

    private final Map<String, SessionArtifacts> sessionArtifacts = new ConcurrentHashMap<>();

    private final Map<String, String> userProxyGrantingTickets = new ConcurrentHashMap<>();

    public MemoryAuthSessionStore(Duration loginCodeTtl) {
        this.loginCodes =
                Caffeine.newBuilder().expireAfterWrite(loginCodeTtl).maximumSize(10000).build();
        this.proxyGrantingTickets =
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
    public void bindCasSessionToken(
            CasSessionScope scope, String userId, String sessionIndex, String token) {
        String sessionKey = buildSessionKey(scope, sessionIndex);
        String userKey = buildUserKey(scope, userId);

        SessionArtifacts bucket =
                sessionArtifacts.computeIfAbsent(
                        sessionKey, ignored -> new SessionArtifacts(userId));
        bucket.addToken(TokenUtil.getTokenDigest(token), TokenUtil.getTokenExpireTime(token));

        userSessions
                .computeIfAbsent(userKey, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionKey);
    }

    @Override
    public int revokeCasSession(CasSessionScope scope, String sessionIndex) {
        String sessionKey = buildSessionKey(scope, sessionIndex);
        SessionArtifacts bucket = sessionArtifacts.remove(sessionKey);
        if (bucket == null) {
            return 0;
        }

        String userKey = buildUserKey(scope, bucket.getUserId());
        java.util.Set<String> sessions = userSessions.get(userKey);
        if (sessions != null) {
            sessions.remove(sessionKey);
            if (sessions.isEmpty()) {
                userSessions.remove(userKey);
            }
        }

        bucket.snapshotProxyKeys().forEach(userProxyGrantingTickets::remove);
        int revoked = 0;
        for (Map.Entry<String, Long> entry : bucket.snapshot().entrySet()) {
            revokeTokenDigest(entry.getKey(), entry.getValue());
            revoked++;
        }
        return revoked;
    }

    @Override
    public int revokeUserSessions(CasSessionScope scope, String userId) {
        String userKey = buildUserKey(scope, userId);
        java.util.Set<String> sessions = userSessions.remove(userKey);
        if (sessions == null) {
            return 0;
        }
        int totalRevoked = 0;
        for (String sessionKey : sessions) {
            String sessionIndex = sessionKey.substring(sessionKey.indexOf(':') + 1);
            totalRevoked += revokeCasSession(scope, sessionIndex);
        }
        return totalRevoked;
    }

    @Override
    public void saveCasProxyGrantingTicket(String pgtIou, String pgtId, Duration ttl) {
        proxyGrantingTickets.put(pgtIou, pgtId);
    }

    @Override
    public String consumeCasProxyGrantingTicket(String pgtIou) {
        return proxyGrantingTickets.asMap().remove(pgtIou);
    }

    @Override
    public void bindCasProxyGrantingTicket(
            CasSessionScope scope,
            String provider,
            String userId,
            String sessionIndex,
            String pgtId) {
        String principalKey = buildPrincipalKey(scope, provider, userId);
        userProxyGrantingTickets.put(principalKey, pgtId);
        sessionArtifacts
                .computeIfAbsent(
                        buildSessionKey(scope, sessionIndex),
                        ignored -> new SessionArtifacts(userId))
                .addProxyKey(principalKey);
    }

    @Override
    public String getCasProxyGrantingTicket(CasSessionScope scope, String provider, String userId) {
        return userProxyGrantingTickets.get(buildPrincipalKey(scope, provider, userId));
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

    private String buildPrincipalKey(CasSessionScope scope, String provider, String userId) {
        return scope.name() + ":" + provider + ":" + userId;
    }

    private String buildUserKey(CasSessionScope scope, String userId) {
        return scope.name() + ":u:" + userId;
    }

    private static final class SessionArtifacts {

        private final String userId;

        private final Map<String, Long> tokens = new ConcurrentHashMap<>();

        private final Map<String, Boolean> proxyKeys = new ConcurrentHashMap<>();

        public SessionArtifacts(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }

        private void addToken(String tokenDigest, long expireAt) {
            tokens.put(tokenDigest, expireAt);
        }

        private void addProxyKey(String proxyKey) {
            proxyKeys.put(proxyKey, Boolean.TRUE);
        }

        private Map<String, Long> snapshot() {
            tokens.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
            return Map.copyOf(tokens);
        }

        private java.util.Set<String> snapshotProxyKeys() {
            return java.util.Set.copyOf(proxyKeys.keySet());
        }
    }
}
