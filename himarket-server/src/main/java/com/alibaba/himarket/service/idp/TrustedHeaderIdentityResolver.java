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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.TrustedHeaderConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TrustedHeaderIdentityResolver {

    public Map<String, Object> resolve(HttpServletRequest request, OAuth2Config config) {
        TrustedHeaderConfig trustedHeaderConfig = requireConfig(config);
        assertTrustedProxy(request, trustedHeaderConfig);

        IdentityMapping identityMapping =
                Optional.ofNullable(config.getIdentityMapping()).orElseGet(IdentityMapping::new);
        String userId = getRequiredHeader(request, trustedHeaderConfig.resolveUserIdHeader());
        String userName = getOptionalHeader(request, trustedHeaderConfig.resolveUserNameHeader());
        String email = getOptionalHeader(request, trustedHeaderConfig.resolveEmailHeader());
        List<String> groups =
                getMultiValueHeader(
                        request,
                        trustedHeaderConfig.resolveGroupsHeader(),
                        trustedHeaderConfig.resolveValueSeparator());
        List<String> roles =
                getMultiValueHeader(
                        request,
                        trustedHeaderConfig.resolveRolesHeader(),
                        trustedHeaderConfig.resolveValueSeparator());

        String userIdField = resolveField(identityMapping.getUserIdField(), IdpConstants.SUBJECT);
        String userNameField = resolveField(identityMapping.getUserNameField(), IdpConstants.NAME);
        String emailField = resolveField(identityMapping.getEmailField(), IdpConstants.EMAIL);
        String finalUserName = StrUtil.blankToDefault(userName, userId);

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put(IdpConstants.SUBJECT, userId);
        identity.put(IdpConstants.NAME, finalUserName);
        identity.put(userIdField, userId);
        identity.put(userNameField, finalUserName);
        if (StrUtil.isNotBlank(email)) {
            identity.put(IdpConstants.EMAIL, email);
            identity.put(emailField, email);
        }
        if (CollUtil.isNotEmpty(groups)) {
            identity.put("groups", groups);
        }
        if (CollUtil.isNotEmpty(roles)) {
            identity.put("roles", roles);
        }
        identity.put("trusted_proxy_remote_addr", request.getRemoteAddr());
        return identity;
    }

    private TrustedHeaderConfig requireConfig(OAuth2Config config) {
        TrustedHeaderConfig trustedHeaderConfig = config.getTrustedHeaderConfig();
        if (trustedHeaderConfig == null || !trustedHeaderConfig.resolveEnabled()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format(
                            "OAuth2 config {} does not support trusted header login",
                            config.getProvider()));
        }
        return trustedHeaderConfig;
    }

    private void assertTrustedProxy(
            HttpServletRequest request, TrustedHeaderConfig trustedHeaderConfig) {
        String remoteAddr = request.getRemoteAddr();
        boolean cidrMatched =
                Optional.ofNullable(trustedHeaderConfig.getTrustedProxyCidrs())
                        .orElseGet(List::of)
                        .stream()
                        .filter(StrUtil::isNotBlank)
                        .anyMatch(cidr -> ipMatchesCidr(remoteAddr, cidr));
        boolean hostMatched =
                Optional.ofNullable(trustedHeaderConfig.getTrustedProxyHosts())
                        .orElseGet(List::of)
                        .stream()
                        .filter(StrUtil::isNotBlank)
                        .anyMatch(
                                host ->
                                        StrUtil.equalsIgnoreCase(host, request.getRemoteHost())
                                                || StrUtil.equalsIgnoreCase(host, remoteAddr));
        if (!cidrMatched && !hostMatched) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    StrUtil.format("Trusted header login rejected remote address {}", remoteAddr));
        }
    }

    private String getRequiredHeader(HttpServletRequest request, String headerName) {
        String value = getOptionalHeader(request, headerName);
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format("Trusted header {} is missing", headerName));
        }
        return value;
    }

    private String getOptionalHeader(HttpServletRequest request, String headerName) {
        Enumeration<String> headers = request.getHeaders(headerName);
        if (headers == null) {
            return null;
        }
        while (headers.hasMoreElements()) {
            String value = StrUtil.trim(headers.nextElement());
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> getMultiValueHeader(
            HttpServletRequest request, String headerName, String valueSeparator) {
        Enumeration<String> headers = request.getHeaders(headerName);
        if (headers == null) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        while (headers.hasMoreElements()) {
            String rawValue = StrUtil.trim(headers.nextElement());
            if (StrUtil.isBlank(rawValue)) {
                continue;
            }
            String[] segments = StrUtil.splitToArray(rawValue, valueSeparator);
            for (String segment : segments) {
                String value = StrUtil.trim(segment);
                if (StrUtil.isNotBlank(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private String resolveField(String configuredField, String defaultField) {
        return StrUtil.blankToDefault(configuredField, defaultField);
    }

    private boolean ipMatchesCidr(String ipAddress, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                return false;
            }
            InetAddress networkAddress = InetAddress.getByName(parts[0]);
            InetAddress remoteAddress = InetAddress.getByName(ipAddress);
            if (networkAddress.getAddress().length != remoteAddress.getAddress().length) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1]);
            int totalBits = networkAddress.getAddress().length * 8;
            if (prefixLength < 0 || prefixLength > totalBits) {
                return false;
            }
            BigInteger mask =
                    prefixLength == 0
                            ? BigInteger.ZERO
                            : BigInteger.ONE
                                    .shiftLeft(totalBits)
                                    .subtract(BigInteger.ONE)
                                    .shiftRight(totalBits - prefixLength)
                                    .shiftLeft(totalBits - prefixLength);
            BigInteger network = new BigInteger(1, networkAddress.getAddress());
            BigInteger remote = new BigInteger(1, remoteAddress.getAddress());
            return network.and(mask).equals(remote.and(mask));
        } catch (NumberFormatException | UnknownHostException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("Trusted proxy CIDR {} is invalid", cidr));
        }
    }
}
