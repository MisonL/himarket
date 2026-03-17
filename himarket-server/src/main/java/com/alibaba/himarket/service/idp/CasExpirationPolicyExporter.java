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
import com.alibaba.himarket.support.portal.cas.CasExpirationPolicyConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class CasExpirationPolicyExporter {

    private static final String EXPIRATION_POLICY_CLASS =
            "org.apereo.cas.services.DefaultRegisteredServiceExpirationPolicy";

    Map<String, Object> export(CasExpirationPolicyConfig expirationPolicyConfig) {
        if (expirationPolicyConfig == null
                || (StrUtil.isBlank(expirationPolicyConfig.getExpirationDate())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getDeleteWhenExpired())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getNotifyWhenExpired())
                        && !Boolean.TRUE.equals(expirationPolicyConfig.getNotifyWhenDeleted()))) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@class", EXPIRATION_POLICY_CLASS);
        if (StrUtil.isNotBlank(expirationPolicyConfig.getExpirationDate())) {
            policy.put("expirationDate", expirationPolicyConfig.getExpirationDate());
        }
        policy.put(
                "deleteWhenExpired",
                Optional.ofNullable(expirationPolicyConfig.getDeleteWhenExpired()).orElse(false));
        policy.put(
                "notifyWhenExpired",
                Optional.ofNullable(expirationPolicyConfig.getNotifyWhenExpired()).orElse(false));
        policy.put(
                "notifyWhenDeleted",
                Optional.ofNullable(expirationPolicyConfig.getNotifyWhenDeleted()).orElse(false));
        return policy;
    }
}
