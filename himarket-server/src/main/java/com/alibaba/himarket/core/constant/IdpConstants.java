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

package com.alibaba.himarket.core.constant;

public class IdpConstants {

    /**
     * Grant type
     */
    public static final String GRANT_TYPE = "grant_type";

    /**
     * Authorization code
     */
    public static final String CODE = "code";

    /**
     * Redirect URI
     */
    public static final String REDIRECT_URI = "redirect_uri";

    /**
     * Client ID
     */
    public static final String CLIENT_ID = "client_id";

    /**
     * Client secret
     */
    public static final String CLIENT_SECRET = "client_secret";

    /**
     * Response type
     */
    public static final String RESPONSE_TYPE = "response_type";

    /**
     * Scope
     */
    public static final String SCOPE = "scope";

    /**
     * State
     */
    public static final String STATE = "state";

    public static final String PROVIDER = "provider";

    public static final String LOGOUT_REQUEST = "logoutRequest";

    public static final String GATEWAY = "gateway";

    public static final String RENEW = "renew";

    public static final String WARN = "warn";

    public static final String REMEMBER_ME = "rememberMe";

    public static final String METHOD = "method";

    public static final String FORMAT = "format";

    /**
     * State TTL, also used by OIDC/CAS callback to reject stale requests.
     */
    public static final long IDP_STATE_TTL_MILLIS = 10 * 60 * 1000L;

    public static final int IDP_STATE_COOKIE_MAX_AGE_SECONDS = 10 * 60;

    public static final String IDP_STATE_COOKIE_PATH = "/";

    public static final String IDP_STATE_COOKIE_SAMESITE = "Lax";

    public static final String OIDC_STATE_COOKIE_NAME = "hm_oidc_state";

    public static final String CAS_STATE_COOKIE_NAME = "hm_cas_state";

    public static final String ADMIN_CAS_STATE_COOKIE_NAME = "hm_admin_cas_state";

    /**
     * Nonce
     */
    public static final String NONCE = "nonce";

    /**
     * Ticket
     */
    public static final String TICKET = "ticket";

    public static final String PGT = "pgt";

    public static final String PGT_ID = "pgtId";

    public static final String PGT_IOU = "pgtIou";

    public static final String PGT_URL = "pgtUrl";

    public static final String TARGET_SERVICE = "targetService";

    public static final String PROXY_GRANTING_TICKET = "proxyGrantingTicket";

    public static final String PROXY_TICKET = "proxyTicket";

    /**
     * Service
     */
    public static final String SERVICE = "service";

    /**
     * Subject
     */
    public static final String SUBJECT = "sub";

    /**
     * Name
     */
    public static final String NAME = "name";

    /**
     * Email
     */
    public static final String EMAIL = "email";

    /**
     * Authorization endpoint
     */
    public static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";

    /**
     * Token endpoint
     */
    public static final String TOKEN_ENDPOINT = "token_endpoint";

    /**
     * User info endpoint
     */
    public static final String USERINFO_ENDPOINT = "userinfo_endpoint";

    /**
     * JWK set URI
     */
    public static final String JWKS_URI = "jwks_uri";

    /**
     * Default CAS login path
     */
    public static final String CAS_LOGIN_PATH = "/login";

    /**
     * Default CAS 1.0 validate path
     */
    public static final String CAS1_VALIDATE_PATH = "/validate";

    /**
     * Default CAS 2.0 validate path
     */
    public static final String CAS2_VALIDATE_PATH = "/serviceValidate";

    /**
     * Default CAS validate path
     */
    public static final String CAS_VALIDATE_PATH = "/p3/serviceValidate";

    /**
     * Default CAS SAML validate path
     */
    public static final String CAS_SAML1_VALIDATE_PATH = "/samlValidate";

    /**
     * Default CAS logout path
     */
    public static final String CAS_LOGOUT_PATH = "/logout";

    /**
     * Default CAS proxy path
     */
    public static final String CAS_PROXY_PATH = "/proxy";

    /**
     * Default authentication expiration (24 hours in milliseconds)
     */
    public static final long DEFAULT_EXPIRATION_MILLIS = 24 * 60 * 60 * 1000L;

    /**
     * Seconds in a full day (24 hours)
     */
    public static final long SECONDS_PER_DAY = 24 * 60 * 60L;
}
