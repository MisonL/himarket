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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RequiredArgsConstructor
public class CasTicketValidator {

    private final RestTemplate restTemplate = HTTPClientFactory.createRestTemplate();

    private final CasTicketValidationParser casTicketValidationParser;

    public Map<String, Object> validate(CasConfig config, String ticket, String serviceUrl) {
        String validateUrl =
                UriComponentsBuilder.fromUriString(buildValidateUrl(config))
                        .queryParam(IdpConstants.SERVICE, serviceUrl)
                        .queryParam(IdpConstants.TICKET, ticket)
                        .build()
                        .toUriString();
        try {
            String response =
                    restTemplate
                            .exchange(validateUrl, HttpMethod.GET, null, String.class)
                            .getBody();
            return casTicketValidationParser.parse(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate CAS ticket for provider {}", config.getProvider(), e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "CAS ticket validation failed");
        }
    }

    private String buildValidateUrl(CasConfig config) {
        String endpoint =
                StrUtil.blankToDefault(
                        config.getValidateEndpoint(), IdpConstants.CAS_VALIDATE_PATH);
        return joinUrl(config.getServerUrl(), endpoint);
    }

    private String joinUrl(String baseUrl, String path) {
        return StrUtil.removeSuffix(baseUrl, "/") + StrUtil.addPrefixIfNot(path, "/");
    }
}
