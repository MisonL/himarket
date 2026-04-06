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
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.PortalService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortalFrontendUrlResolver {

    private final PortalService portalService;

    private final ContextHolder contextHolder;

    public String buildCallbackUrl(String callbackPath) {
        String baseUrl = getFrontendBaseUrl();
        return baseUrl + StrUtil.addPrefixIfNot(callbackPath, "/");
    }

    public String getFrontendBaseUrl() {
        String portalId = contextHolder.getPortal();
        if (StrUtil.isBlank(portalId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Portal cannot be resolved for authentication request");
        }

        PortalResult portal = portalService.getPortal(portalId);
        String frontendRedirectUrl =
                portal.getPortalSettingConfig() == null
                        ? null
                        : portal.getPortalSettingConfig().getFrontendRedirectUrl();
        if (StrUtil.isBlank(frontendRedirectUrl)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Portal frontend redirect URL is not configured");
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
                    "Portal frontend redirect URL must be an absolute http/https URL");
        }
    }
}
