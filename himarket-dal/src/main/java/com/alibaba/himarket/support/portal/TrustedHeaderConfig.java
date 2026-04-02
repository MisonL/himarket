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

import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class TrustedHeaderConfig {

    private static final String DEFAULT_USER_ID_HEADER = "X-Forwarded-User";

    private static final String DEFAULT_USER_NAME_HEADER = "X-Forwarded-Name";

    private static final String DEFAULT_EMAIL_HEADER = "X-Forwarded-Email";

    private static final String DEFAULT_GROUPS_HEADER = "X-Forwarded-Groups";

    private static final String DEFAULT_ROLES_HEADER = "X-Forwarded-Roles";

    private static final String DEFAULT_VALUE_SEPARATOR = ",";

    private Boolean enabled;

    private List<String> trustedProxyCidrs;

    private List<String> trustedProxyHosts;

    private String userIdHeader;

    private String userNameHeader;

    private String emailHeader;

    private String groupsHeader;

    private String rolesHeader;

    private String valueSeparator;

    public boolean resolveEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String resolveUserIdHeader() {
        return Optional.ofNullable(userIdHeader)
                .filter(header -> !header.isBlank())
                .orElse(DEFAULT_USER_ID_HEADER);
    }

    public String resolveUserNameHeader() {
        return Optional.ofNullable(userNameHeader)
                .filter(header -> !header.isBlank())
                .orElse(DEFAULT_USER_NAME_HEADER);
    }

    public String resolveEmailHeader() {
        return Optional.ofNullable(emailHeader)
                .filter(header -> !header.isBlank())
                .orElse(DEFAULT_EMAIL_HEADER);
    }

    public String resolveGroupsHeader() {
        return Optional.ofNullable(groupsHeader)
                .filter(header -> !header.isBlank())
                .orElse(DEFAULT_GROUPS_HEADER);
    }

    public String resolveRolesHeader() {
        return Optional.ofNullable(rolesHeader)
                .filter(header -> !header.isBlank())
                .orElse(DEFAULT_ROLES_HEADER);
    }

    public String resolveValueSeparator() {
        return Optional.ofNullable(valueSeparator)
                .filter(separator -> !separator.isBlank())
                .orElse(DEFAULT_VALUE_SEPARATOR);
    }
}
