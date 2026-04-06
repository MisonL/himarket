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
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisAuthSessionStore implements AuthSessionStore {

    private static final String LOGIN_CODE_PREFIX = "hm:auth:cas:code:";

    private static final String USER_SESSIONS_PREFIX = "hm:auth:cas:user-sessions:";

    private static final String SESSION_PREFIX = "hm:auth:cas:session:";

    private static final String PGT_IOU_PREFIX = "hm:auth:cas:pgt-iou:";

    private static final String PGT_PREFIX = "hm:auth:cas:pgt:";

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
        return value == null ? null : parseCasLoginContext(value);
    }

    @Override
    public void bindCasSessionToken(
            CasSessionScope scope, String userId, String sessionIndex, String token) {
        String key = sessionKey(scope, sessionIndex);
        long expireAt = TokenUtil.getTokenExpireTime(token);
        String payload = "token:" + TokenUtil.getTokenDigest(token) + ":" + expireAt;
        redisTemplate.opsForSet().add(key, payload);
        redisTemplate.opsForSet().add(key, "user:" + userId);
        extendSessionKey(key, expireAt);

        String userKey = userSessionsKey(scope, userId);
        redisTemplate.opsForSet().add(userKey, sessionIndex);
        extendSessionKey(userKey, expireAt);
    }

    @Override
    public int revokeCasSession(CasSessionScope scope, String sessionIndex) {
        String key = sessionKey(scope, sessionIndex);
        Set<String> entries = redisTemplate.opsForSet().members(key);
        redisTemplate.delete(key);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        String userId = null;
        int revoked = 0;
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
                try {
                    revokeTokenDigest(parts[1], Long.parseLong(parts[2]));
                    revoked++;
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if ("pgt".equals(parts[0]) && parts.length == 3) {
                redisTemplate.delete(proxyGrantingTicketKey(scope, parts[1], parts[2]));
            }
        }

        if (userId != null) {
            redisTemplate.opsForSet().remove(userSessionsKey(scope, userId), sessionIndex);
        }

        return revoked;
    }

    @Override
    public int revokeUserSessions(CasSessionScope scope, String userId) {
        String userKey = userSessionsKey(scope, userId);
        Set<String> sessionIndices = redisTemplate.opsForSet().members(userKey);
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
        redisTemplate.opsForValue().set(proxyGrantingTicketKey(scope, provider, userId), pgtId);
        redisTemplate.opsForSet().add(sessionKey, "pgt:" + provider + ":" + userId);
        redisTemplate.opsForSet().add(sessionKey, "user:" + userId);

        String userKey = userSessionsKey(scope, userId);
        redisTemplate.opsForSet().add(userKey, sessionIndex);
    }

    @Override
    public String getCasProxyGrantingTicket(CasSessionScope scope, String provider, String userId) {
        return redisTemplate.opsForValue().get(proxyGrantingTicketKey(scope, provider, userId));
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

    private String proxyGrantingTicketKey(CasSessionScope scope, String provider, String userId) {
        return PGT_PREFIX + scope.name() + ":" + provider + ":" + userId;
    }

    private String revokeKey(String tokenDigest) {
        return REVOKE_PREFIX + tokenDigest;
    }
}
