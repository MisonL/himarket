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
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.idp.IdpState;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class IdpStateCodec {

    public String encode(IdpState state) {
        if (state == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing state");
        }
        String json = JSONUtil.toJsonStr(state);
        return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public IdpState decode(String encodedState) {
        if (StrUtil.isBlank(encodedState)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing state");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedState);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return JSONUtil.toBean(json, IdpState.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid state");
        }
    }
}
