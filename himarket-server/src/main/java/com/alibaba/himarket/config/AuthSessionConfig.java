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

import com.alibaba.himarket.support.enums.AuthSessionStoreType;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthSessionConfig {

    private SessionStore sessionStore = new SessionStore();

    private Cas cas = new Cas();

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public void setSessionStore(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public Cas getCas() {
        return cas;
    }

    public void setCas(Cas cas) {
        this.cas = cas;
    }

    public static class SessionStore {

        private AuthSessionStoreType type = AuthSessionStoreType.MEMORY;

        public AuthSessionStoreType getType() {
            return type;
        }

        public void setType(AuthSessionStoreType type) {
            this.type = type;
        }
    }

    public static class Cas {

        private Duration loginCodeTtl = Duration.ofSeconds(60);

        private int maxSessionsPerUser = 10;

        private Duration rememberMeTokenTtl = Duration.ofDays(14);

        private Duration sessionLeaseBuffer = Duration.ofMinutes(5);

        public Duration getLoginCodeTtl() {
            return loginCodeTtl;
        }

        public void setLoginCodeTtl(Duration loginCodeTtl) {
            this.loginCodeTtl = loginCodeTtl;
        }

        public int getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }

        public void setMaxSessionsPerUser(int maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }

        public Duration getRememberMeTokenTtl() {
            return rememberMeTokenTtl;
        }

        public void setRememberMeTokenTtl(Duration rememberMeTokenTtl) {
            this.rememberMeTokenTtl = rememberMeTokenTtl;
        }

        public Duration getSessionLeaseBuffer() {
            return sessionLeaseBuffer;
        }

        public void setSessionLeaseBuffer(Duration sessionLeaseBuffer) {
            this.sessionLeaseBuffer = sessionLeaseBuffer;
        }
    }
}
