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

    private static final String SESSION_PREFIX = "hm:auth:cas:session:";

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
        return value == null ? null : JSONUtil.toBean(value, CasLoginContext.class);
    }

    @Override
    public void bindCasSessionToken(CasSessionScope scope, String sessionIndex, String token) {
        String key = sessionKey(scope, sessionIndex);
        long expireAt = TokenUtil.getTokenExpireTime(token);
        String payload = TokenUtil.getTokenDigest(token) + ":" + expireAt;
        redisTemplate.opsForSet().add(key, payload);
        extendSessionKey(key, expireAt);
    }

    @Override
    public int revokeCasSession(CasSessionScope scope, String sessionIndex) {
        String key = sessionKey(scope, sessionIndex);
        Set<String> entries = redisTemplate.opsForSet().members(key);
        redisTemplate.delete(key);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int revoked = 0;
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                revokeTokenDigest(parts[0], Long.parseLong(parts[1]));
                revoked++;
            } catch (NumberFormatException ignored) {
            }
        }
        return revoked;
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

    private String sessionKey(CasSessionScope scope, String sessionIndex) {
        return SESSION_PREFIX + scope.name() + ":" + sessionIndex;
    }

    private String revokeKey(String tokenDigest) {
        return REVOKE_PREFIX + tokenDigest;
    }
}
