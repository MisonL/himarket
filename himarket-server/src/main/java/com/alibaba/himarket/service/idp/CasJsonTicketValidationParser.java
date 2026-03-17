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
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CasJsonTicketValidationParser {

    public Map<String, Object> parse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS validation returned empty response");
        }
        try {
            JSONObject serviceResponse =
                    JSONUtil.parseObj(responseBody).getJSONObject("serviceResponse");
            if (serviceResponse == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "CAS validation response is missing payload");
            }

            JSONObject success = serviceResponse.getJSONObject("authenticationSuccess");
            if (success == null) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        extractFailureMessage(serviceResponse, "CAS ticket validation failed"));
            }
            return extractAttributes(success);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse CAS JSON validation response", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to parse CAS validation response");
        }
    }

    private Map<String, Object> extractAttributes(JSONObject success) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String user = success.getStr("user");
        if (StrUtil.isNotBlank(user)) {
            attributes.put("user", user.trim());
        }
        String proxyGrantingTicket = success.getStr(IdpConstants.PROXY_GRANTING_TICKET);
        if (StrUtil.isNotBlank(proxyGrantingTicket)) {
            attributes.put(IdpConstants.PROXY_GRANTING_TICKET, proxyGrantingTicket.trim());
        }

        JSONObject attributeRoot = success.getJSONObject("attributes");
        if (attributeRoot == null) {
            return attributes;
        }
        for (String key : attributeRoot.keySet()) {
            Object value = normalizeValue(attributeRoot.get(key));
            if (value != null && StrUtil.isNotBlank(value.toString())) {
                attributes.put(key, value);
            }
        }
        return attributes;
    }

    private String extractFailureMessage(JSONObject serviceResponse, String defaultMessage) {
        Object failure = serviceResponse.get("authenticationFailure");
        if (failure instanceof JSONObject failureObject) {
            String code = failureObject.getStr("code");
            String description = failureObject.getStr("description");
            String message =
                    CollUtil.newArrayList(code, description).stream()
                            .filter(StrUtil::isNotBlank)
                            .collect(Collectors.joining(": "));
            return StrUtil.blankToDefault(message, defaultMessage);
        }
        Object normalizedFailure = normalizeValue(failure);
        return StrUtil.blankToDefault(
                normalizedFailure == null ? null : normalizedFailure.toString(), defaultMessage);
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONArray array) {
            return array.stream()
                    .map(this::normalizeValue)
                    .filter(v -> v != null && StrUtil.isNotBlank(v.toString()))
                    .collect(Collectors.toList());
        }
        if (value instanceof JSONObject object) {
            return StrUtil.blankToDefault(object.getStr("value"), object.toString());
        }
        return StrUtil.trim(String.valueOf(value));
    }
}
