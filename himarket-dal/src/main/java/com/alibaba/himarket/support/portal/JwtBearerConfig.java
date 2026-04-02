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

package com.alibaba.himarket.support.portal;

import com.alibaba.himarket.support.enums.JwtDirectAcquireMode;
import com.alibaba.himarket.support.enums.JwtDirectIdentitySource;
import com.alibaba.himarket.support.enums.JwtDirectTokenSource;
import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class JwtBearerConfig {

    private static final String DEFAULT_AUTHORIZATION_SERVICE_FIELD = "service";

    private static final String DEFAULT_TICKET_EXCHANGE_METHOD = "POST";

    private static final String DEFAULT_TICKET_EXCHANGE_TICKET_FIELD = "ticket";

    private static final String DEFAULT_TICKET_EXCHANGE_TOKEN_FIELD = "access_token";

    private static final String DEFAULT_TICKET_EXCHANGE_SERVICE_FIELD = "service";

    private static final JwtDirectTokenSource DEFAULT_JWT_SOURCE = JwtDirectTokenSource.QUERY;

    /**
     * Token issuer, used by standard JWT validation mode.
     */
    private String issuer;

    /**
     * JWK set URI used to verify token signature.
     */
    private String jwkSetUri;

    /**
     * Expected audiences, used by standard JWT validation mode.
     */
    private List<String> audiences;

    /**
     * Browser authorization endpoint for JWT direct login.
     */
    private String authorizationEndpoint;

    /**
     * Query parameter name used to pass the callback URL to authorization endpoint.
     */
    private String authorizationServiceField;

    /**
     * JWT acquisition mode after browser authorization.
     */
    private JwtDirectAcquireMode acquireMode;

    /**
     * Ticket exchange endpoint used by ticket-based acquisition mode.
     */
    private String ticketExchangeUrl;

    /**
     * HTTP method for ticket exchange endpoint.
     */
    private String ticketExchangeMethod;

    /**
     * Request field name that carries the incoming ticket.
     */
    private String ticketExchangeTicketField;

    /**
     * Response field name that carries the exchanged JWT token.
     */
    private String ticketExchangeTokenField;

    /**
     * Request field name that carries the callback URL to ticket exchange endpoint.
     */
    private String ticketExchangeServiceField;

    /**
     * UserInfo endpoint used when identity is loaded from remote profile.
     */
    private String userInfoEndpoint;

    /**
     * Identity source for developer provisioning.
     */
    private JwtDirectIdentitySource identitySource;

    /**
     * Browser callback token source.
     */
    private JwtDirectTokenSource tokenSource;

    /**
     * JWT public keys
     */
    private List<PublicKeyConfig> publicKeys;

    public JwtDirectAcquireMode resolveAcquireMode() {
        return Optional.ofNullable(acquireMode).orElse(JwtDirectAcquireMode.DIRECT);
    }

    public JwtDirectIdentitySource resolveIdentitySource() {
        return Optional.ofNullable(identitySource).orElse(JwtDirectIdentitySource.CLAIMS);
    }

    public JwtDirectTokenSource resolveTokenSource() {
        return Optional.ofNullable(tokenSource).orElse(DEFAULT_JWT_SOURCE);
    }

    public String resolveAuthorizationServiceField() {
        return Optional.ofNullable(authorizationServiceField)
                .filter(field -> !field.isBlank())
                .orElse(DEFAULT_AUTHORIZATION_SERVICE_FIELD);
    }

    public String resolveTicketExchangeMethod() {
        return Optional.ofNullable(ticketExchangeMethod)
                .filter(method -> !method.isBlank())
                .orElse(DEFAULT_TICKET_EXCHANGE_METHOD);
    }

    public String resolveTicketExchangeTicketField() {
        return Optional.ofNullable(ticketExchangeTicketField)
                .filter(field -> !field.isBlank())
                .orElse(DEFAULT_TICKET_EXCHANGE_TICKET_FIELD);
    }

    public String resolveTicketExchangeTokenField() {
        return Optional.ofNullable(ticketExchangeTokenField)
                .filter(field -> !field.isBlank())
                .orElse(DEFAULT_TICKET_EXCHANGE_TOKEN_FIELD);
    }

    public String resolveTicketExchangeServiceField() {
        return Optional.ofNullable(ticketExchangeServiceField)
                .filter(field -> !field.isBlank())
                .orElse(DEFAULT_TICKET_EXCHANGE_SERVICE_FIELD);
    }
}
