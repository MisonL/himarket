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

package com.alibaba.himarket.config;

import com.alibaba.himarket.support.portal.CasConfig;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "admin.auth")
public class AdminAuthConfig {

    /**
     * Admin frontend base URL used to build authentication callback URLs.
     */
    private String frontendRedirectUrl;

    /**
     * CAS configurations for admin authentication.
     */
    private List<CasConfig> casConfigs;

    /**
     * LDAP configurations for admin authentication.
     */
    private List<LdapConfig> ldapConfigs;

    /**
     * OAuth2 configurations for admin authentication (JWT bearer).
     */
    private List<OAuth2Config> oauth2Configs;
}
