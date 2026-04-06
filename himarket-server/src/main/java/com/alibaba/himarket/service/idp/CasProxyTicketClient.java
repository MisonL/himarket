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
import com.alibaba.himarket.service.gateway.factory.HTTPClientFactory;
import com.alibaba.himarket.support.portal.CasConfig;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RequiredArgsConstructor
public class CasProxyTicketClient {

    private final RestTemplate restTemplate = HTTPClientFactory.createRestTemplate();

    private final CasProxyTicketParser casProxyTicketParser;

    public String requestProxyTicket(
            CasConfig config, String proxyGrantingTicket, String targetService) {
        validateTargetService(config, targetService);
        String proxyUrl =
                UriComponentsBuilder.fromUriString(buildProxyUrl(config))
                        .queryParam(IdpConstants.PGT, proxyGrantingTicket)
                        .queryParam(IdpConstants.TARGET_SERVICE, targetService)
                        .build()
                        .toUriString();
        try {
            String response =
                    restTemplate.exchange(proxyUrl, HttpMethod.GET, null, String.class).getBody();
            return casProxyTicketParser.parse(response);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException | IllegalArgumentException e) {
            log.error(
                    "Failed to request CAS proxy ticket for provider {}", config.getProvider(), e);
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS proxy ticket request failed");
        }
    }

    private void validateTargetService(CasConfig config, String targetService) {
        String pattern = config.resolveProxyConfig().getTargetServicePattern();
        if (StrUtil.isBlank(pattern)) {
            return;
        }
        try {
            if (!Pattern.matches(pattern, targetService)) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "CAS proxy target service is not allowed by current provider policy");
            }
        } catch (PatternSyntaxException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "CAS proxy target service pattern is invalid");
        }
    }

    private String buildProxyUrl(CasConfig config) {
        String endpoint =
                StrUtil.blankToDefault(
                        config.resolveProxyConfig().getProxyEndpoint(),
                        IdpConstants.CAS_PROXY_PATH);
        if (StrUtil.startWithAnyIgnoreCase(endpoint, "http://", "https://")) {
            return endpoint;
        }
        return StrUtil.removeSuffix(config.getServerUrl(), "/")
                + StrUtil.addPrefixIfNot(endpoint, "/");
    }
}
