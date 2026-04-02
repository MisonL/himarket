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

package com.alibaba.himarket.service.idp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class IdpStateCookie {

    private IdpStateCookie() {}

    public static void writeOidcStateCookie(
            HttpServletRequest request, HttpServletResponse response, String state) {
        writeCookie(
                request,
                response,
                IdpConstants.OIDC_STATE_COOKIE_NAME,
                state,
                IdpConstants.IDP_STATE_COOKIE_MAX_AGE_SECONDS);
    }

    public static void clearOidcStateCookie(
            HttpServletRequest request, HttpServletResponse response) {
        clearCookie(request, response, IdpConstants.OIDC_STATE_COOKIE_NAME);
    }

    public static void assertOidcStateCookieMatches(HttpServletRequest request, String state) {
        assertCookieMatches(request, IdpConstants.OIDC_STATE_COOKIE_NAME, state);
    }

    public static void writeCasStateCookie(
            HttpServletRequest request, HttpServletResponse response, String state) {
        writeCookie(
                request,
                response,
                IdpConstants.CAS_STATE_COOKIE_NAME,
                state,
                IdpConstants.IDP_STATE_COOKIE_MAX_AGE_SECONDS);
    }

    public static void clearCasStateCookie(
            HttpServletRequest request, HttpServletResponse response) {
        clearCookie(request, response, IdpConstants.CAS_STATE_COOKIE_NAME);
    }

    public static void assertCasStateCookieMatches(HttpServletRequest request, String state) {
        assertCookieMatches(request, IdpConstants.CAS_STATE_COOKIE_NAME, state);
    }

    public static void writeOauth2StateCookie(
            HttpServletRequest request, HttpServletResponse response, String state) {
        writeCookie(
                request,
                response,
                IdpConstants.OAUTH2_STATE_COOKIE_NAME,
                state,
                IdpConstants.IDP_STATE_COOKIE_MAX_AGE_SECONDS);
    }

    public static void clearOauth2StateCookie(
            HttpServletRequest request, HttpServletResponse response) {
        clearCookie(request, response, IdpConstants.OAUTH2_STATE_COOKIE_NAME);
    }

    public static void assertOauth2StateCookieMatches(HttpServletRequest request, String state) {
        assertCookieMatches(request, IdpConstants.OAUTH2_STATE_COOKIE_NAME, state);
    }

    public static void writeAdminCasStateCookie(
            HttpServletRequest request, HttpServletResponse response, String state) {
        writeCookie(
                request,
                response,
                IdpConstants.ADMIN_CAS_STATE_COOKIE_NAME,
                state,
                IdpConstants.IDP_STATE_COOKIE_MAX_AGE_SECONDS);
    }

    public static void clearAdminCasStateCookie(
            HttpServletRequest request, HttpServletResponse response) {
        clearCookie(request, response, IdpConstants.ADMIN_CAS_STATE_COOKIE_NAME);
    }

    public static void assertAdminCasStateCookieMatches(HttpServletRequest request, String state) {
        assertCookieMatches(request, IdpConstants.ADMIN_CAS_STATE_COOKIE_NAME, state);
    }

    private static void assertCookieMatches(
            HttpServletRequest request, String cookieName, String expected) {
        String actual = readCookieValue(request, cookieName);
        if (StrUtil.isBlank(actual) || !StrUtil.equals(actual, expected)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid state");
        }
    }

    private static void clearCookie(
            HttpServletRequest request, HttpServletResponse response, String cookieName) {
        writeCookie(request, response, cookieName, "", 0);
    }

    private static void writeCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String cookieName,
            String value,
            int maxAgeSeconds) {
        boolean isSecure = isSecureRequest(request);
        ResponseCookie.ResponseCookieBuilder cookieBuilder =
                ResponseCookie.from(cookieName, value)
                        .httpOnly(true)
                        .secure(isSecure)
                        .path(IdpConstants.IDP_STATE_COOKIE_PATH)
                        .maxAge(maxAgeSeconds);

        if (isSecure) {
            cookieBuilder.sameSite(IdpConstants.IDP_STATE_COOKIE_SAMESITE);
        } else {
            // Chrome-based headless browsers (DevTools/Playwright) may drop Lax cookies
            // on cross-origin redirects if not Secure=true. On localhost, we can relax this.
            cookieBuilder.sameSite("Lax");
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
    }

    private static boolean isSecureRequest(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(proto);
    }

    private static String readCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
