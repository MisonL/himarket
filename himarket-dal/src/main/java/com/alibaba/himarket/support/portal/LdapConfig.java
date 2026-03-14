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

import com.alibaba.himarket.support.common.Encrypted;
import lombok.Data;

@Data
public class LdapConfig {

    private String provider;

    private String name;

    private boolean enabled = true;

    /**
     * LDAP server URL, e.g. ldap://ldap.example.com:389 or ldaps://ldap.example.com:636
     */
    private String serverUrl;

    /**
     * Base DN for user search.
     */
    private String baseDn;

    /**
     * Bind DN used to search users.
     */
    private String bindDn;

    /**
     * Bind password used to search users.
     */
    @Encrypted private String bindPassword;

    /**
     * LDAP search filter template, e.g. (uid={0}) or (sAMAccountName={0}).
     */
    private String userSearchFilter = "(uid={0})";

    private IdentityMapping identityMapping = new IdentityMapping();
}
