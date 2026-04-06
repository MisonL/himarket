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
import com.alibaba.himarket.config.AdminAuthConfig;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminFrontendUrlResolver {

    private final AdminAuthConfig adminAuthConfig;

    public String buildCallbackUrl(String callbackPath) {
        String baseUrl = getFrontendBaseUrl();
        return baseUrl + StrUtil.addPrefixIfNot(callbackPath, "/");
    }

    public String getFrontendBaseUrl() {
        String frontendRedirectUrl = adminAuthConfig.getFrontendRedirectUrl();
        if (StrUtil.isBlank(frontendRedirectUrl)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Admin frontend redirect URL is not configured");
        }
        validateAbsoluteUrl(frontendRedirectUrl);
        return StrUtil.removeSuffix(frontendRedirectUrl, "/");
    }

    private void validateAbsoluteUrl(String frontendRedirectUrl) {
        try {
            URI uri = URI.create(frontendRedirectUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Unsupported scheme");
            }
            if (StrUtil.isBlank(uri.getHost())) {
                throw new IllegalArgumentException("Missing host");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "Admin frontend redirect URL must be an absolute http/https URL");
        }
    }
}
