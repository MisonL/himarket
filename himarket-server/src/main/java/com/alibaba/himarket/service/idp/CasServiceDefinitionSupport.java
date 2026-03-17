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
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class CasServiceDefinitionSupport {

    private CasServiceDefinitionSupport() {}

    static List<Object> typedCollection(String typeName, List<String> values) {
        return List.of(typeName, values);
    }

    static Map<String, String> resolveStringMap(Map<String, String> primary, String jsonFallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        if (StrUtil.isBlank(jsonFallback)) {
            return Map.of();
        }
        JSONObject jsonObject = JSONUtil.parseObj(jsonFallback);
        Map<String, String> resolved = new LinkedHashMap<>();
        jsonObject.forEach(
                (key, value) -> {
                    if (StrUtil.isNotBlank(key)
                            && value != null
                            && StrUtil.isNotBlank(value.toString())) {
                        resolved.put(key, value.toString());
                    }
                });
        return resolved;
    }

    static Map<String, List<String>> resolveStringListMap(
            Map<String, List<String>> primary, String jsonFallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        if (StrUtil.isBlank(jsonFallback)) {
            return Map.of();
        }
        JSONObject jsonObject = JSONUtil.parseObj(jsonFallback);
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        jsonObject.forEach(
                (key, value) -> {
                    if (StrUtil.isBlank(key) || value == null) {
                        return;
                    }
                    if (value instanceof Iterable<?> iterable) {
                        List<String> values = new ArrayList<>();
                        iterable.forEach(
                                item -> {
                                    if (item != null && StrUtil.isNotBlank(item.toString())) {
                                        values.add(item.toString());
                                    }
                                });
                        if (!values.isEmpty()) {
                            resolved.put(key, values);
                        }
                        return;
                    }
                    if (StrUtil.isNotBlank(value.toString())) {
                        resolved.put(key, List.of(value.toString()));
                    }
                });
        return resolved;
    }

    static void addIfPresent(LinkedHashSet<String> attributes, String value) {
        if (StrUtil.isNotBlank(value)) {
            attributes.add(value);
        }
    }
}
