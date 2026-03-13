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
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.portal.AuthCodeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class OidcIdTokenVerifier {

    public Map<String, Object> verifyAndExtractClaims(
            String idToken, AuthCodeConfig authCodeConfig, String expectedNonce) {
        if (StrUtil.isBlank(authCodeConfig.getJwkSetUri())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "OIDC config missing JWK set URI");
        }

        try {
            Jwt jwt = createDecoder(authCodeConfig).decode(idToken);
            validateNonce(jwt, expectedNonce);
            return new HashMap<>(jwt.getClaims());
        } catch (JwtException e) {
            log.error("Failed to verify OIDC ID token", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid OIDC ID Token");
        }
    }

    private JwtDecoder createDecoder(AuthCodeConfig authCodeConfig) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(authCodeConfig.getJwkSetUri()).build();
        decoder.setJwtValidator(buildValidator(authCodeConfig));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> buildValidator(AuthCodeConfig authCodeConfig) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (StrUtil.isNotBlank(authCodeConfig.getIssuer())) {
            validators.add(JwtValidators.createDefaultWithIssuer(authCodeConfig.getIssuer()));
        } else {
            validators.add(JwtValidators.createDefault());
        }
        validators.add(validateAudience(authCodeConfig.getClientId()));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private OAuth2TokenValidator<Jwt> validateAudience(String expectedAudience) {
        return token -> {
            if (StrUtil.isBlank(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }

            List<String> audiences = token.getAudience();
            if (audiences != null && audiences.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            ErrorCode.INVALID_REQUEST.name(),
                            "OIDC ID Token audience does not match client ID",
                            null));
        };
    }

    private void validateNonce(Jwt jwt, String expectedNonce) {
        if (StrUtil.isBlank(expectedNonce)) {
            return;
        }

        String nonce = jwt.getClaimAsString("nonce");
        if (StrUtil.isBlank(nonce) || !expectedNonce.equals(nonce)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "OIDC ID Token nonce mismatch");
        }
    }
}
