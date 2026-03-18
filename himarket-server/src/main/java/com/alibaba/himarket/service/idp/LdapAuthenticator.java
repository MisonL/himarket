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

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.portal.LdapConfig;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.NamingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LDAP Authenticator for handling user authentication against LDAP/AD servers.
 * Complies with Alibaba Java Coding Guidelines (P3C).
 */
@Component
@Slf4j
public class LdapAuthenticator {

    private static final String CONNECT_TIMEOUT_MS = "5000";

    private static final String READ_TIMEOUT_MS = "5000";

    /**
     * Authenticates a user against the LDAP server.
     *
     * @param config   LDAP configuration
     * @param username User name (e.g., uid)
     * @param password User password
     * @return Map of LDAP attributes for the authenticated user
     */
    public Map<String, Object> authenticate(LdapConfig config, String username, String password) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Username or password cannot be blank");
        }

        SearchResult user = findUniqueUser(config, username);
        String userDn = resolveUserDn(user);

        verifyBind(config, userDn, password);

        return extractAttributes(user);
    }

    private SearchResult findUniqueUser(LdapConfig config, String username) {
        Hashtable<String, Object> env = createBaseEnv(config.getServerUrl());
        if (StrUtil.isNotBlank(config.getBindDn())) {
            env.put(Context.SECURITY_PRINCIPAL, config.getBindDn());
            env.put(Context.SECURITY_CREDENTIALS, config.getBindPassword());
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
        }

        String filter =
                StrUtil.replace(config.getUserSearchFilter(), "{0}", escapeFilterValue(username));
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setCountLimit(2);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            NamingEnumeration<SearchResult> results =
                    ctx.search(config.getBaseDn(), filter, controls);
            if (results == null || !results.hasMore()) {
                throw invalidCredentials();
            }
            SearchResult first = results.next();
            if (results.hasMore()) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "LDAP search returned multiple users");
            }
            return first;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LDAP search failed, provider={}", config.getProvider(), e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "LDAP authentication failed");
        } finally {
            closeQuietly(ctx);
        }
    }

    private void verifyBind(LdapConfig config, String userDn, String password) {
        Hashtable<String, Object> env = createBaseEnv(config.getServerUrl());
        env.put(Context.SECURITY_PRINCIPAL, userDn);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            // Bind success means password is correct.
        } catch (NamingException e) {
            throw invalidCredentials();
        } finally {
            closeQuietly(ctx);
        }
    }

    private Hashtable<String, Object> createBaseEnv(String serverUrl) {
        // Note: Using Hashtable is mandated by JNDI InitialDirContext constructor.
        // We use specifically typed Hashtable to comply with JNDI requirements while reducing generic warnings.
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, serverUrl);
        env.put("com.sun.jndi.ldap.connect.timeout", CONNECT_TIMEOUT_MS);
        env.put("com.sun.jndi.ldap.read.timeout", READ_TIMEOUT_MS);
        return env;
    }

    private void closeQuietly(DirContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.close();
        } catch (Exception e) {
            log.debug("Failed to close LDAP context", e);
        }
    }

    private String resolveUserDn(SearchResult user) {
        try {
            String dn = user.getNameInNamespace();
            if (StrUtil.isBlank(dn)) {
                throw new IllegalArgumentException("Empty DN");
            }
            return dn;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "LDAP user DN is missing");
        }
    }

    private Map<String, Object> extractAttributes(SearchResult user) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Attributes attrs = user.getAttributes();
        if (attrs == null) {
            return attributes;
        }

        try {
            NamingEnumeration<? extends Attribute> all = attrs.getAll();
            while (all.hasMore()) {
                Attribute attr = all.next();
                String id = attr.getID();
                Object raw = attr.get();
                String value = Convert.toStr(raw, null);
                if (StrUtil.isNotBlank(id) && StrUtil.isNotBlank(value)) {
                    attributes.put(id, value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract LDAP attributes", e);
        }
        return attributes;
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                ErrorCode.UNAUTHORIZED, "The username or password you entered is incorrect");
    }

    static String escapeFilterValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\0':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }
}
