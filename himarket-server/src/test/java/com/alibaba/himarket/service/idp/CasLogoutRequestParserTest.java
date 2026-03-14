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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.himarket.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CasLogoutRequestParserTest {

    private final CasLogoutRequestParser parser = new CasLogoutRequestParser();

    @Test
    void parseSessionIndexShouldExtractValue() {
        String xml =
                "<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                        + "<samlp:SessionIndex>ST-1</samlp:SessionIndex>"
                        + "</samlp:LogoutRequest>";

        assertEquals("ST-1", parser.parseSessionIndex(xml));
    }

    @Test
    void parseSessionIndexShouldRejectInvalidPayload() {
        assertThrows(BusinessException.class, () -> parser.parseSessionIndex("<bad>"));
    }

    @Test
    void parseSessionIndexShouldRejectDoctypePayload() {
        String xml =
                "<!DOCTYPE samlp:LogoutRequest [<!ENTITY xxe SYSTEM"
                        + " \"file:///etc/passwd\">]><samlp:LogoutRequest"
                        + " xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
                        + "<samlp:SessionIndex>&xxe;</samlp:SessionIndex></samlp:LogoutRequest>";

        assertThrows(BusinessException.class, () -> parser.parseSessionIndex(xml));
    }
}
