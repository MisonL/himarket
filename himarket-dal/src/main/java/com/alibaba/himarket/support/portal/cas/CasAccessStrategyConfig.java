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

package com.alibaba.himarket.support.portal.cas;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CasAccessStrategyConfig {

    private Boolean enabled = true;

    private Boolean ssoEnabled = true;

    private String unauthorizedRedirectUrl;

    private Boolean requireAllAttributes = false;

    private Boolean caseInsensitive = false;

    private Map<String, List<String>> requiredAttributes;

    private Map<String, List<String>> rejectedAttributes;

    private String startingDateTime;

    private String endingDateTime;

    private String zoneId;

    private CasDelegatedAuthenticationPolicyConfig delegatedAuthenticationPolicy;

    private CasHttpRequestAccessStrategyConfig httpRequest;
}
