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
import java.util.Map;
import org.junit.jupiter.api.Test;

class CasTicketValidationParserTest {

    private final CasTicketValidationParser parser = new CasTicketValidationParser();

    @Test
    void parseShouldExtractUserAndAttributes() {
        String xml =
                "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                        + "<cas:authenticationSuccess>"
                        + "<cas:user>alice</cas:user>"
                        + "<cas:attributes>"
                        + "<cas:mail>alice@example.com</cas:mail>"
                        + "</cas:attributes>"
                        + "</cas:authenticationSuccess>"
                        + "</cas:serviceResponse>";

        Map<String, Object> attributes = parser.parse(xml);

        assertEquals("alice", attributes.get("user"));
        assertEquals("alice@example.com", attributes.get("mail"));
    }

    @Test
    void parseShouldRejectDoctypePayload() {
        String xml =
                "<!DOCTYPE cas:serviceResponse [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">"
                        + "<cas:authenticationSuccess>"
                        + "<cas:user>&xxe;</cas:user>"
                        + "</cas:authenticationSuccess>"
                        + "</cas:serviceResponse>";

        assertThrows(BusinessException.class, () -> parser.parse(xml));
    }
}
