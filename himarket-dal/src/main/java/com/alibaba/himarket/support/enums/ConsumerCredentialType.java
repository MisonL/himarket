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

package com.alibaba.himarket.support.enums;

import cn.hutool.core.util.StrUtil;

public enum ConsumerCredentialType {
    API_KEY,
    HMAC,
    JWT;

    public boolean isApiKeyCompatible() {
        return this == API_KEY;
    }

    public static ConsumerCredentialType fromHigressAuthType(String authType) {
        if (StrUtil.isBlank(authType)) {
            return API_KEY;
        }

        return switch (authType.trim().toUpperCase()) {
            case "KEY-AUTH", "API_KEY" -> API_KEY;
            case "HMAC-AUTH", "HMAC" -> HMAC;
            case "JWT-AUTH", "JWT" -> JWT;
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported Higress auth type: " + authType);
        };
    }
}
