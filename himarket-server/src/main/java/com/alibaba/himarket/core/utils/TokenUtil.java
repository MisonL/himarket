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

package com.alibaba.himarket.core.utils;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.service.idp.session.AuthSessionStore;
import com.alibaba.himarket.support.common.User;
import com.alibaba.himarket.support.enums.UserType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class TokenUtil {

    private static final Pattern SHORT_DURATION_PATTERN =
            Pattern.compile("\\d+[smhd]", Pattern.CASE_INSENSITIVE);

    private static String getJwtSecret() {
        String jwtSecret = resolveProperty("jwt.secret");
        if (StrUtil.isBlank(jwtSecret)) {
            throw new IllegalStateException("JWT secret cannot be empty");
        }
        return jwtSecret;
    }

    private static long getJwtExpireMillis() {
        String expiration = resolveProperty("jwt.expiration");
        if (StrUtil.isBlank(expiration)) {
            throw new IllegalStateException("JWT expiration is empty");
        }

        if (SHORT_DURATION_PATTERN.matcher(expiration).matches()) {
            String upper = expiration.toUpperCase();
            if (upper.endsWith("D")) {
                return Duration.parse("P" + upper).toMillis();
            }
            return Duration.parse("PT" + upper).toMillis();
        }
        return Long.parseLong(expiration);
    }

    public static String generateAdminToken(String userId) {
        return generateToken(UserType.ADMIN, userId);
    }

    public static String generateDeveloperToken(String userId) {
        return generateToken(UserType.DEVELOPER, userId);
    }

    private static String generateToken(UserType userType, String userId) {
        long now = System.currentTimeMillis();

        Map<String, String> claims =
                MapUtil.<String, String>builder()
                        .put(CommonConstants.USER_TYPE, userType.name())
                        .put(CommonConstants.USER_ID, userId)
                        .put(JwtConstants.PAYLOAD_JTI, IdUtil.fastSimpleUUID())
                        .build();

        return JWT.create()
                .addPayloads(claims)
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(now + getJwtExpireMillis()))
                .setSigner(JWTSignerUtil.hs256(getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                .sign();
    }

    public static User parseUser(String token) {
        JWT jwt = JWTUtil.parseToken(token);

        boolean isValid =
                jwt.setSigner(JWTSignerUtil.hs256(getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                        .verify();
        if (!isValid) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        Object expObj = jwt.getPayloads().get(JWT.EXPIRES_AT);
        if (ObjectUtil.isNotNull(expObj)) {
            long expireAt = Long.parseLong(expObj.toString());
            if (expireAt * 1000 <= System.currentTimeMillis()) {
                throw new IllegalArgumentException("Token has expired");
            }
        }

        return jwt.getPayloads().toBean(User.class);
    }

    public static String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);

        String token = null;
        if (authHeader != null && authHeader.startsWith(CommonConstants.BEARER_PREFIX)) {
            token = authHeader.substring(CommonConstants.BEARER_PREFIX.length());
        }

        if (StrUtil.isBlank(token)) {
            token =
                    Optional.ofNullable(request.getCookies())
                            .flatMap(
                                    cookies ->
                                            Arrays.stream(cookies)
                                                    .filter(
                                                            cookie ->
                                                                    CommonConstants
                                                                            .AUTH_TOKEN_COOKIE
                                                                            .equals(cookie.getName()))
                                                    .map(Cookie::getValue)
                                                    .findFirst())
                            .orElse(null);
        }
        if (StrUtil.isBlank(token)) {
            return null;
        }

        return token;
    }

    public static String extractTokenFromRequest(HttpServletRequest request) {
        return getTokenFromRequest(request);
    }

    public static long getTokenExpireTime(String token) {
        JWT jwt = JWTUtil.parseToken(token);
        Object expObj = jwt.getPayloads().get(JWT.EXPIRES_AT);
        if (ObjectUtil.isNotNull(expObj)) {
            return Long.parseLong(expObj.toString()) * 1000;
        }
        return System.currentTimeMillis() + getJwtExpireMillis();
    }

    public static long getTokenExpireTimeMillis(String token) {
        return getTokenExpireTime(token);
    }

    public static String getTokenDigest(String token) {
        return DigestUtil.sha256Hex(token);
    }

    public static Duration getTokenTtl(String token) {
        long ttlMillis = getTokenExpireTime(token) - System.currentTimeMillis();
        return ttlMillis <= 0 ? Duration.ZERO : Duration.ofMillis(ttlMillis);
    }

    public static void revokeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        authSessionStore().revokeToken(token);
    }

    public static void revokeToken(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (StrUtil.isNotBlank(token)) {
            revokeToken(token);
        }
    }

    public static boolean isTokenRevoked(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        return authSessionStore().isTokenRevoked(token);
    }

    public static long getTokenExpiresIn() {
        return getJwtExpireMillis() / 1000;
    }

    private static String resolveProperty(String key) {
        String value = SpringUtil.getProperty(key);
        if (StrUtil.isBlank(value)) {
            value = System.getProperty(key);
        }
        return value;
    }

    private static AuthSessionStore authSessionStore() {
        return SpringUtil.getBean(AuthSessionStore.class);
    }
}
