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

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.utils.TokenUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

public class RedisAuthSessionStore implements AuthSessionStore {

    private static final String LOGIN_CODE_PREFIX = "hm:auth:cas:code:";

    private static final String USER_SESSIONS_PREFIX = "hm:auth:cas:user-sessions:";

    private static final String SESSION_PREFIX = "hm:auth:cas:session:";

    private static final String PGT_IOU_PREFIX = "hm:auth:cas:pgt-iou:";

    private static final String PGT_PREFIX = "hm:auth:cas:pgt:";

    private static final String TOKEN_SESSION_PREFIX = "hm:auth:cas:token-session:";

    private static final String REVOKE_PREFIX = "hm:auth:revoke:";

    private final StringRedisTemplate redisTemplate;

    public RedisAuthSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveCasLoginContext(String code, CasLoginContext context, Duration ttl) {
        redisTemplate.opsForValue().set(loginCodeKey(code), JSONUtil.toJsonStr(context), ttl);
    }

    @Override
    public CasLoginContext consumeCasLoginContext(String code) {
        String value = redisTemplate.opsForValue().getAndDelete(loginCodeKey(code));
        if (value == null) {
            return null;
        }
        return parseCasLoginContext(value);
    }

    @Override
    public void bindCasSessionToken(
            CasSessionScope scope, String userId, String sessionIndex, String token) {
        String key = sessionKey(scope, sessionIndex);
        long expireAt = TokenUtil.getTokenExpireTime(token);
        String tokenDigest = TokenUtil.getTokenDigest(token);
        String payload = "token:" + tokenDigest + ":" + expireAt;
        redisTemplate.opsForSet().add(key, payload);
        redisTemplate.opsForSet().add(key, "user:" + userId);
        extendSessionKey(key, expireAt);
        saveTokenSession(tokenDigest, sessionIndex, expireAt);

        String userKey = userSessionsKey(scope, userId);
        redisTemplate.opsForZSet().add(userKey, sessionIndex, System.currentTimeMillis());
        extendSessionKey(userKey, expireAt);
    }

    @Override
    public int revokeCasSession(CasSessionScope scope, String sessionIndex) {
        String key = sessionKey(scope, sessionIndex);
        Set<String> entries = redisTemplate.opsForSet().members(key);
        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return 0;
        }

        ParsedSessionArtifacts artifacts = parseSessionArtifacts(entries, sessionIndex);
        redisTemplate.delete(key);
        artifacts.tokenArtifacts.forEach(
                tokenArtifact -> {
                    redisTemplate.delete(tokenSessionKey(tokenArtifact.tokenDigest));
                    revokeTokenDigest(tokenArtifact.tokenDigest, tokenArtifact.expireAt);
                });
        artifacts.proxyGrantingTickets.forEach(
                proxyKey ->
                        redisTemplate.delete(
                                proxyGrantingTicketKey(
                                        scope, proxyKey.provider, proxyKey.userId, sessionIndex)));

        if (artifacts.userId != null) {
            redisTemplate
                    .opsForZSet()
                    .remove(userSessionsKey(scope, artifacts.userId), sessionIndex);
        }

        return artifacts.tokenArtifacts.size();
    }

    @Override
    public int revokeUserSessions(CasSessionScope scope, String userId) {
        String userKey = userSessionsKey(scope, userId);
        Set<String> sessionIndices = redisTemplate.opsForZSet().range(userKey, 0, -1);
        redisTemplate.delete(userKey);
        if (sessionIndices == null || sessionIndices.isEmpty()) {
            return 0;
        }
        int totalRevoked = 0;
        for (String sessionIndex : sessionIndices) {
            totalRevoked += revokeCasSession(scope, sessionIndex);
        }
        return totalRevoked;
    }

    @Override
    public int revokeOverflowUserSessions(CasSessionScope scope, String userId, int maxSessions) {
        if (maxSessions <= 0) {
            return revokeUserSessions(scope, userId);
        }

        String userKey = userSessionsKey(scope, userId);
        Long sessionCount = redisTemplate.opsForZSet().zCard(userKey);
        if (sessionCount == null || sessionCount <= maxSessions) {
            return 0;
        }

        Set<ZSetOperations.TypedTuple<String>> sessionEntries =
                redisTemplate.opsForZSet().rangeWithScores(userKey, 0, -1);
        if (sessionEntries == null || sessionEntries.size() <= maxSessions) {
            return 0;
        }

        List<String> orderedSessionIndices = new ArrayList<>();
        sessionEntries.stream()
                .sorted(
                        Comparator.comparingDouble(
                                entry -> entry.getScore() == null ? 0D : entry.getScore()))
                .forEach(entry -> orderedSessionIndices.add(entry.getValue()));

        int totalRevoked = 0;
        for (int i = 0; i < orderedSessionIndices.size() - maxSessions; i++) {
            totalRevoked += revokeCasSession(scope, orderedSessionIndices.get(i));
        }
        return totalRevoked;
    }

    @Override
    public void saveCasProxyGrantingTicket(String pgtIou, String pgtId, Duration ttl) {
        redisTemplate.opsForValue().set(proxyGrantingTicketIouKey(pgtIou), pgtId, ttl);
    }

    @Override
    public String consumeCasProxyGrantingTicket(String pgtIou) {
        return redisTemplate.opsForValue().getAndDelete(proxyGrantingTicketIouKey(pgtIou));
    }

    @Override
    public void bindCasProxyGrantingTicket(
            CasSessionScope scope,
            String provider,
            String userId,
            String sessionIndex,
            String pgtId) {
        String sessionKey = sessionKey(scope, sessionIndex);
        String proxyKey = proxyGrantingTicketKey(scope, provider, userId, sessionIndex);
        redisTemplate.opsForValue().set(proxyKey, pgtId);
        redisTemplate.opsForSet().add(sessionKey, "pgt:" + provider + ":" + userId);
        redisTemplate.opsForSet().add(sessionKey, "user:" + userId);
        extendValueKey(proxyKey, sessionKey);

        String userKey = userSessionsKey(scope, userId);
        redisTemplate.opsForZSet().add(userKey, sessionIndex, System.currentTimeMillis());
    }

    @Override
    public String getCasProxyGrantingTicket(
            CasSessionScope scope, String provider, String userId, String tokenDigest) {
        String sessionIndex = redisTemplate.opsForValue().get(tokenSessionKey(tokenDigest));
        if (sessionIndex == null) {
            return null;
        }
        Set<String> entries = redisTemplate.opsForSet().members(sessionKey(scope, sessionIndex));
        if (entries == null || !entries.contains("user:" + userId)) {
            return null;
        }
        return redisTemplate
                .opsForValue()
                .get(proxyGrantingTicketKey(scope, provider, userId, sessionIndex));
    }

    @Override
    public void revokeToken(String token) {
        revokeTokenDigest(TokenUtil.getTokenDigest(token), TokenUtil.getTokenExpireTime(token));
    }

    @Override
    public boolean isTokenRevoked(String token) {
        Boolean exists = redisTemplate.hasKey(revokeKey(TokenUtil.getTokenDigest(token)));
        return Boolean.TRUE.equals(exists);
    }

    private void revokeTokenDigest(String tokenDigest, long expireAt) {
        long ttlMillis = expireAt - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(revokeKey(tokenDigest), "1", Duration.ofMillis(ttlMillis));
    }

    private void extendSessionKey(String key, long expireAt) {
        Long currentTtlSeconds = redisTemplate.getExpire(key);
        Instant targetExpireAt = Instant.ofEpochMilli(expireAt);
        if (currentTtlSeconds == null || currentTtlSeconds < 0) {
            redisTemplate.expireAt(key, targetExpireAt);
            return;
        }
        Instant currentExpireAt = Instant.now().plusSeconds(currentTtlSeconds);
        if (targetExpireAt.isAfter(currentExpireAt)) {
            redisTemplate.expireAt(key, targetExpireAt);
        }
    }

    private void saveTokenSession(String tokenDigest, String sessionIndex, long expireAt) {
        long ttlMillis = expireAt - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate
                .opsForValue()
                .set(tokenSessionKey(tokenDigest), sessionIndex, Duration.ofMillis(ttlMillis));
    }

    private void extendValueKey(String key, String referenceKey) {
        Long ttlSeconds = redisTemplate.getExpire(referenceKey);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return;
        }
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    private String loginCodeKey(String code) {
        return LOGIN_CODE_PREFIX + code;
    }

    private CasLoginContext parseCasLoginContext(String value) {
        cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(value);
        return new CasLoginContext(
                resolveScope(jsonObject.getStr("scope")),
                jsonObject.getStr("provider"),
                jsonObject.getStr("userId"),
                jsonObject.getStr("sessionIndex"),
                jsonObject.getStr("proxyGrantingTicketIou"),
                jsonObject.getLong("tokenExpiresIn"));
    }

    private CasSessionScope resolveScope(String scopeValue) {
        if (scopeValue == null) {
            return null;
        }
        try {
            return CasSessionScope.valueOf(scopeValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String sessionKey(CasSessionScope scope, String sessionIndex) {
        return SESSION_PREFIX + scope.name() + ":" + sessionIndex;
    }

    private String userSessionsKey(CasSessionScope scope, String userId) {
        return USER_SESSIONS_PREFIX + scope.name() + ":" + userId;
    }

    private String proxyGrantingTicketIouKey(String pgtIou) {
        return PGT_IOU_PREFIX + pgtIou;
    }

    private ParsedSessionArtifacts parseSessionArtifacts(Set<String> entries, String sessionIndex) {
        String userId = null;
        List<TokenArtifact> tokenArtifacts = new ArrayList<>();
        List<ProxyGrantingTicketArtifact> proxyGrantingTickets = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split(":", 3);
            if (parts.length < 2) {
                continue;
            }
            if ("user".equals(parts[0])) {
                userId = parts[1];
                continue;
            }
            if ("token".equals(parts[0]) && parts.length == 3) {
                tokenArtifacts.add(parseTokenArtifact(parts[1], parts[2], sessionIndex));
                continue;
            }
            if ("pgt".equals(parts[0]) && parts.length == 3) {
                proxyGrantingTickets.add(new ProxyGrantingTicketArtifact(parts[1], parts[2]));
            }
        }
        return new ParsedSessionArtifacts(userId, tokenArtifacts, proxyGrantingTickets);
    }

    private TokenArtifact parseTokenArtifact(
            String tokenDigest, String expireAt, String sessionIndex) {
        try {
            return new TokenArtifact(tokenDigest, Long.parseLong(expireAt));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Corrupted CAS session token expiration for session " + sessionIndex, e);
        }
    }

    private String proxyGrantingTicketKey(
            CasSessionScope scope, String provider, String userId, String sessionIndex) {
        return PGT_PREFIX + scope.name() + ":" + provider + ":" + userId + ":" + sessionIndex;
    }

    private String tokenSessionKey(String tokenDigest) {
        return TOKEN_SESSION_PREFIX + tokenDigest;
    }

    private String revokeKey(String tokenDigest) {
        return REVOKE_PREFIX + tokenDigest;
    }

    private static final class ParsedSessionArtifacts {

        private final String userId;

        private final List<TokenArtifact> tokenArtifacts;

        private final List<ProxyGrantingTicketArtifact> proxyGrantingTickets;

        private ParsedSessionArtifacts(
                String userId,
                List<TokenArtifact> tokenArtifacts,
                List<ProxyGrantingTicketArtifact> proxyGrantingTickets) {
            this.userId = userId;
            this.tokenArtifacts = tokenArtifacts;
            this.proxyGrantingTickets = proxyGrantingTickets;
        }
    }

    private static final class TokenArtifact {

        private final String tokenDigest;

        private final long expireAt;

        private TokenArtifact(String tokenDigest, long expireAt) {
            this.tokenDigest = tokenDigest;
            this.expireAt = expireAt;
        }
    }

    private static final class ProxyGrantingTicketArtifact {

        private final String provider;

        private final String userId;

        private ProxyGrantingTicketArtifact(String provider, String userId) {
            this.provider = provider;
            this.userId = userId;
        }
    }
}
