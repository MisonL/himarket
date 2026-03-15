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
import com.alibaba.himarket.support.portal.cas.CasProtocolVersion;
import com.alibaba.himarket.support.portal.cas.CasValidationConfig;
import com.alibaba.himarket.support.portal.cas.CasValidationResponseFormat;
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

    private final CasJsonTicketValidationParser casJsonTicketValidationParser;

    public Map<String, Object> validate(CasConfig config, String ticket, String serviceUrl) {
        return validate(config, ticket, serviceUrl, null);
    }

    public Map<String, Object> validate(
            CasConfig config, String ticket, String serviceUrl, String proxyCallbackUrl) {
        CasValidationConfig validationConfig = config.resolveValidationConfig();
        String validateUrl =
                UriComponentsBuilder.fromUriString(buildValidateUrl(config))
                        .queryParam(IdpConstants.SERVICE, serviceUrl)
                        .queryParam(IdpConstants.TICKET, ticket)
                        .queryParamIfPresent(
                                IdpConstants.PGT_URL,
                                java.util.Optional.ofNullable(proxyCallbackUrl))
                        .queryParamIfPresent(
                                IdpConstants.FORMAT,
                                java.util.Optional.ofNullable(
                                        resolveFormatQueryParam(validationConfig)))
                        .build()
                        .toUriString();
        try {
            String response =
                    restTemplate
                            .exchange(validateUrl, HttpMethod.GET, null, String.class)
                            .getBody();
            return parseResponse(validationConfig, response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate CAS ticket for provider {}", config.getProvider(), e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "CAS ticket validation failed");
        }
    }

    private String buildValidateUrl(CasConfig config) {
        String endpoint =
                StrUtil.blankToDefault(config.getValidateEndpoint(), defaultValidatePath(config));
        return joinUrl(config.getServerUrl(), endpoint);
    }

    private String defaultValidatePath(CasConfig config) {
        return switch (config.resolveValidationConfig().getProtocolVersion()) {
            case CAS1 -> IdpConstants.CAS1_VALIDATE_PATH;
            case CAS2 -> IdpConstants.CAS2_VALIDATE_PATH;
            case SAML1 -> IdpConstants.CAS_SAML1_VALIDATE_PATH;
            case CAS3 -> IdpConstants.CAS_VALIDATE_PATH;
        };
    }

    private String resolveFormatQueryParam(CasValidationConfig validationConfig) {
        if (validationConfig.getResponseFormat() != CasValidationResponseFormat.JSON) {
            return null;
        }
        if (validationConfig.getProtocolVersion() == CasProtocolVersion.CAS1
                || validationConfig.getProtocolVersion() == CasProtocolVersion.SAML1) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "CAS JSON validation format is not supported by selected protocol version");
        }
        return "JSON";
    }

    private Map<String, Object> parseResponse(
            CasValidationConfig validationConfig, String responseBody) {
        if (validationConfig.getResponseFormat() == CasValidationResponseFormat.JSON) {
            return casJsonTicketValidationParser.parse(responseBody);
        }
        if (validationConfig.getProtocolVersion() == CasProtocolVersion.CAS1) {
            return parseCas1Response(responseBody);
        }
        throwIfUnsupported(validationConfig);
        return casTicketValidationParser.parse(responseBody);
    }

    private Map<String, Object> parseCas1Response(String responseBody) {
        String[] lines = StrUtil.splitToArray(StrUtil.blankToDefault(responseBody, ""), '\n');
        if (lines.length >= 2 && "yes".equalsIgnoreCase(StrUtil.trim(lines[0]))) {
            return Map.of("user", StrUtil.trim(lines[1]));
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "CAS ticket validation failed");
    }

    private void throwIfUnsupported(CasValidationConfig validationConfig) {
        if (validationConfig.getProtocolVersion() == CasProtocolVersion.SAML1) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "CAS SAML1 validation is not implemented yet");
        }
    }

    private String joinUrl(String baseUrl, String path) {
        if (StrUtil.startWithAnyIgnoreCase(path, "http://", "https://")) {
            return path;
        }
        return StrUtil.removeSuffix(baseUrl, "/") + StrUtil.addPrefixIfNot(path, "/");
    }
}
