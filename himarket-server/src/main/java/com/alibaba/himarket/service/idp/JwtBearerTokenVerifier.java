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
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtBearerTokenVerifier {

    public Jwt verify(String jwtToken, JwtBearerConfig config) {
        if (StrUtil.isBlank(config.getJwkSetUri())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "JWT bearer config missing JWK set URI");
        }
        try {
            return createDecoder(config).decode(jwtToken);
        } catch (JwtException e) {
            log.error("Failed to verify JWT bearer token", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JWT");
        }
    }

    private JwtDecoder createDecoder(JwtBearerConfig config) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.getJwkSetUri()).build();
        decoder.setJwtValidator(buildValidator(config));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> buildValidator(JwtBearerConfig config) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (StrUtil.isNotBlank(config.getIssuer())) {
            validators.add(JwtValidators.createDefaultWithIssuer(config.getIssuer()));
        } else {
            validators.add(JwtValidators.createDefault());
        }
        validators.add(validateAudience(config.getAudiences()));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private OAuth2TokenValidator<Jwt> validateAudience(List<String> expectedAudiences) {
        return token -> {
            if (CollUtil.isEmpty(expectedAudiences)) {
                return OAuth2TokenValidatorResult.success();
            }

            List<String> audiences = token.getAudience();
            if (audiences != null && expectedAudiences.stream().anyMatch(audiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            ErrorCode.INVALID_REQUEST.name(),
                            "JWT audience does not match expected audiences",
                            null));
        };
    }
}
