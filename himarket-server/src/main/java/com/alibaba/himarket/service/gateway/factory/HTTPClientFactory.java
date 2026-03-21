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

package com.alibaba.himarket.service.gateway.factory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Slf4j
public class HTTPClientFactory {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    public static RestTemplate createRestTemplate() {
        return createRestTemplate(
                "http.client.connect-timeout",
                DEFAULT_CONNECT_TIMEOUT,
                "http.client.read-timeout",
                DEFAULT_READ_TIMEOUT);
    }

    public static RestTemplate createRestTemplate(
            String connectTimeoutPropertyName,
            Duration defaultConnectTimeout,
            String readTimeoutPropertyName,
            Duration defaultReadTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                resolveDuration(connectTimeoutPropertyName, defaultConnectTimeout))
                        .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(resolveDuration(readTimeoutPropertyName, defaultReadTimeout));

        return new RestTemplate(factory);
    }

    private static Duration resolveDuration(String propertyName, Duration defaultValue) {
        String raw = SpringUtil.getProperty(propertyName);
        if (StrUtil.isBlank(raw)) {
            return defaultValue;
        }
        try {
            return Duration.parse(raw);
        } catch (Exception e) {
            log.warn(
                    "Invalid duration property {}={}, fallback to {}",
                    propertyName,
                    raw,
                    defaultValue);
            return defaultValue;
        }
    }
}
